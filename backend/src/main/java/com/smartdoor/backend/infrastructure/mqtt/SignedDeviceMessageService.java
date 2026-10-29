package com.smartdoor.backend.infrastructure.mqtt;

import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.service.HardwareAlertEvent;
import com.smartdoor.backend.alert.service.HardwareAlertService;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.mqtt.SyncAckMessageHandler;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Authenticates topic + envelope before creating the internal trusted identity. */
@Service
public class SignedDeviceMessageService {
    private static final long MAX_QUEUED_EVENT_AGE_SECONDS=86400;
    private static final Pattern TOPIC=Pattern.compile("smart-door/v1/doors/([1-9][0-9]*)/devices/([A-Za-z0-9][A-Za-z0-9._-]{0,99})/events/(sync-ack|alert|heartbeat)");
    private final DeviceRepository devices;
    private final DoorRepository doors;
    private final DeviceTransportCredentialRepository keys;
    private final DeviceTransportCredentialService secrets;
    private final ProcessedDeviceMessageRepository processed;
    private final SyncAckMessageHandler acknowledgements;
    private final HardwareAlertService alerts;
    private final DeviceHeartbeatService heartbeats;
    private final SettingsMqttProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SignedDeviceMessageService(DeviceRepository devices,DoorRepository doors,DeviceTransportCredentialRepository keys,
            DeviceTransportCredentialService secrets,ProcessedDeviceMessageRepository processed,
            SyncAckMessageHandler acknowledgements,HardwareAlertService alerts,DeviceHeartbeatService heartbeats,
            SettingsMqttProperties properties,ObjectMapper mapper,Clock clock) {
        this.devices=devices; this.doors=doors; this.keys=keys; this.secrets=secrets; this.processed=processed;
        this.acknowledgements=acknowledgements; this.alerts=alerts; this.heartbeats=heartbeats;
        this.properties=properties; this.mapper=mapper; this.clock=clock;
    }

    @Transactional
    public void receive(String topic,byte[] bytes) {
        if (bytes==null || bytes.length>properties.maxPayloadBytes()) throw invalid();
        if (topic==null) throw invalid();
        var match=TOPIC.matcher(topic);
        if (!match.matches()) throw invalid();
        long doorId;
        try { doorId=Long.parseLong(match.group(1)); } catch (NumberFormatException e) { throw invalid(); }
        long maximumAge="heartbeat".equals(match.group(3)) ? properties.maxClockSkewSeconds() : MAX_QUEUED_EVENT_AGE_SECONDS;
        SignedEnvelope envelope;
        try { envelope=mapper.readValue(bytes,SignedEnvelope.class); } catch (RuntimeException e) { throw invalid(); }
        if (envelope==null || envelope.credentialId()==null || envelope.nonce()==null || !envelope.nonce().matches("[A-Za-z0-9_-]{8,100}")
                || envelope.payload()==null || envelope.signature()==null
                || !envelope.signature().matches("[0-9a-fA-F]{64}")
                || envelope.sentAt()<clock.instant().getEpochSecond()-maximumAge
                || envelope.sentAt()>clock.instant().getEpochSecond()+properties.maxClockSkewSeconds()) throw invalid();
        doors.findByIdForUpdate(doorId).orElseThrow(SignedDeviceMessageService::invalid);
        Device device=devices.findByDeviceCode(match.group(2)).orElseThrow(SignedDeviceMessageService::invalid);
        if (!device.getDoorId().equals(doorId) || !device.getDeviceCode().equals(match.group(2))
                || device.getLifecycleStatus()==Device.LifecycleStatus.RETIRED) throw invalid();
        DeviceTransportCredential key=keys.findById(device.getId()).orElseThrow(SignedDeviceMessageService::invalid);
        if (key.getRevokedAt()!=null || !key.getCredentialId().equals(envelope.credentialId())) throw invalid();
        byte[] expected=DeviceTransportCredentialService.hmac(secrets.signingKey(key),canonical(topic,envelope));
        if (!MessageDigest.isEqual(expected,HexFormat.of().parseHex(envelope.signature()))) throw invalid();
        String messageId=DeviceTransportCredentialService.digest(key.getCredentialId()+":"+envelope.nonce());
        if (processed.existsById(messageId)) return;
        byte[] payload;
        try { payload=Base64.getDecoder().decode(envelope.payload()); } catch (IllegalArgumentException e) { throw invalid(); }
        if (payload.length>properties.maxPayloadBytes()) throw invalid();
        AuthenticatedDeviceIdentity identity=new AuthenticatedDeviceIdentity(device.getDeviceCode(),doorId,device.getNodeType());
        switch (match.group(3)) {
            case "sync-ack" -> acknowledgements.handle(identity,parse(payload,SyncAckMessageHandler.SyncAckMessage.class));
            case "alert" -> {
                AlertPayload alert=parse(payload,AlertPayload.class);
                LocalDateTime occurred=null;
                if (alert.occurredAt()!=null) {
                    try { occurred=LocalDateTime.ofInstant(Instant.parse(alert.occurredAt()),ZoneOffset.UTC); }
                    catch (RuntimeException e) { throw invalid(); }
                    if (occurred.isAfter(LocalDateTime.ofInstant(clock.instant().plusSeconds(60),ZoneOffset.UTC))) throw invalid();
                }
                alerts.receive(identity,new HardwareAlertEvent(alert.eventId(),alert.type(),alert.observedTemperature(),occurred));
            }
            case "heartbeat" -> heartbeats.recordHeartbeat(identity,parse(payload,HeartbeatPayload.class).firmwareVersion());
            default -> throw invalid();
        }
        key.setVerifiedAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        ProcessedDeviceMessage receipt=new ProcessedDeviceMessage();
        receipt.setId(messageId); receipt.setDeviceId(device.getId()); receipt.setReceivedAt(key.getVerifiedAt());
        processed.save(receipt);
    }

    private <T> T parse(byte[] value,Class<T> type) {
        try {
            T result=mapper.readValue(value,type);
            if (result==null) throw invalid();
            return result;
        } catch (RuntimeException e) { throw invalid(); }
    }

    public static String canonical(String topic,SignedEnvelope e) {
        return topic+"\n"+e.credentialId()+"\n"+e.sentAt()+"\n"+e.nonce()+"\n"+e.payload();
    }

    @Scheduled(initialDelay=60000,fixedDelay=3600000)
    public void cleanReceipts() { processed.deleteByReceivedAtBefore(LocalDateTime.ofInstant(clock.instant().minusSeconds(172800),ZoneOffset.UTC)); }

    private static BadRequestException invalid() { return new BadRequestException("Thông điệp thiết bị không hợp lệ hoặc không xác thực được."); }
    public record SignedEnvelope(String credentialId,long sentAt,String nonce,String payload,String signature) { }
    private record AlertPayload(String eventId,Alert.Type type,BigDecimal observedTemperature,String occurredAt) { }
    private record HeartbeatPayload(String firmwareVersion) { }
}
