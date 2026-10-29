package com.smartdoor.backend.infrastructure.mqtt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.alert.service.HardwareAlertService;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.infrastructure.mqtt.SignedDeviceMessageService.SignedEnvelope;
import com.smartdoor.backend.settings.mqtt.SyncAckMessageHandler;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class SignedDeviceMessageServiceTests {
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DoorRepository doors = mock(DoorRepository.class);
    private final DeviceTransportCredentialRepository keys = mock(DeviceTransportCredentialRepository.class);
    private final ProcessedDeviceMessageRepository processed = mock(ProcessedDeviceMessageRepository.class);
    private final SyncAckMessageHandler acks = mock(SyncAckMessageHandler.class);
    private final HardwareAlertService alerts = mock(HardwareAlertService.class);
    private final DeviceHeartbeatService heartbeats = mock(DeviceHeartbeatService.class);
    private final ObjectMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC);
    private final SettingsMqttProperties properties = new SettingsMqttProperties(false, false,
            Base64.getEncoder().encodeToString("test-only-master-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8)), 60, 4096);
    private final DeviceTransportCredentialService secrets = new DeviceTransportCredentialService(keys, devices, doors, properties, clock);
    private final SignedDeviceMessageService service = new SignedDeviceMessageService(devices, doors, keys, secrets,
            processed, acks, alerts, heartbeats, properties, json, clock);
    private final Device device = new Device();
    private final DeviceTransportCredential key = new DeviceTransportCredential();
    private final String topic = "smart-door/v1/doors/5/devices/NODE-TEST-1/events/heartbeat";

    @BeforeEach
    void setup() {
        device.setId(11L); device.setDoorId(5L); device.setDeviceCode("NODE-TEST-1"); device.setNodeType(Device.NodeType.NODE_1);
        key.setDeviceId(11L); key.setCredentialId("909bcf8f-16f8-4b24-a091-956e333784e9");
        when(doors.findByIdForUpdate(anyLong())).thenReturn(Optional.of(new Door()));
        when(devices.findByDeviceCode("NODE-TEST-1")).thenReturn(Optional.of(device));
        when(keys.findById(11L)).thenReturn(Optional.of(key));
    }

    @Test
    void authenticatedTopicAndCurrentCredentialDetermineTheTrustedIdentity() {
        service.receive(topic, signed(topic, "{\"firmwareVersion\":\"test-v1\",\"doorId\":99,\"deviceCode\":\"FAKE\"}", "nonce_0001"));
        var identity = ArgumentCaptor.forClass(AuthenticatedDeviceIdentity.class);
        verify(heartbeats).recordHeartbeat(identity.capture(), eq("test-v1"));
        assertThat(identity.getValue()).isEqualTo(new AuthenticatedDeviceIdentity("NODE-TEST-1", 5L, Device.NodeType.NODE_1));
        assertThat(key.getVerifiedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        var receipt = ArgumentCaptor.forClass(ProcessedDeviceMessage.class);
        verify(processed).save(receipt.capture());
        assertThat(receipt.getValue().getDeviceId()).isEqualTo(11L);
        assertThat(receipt.getValue().getId()).isEqualTo(DeviceTransportCredentialService.digest(key.getCredentialId()+":nonce_0001"));
        verifyNoInteractions(acks, alerts);
    }

    @Test
    void alteredPayloadOrWrongSignatureCannotCallAnyDomainHandler() {
        SignedEnvelope valid = envelope(topic, "{\"firmwareVersion\":\"test-v1\"}", "nonce_0002", clock.instant().getEpochSecond());
        SignedEnvelope tampered = new SignedEnvelope(valid.credentialId(), valid.sentAt(), valid.nonce(),
                Base64.getEncoder().encodeToString("{\"firmwareVersion\":\"altered\"}".getBytes(StandardCharsets.UTF_8)), valid.signature());
        assertInvalid(topic, json.writeValueAsBytes(tampered));
        SignedEnvelope wrong = new SignedEnvelope(valid.credentialId(), valid.sentAt(), valid.nonce(), valid.payload(), "0".repeat(64));
        assertInvalid(topic, json.writeValueAsBytes(wrong));
        noSideEffects();
    }

    @Test
    void signedMessageForAnotherDoorOrUnknownNodeIsRejected() {
        String anotherDoor = topic.replace("/doors/5/", "/doors/6/");
        assertInvalid(anotherDoor, signed(anotherDoor, "{}", "nonce_0003"));
        String anotherNode = topic.replace("NODE-TEST-1", "UNKNOWN-NODE");
        assertInvalid(anotherNode, signed(anotherNode, "{}", "nonce_0004"));
        noSideEffects();
    }

    @Test
    void retiredRevokedAndRotatedCredentialsCannotSendEvents() {
        byte[] message = signed(topic, "{}", "nonce_0005");
        device.setLifecycleStatus(Device.LifecycleStatus.RETIRED);
        assertInvalid(topic, message);
        device.setLifecycleStatus(Device.LifecycleStatus.PROVISIONED);
        key.setRevokedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertInvalid(topic, message);
        key.setRevokedAt(null); key.setCredentialId("rotated-current-credential-id");
        assertInvalid(topic, message);
        noSideEffects();
    }

    @Test
    void replayIsIdempotentAndDoesNotRefreshVerifiedTimeOrCallHandler() {
        when(processed.existsById(DeviceTransportCredentialService.digest(key.getCredentialId()+":nonce_0006"))).thenReturn(true);
        service.receive(topic, signed(topic, "{}", "nonce_0006"));
        noSideEffects();
    }

    @Test
    void rejectsStaleMalformedOversizedAndNullMessagesWithoutSideEffects() {
        assertInvalid(topic, json.writeValueAsBytes(envelope(topic, "{}", "nonce_0007", clock.instant().getEpochSecond()-61)));
        assertInvalid(topic, json.writeValueAsBytes(envelope(topic, "{}", "nonce_0008", clock.instant().getEpochSecond()+61)));
        assertInvalid(topic, "null".getBytes(StandardCharsets.UTF_8));
        assertInvalid(null, "{}".getBytes(StandardCharsets.UTF_8));
        assertInvalid(topic, "{".getBytes(StandardCharsets.UTF_8));
        assertInvalid(topic, new byte[4097]);
        assertInvalid(topic, signed(topic, "null", "nonce_0009"));
        noSideEffects();
    }

    @Test
    void acknowledgementIsForwardedOnlyAfterAuthentication() {
        String ackTopic = topic.replace("/heartbeat", "/sync-ack");
        service.receive(ackTopic, signed(ackTopic, "{\"eventId\":\"ack-1\",\"kind\":\"CREDENTIALS\",\"version\":7}", "nonce_0010"));
        verify(acks).handle(new AuthenticatedDeviceIdentity("NODE-TEST-1", 5L, Device.NodeType.NODE_1),
                new SyncAckMessageHandler.SyncAckMessage("ack-1", SyncAckMessageHandler.Kind.CREDENTIALS, 7));
        verifyNoInteractions(alerts, heartbeats);
        verify(processed).save(any());
    }

    @Test
    void domainRejectionDoesNotCreateReceiptOrVerifyTheKey() {
        doThrow(new BadRequestException("Rejected heartbeat")).when(heartbeats).recordHeartbeat(any(), any());
        assertInvalid(topic, signed(topic, "{\"firmwareVersion\":\"test-v1\"}", "nonce_0011"));
        assertThat(key.getVerifiedAt()).isNull();
        verify(processed, never()).save(any());
    }

    @Test
    void brokerQueuedAcknowledgementsSurviveAnHourOutageButExpireAfterADay() {
        String ackTopic=topic.replace("/heartbeat","/sync-ack");
        String payload="{\"eventId\":\"queued-ack\",\"kind\":\"CONFIGURATION\",\"version\":7}";
        service.receive(ackTopic,json.writeValueAsBytes(envelope(ackTopic,payload,"queued_0001",clock.instant().getEpochSecond()-3600)));
        verify(acks).handle(any(),eq(new SyncAckMessageHandler.SyncAckMessage("queued-ack",SyncAckMessageHandler.Kind.CONFIGURATION,7)));
        assertInvalid(ackTopic,json.writeValueAsBytes(envelope(ackTopic,payload,"queued_0002",clock.instant().getEpochSecond()-86401)));
    }

    private byte[] signed(String target, String payload, String nonce) {
        return json.writeValueAsBytes(envelope(target, payload, nonce, clock.instant().getEpochSecond()));
    }
    private SignedEnvelope envelope(String target, String payload, String nonce, long sentAt) {
        SignedEnvelope unsigned = new SignedEnvelope(key.getCredentialId(), sentAt, nonce,
                Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)), "");
        String signature = HexFormat.of().formatHex(DeviceTransportCredentialService.hmac(secrets.signingKey(key),
                SignedDeviceMessageService.canonical(target, unsigned)));
        return new SignedEnvelope(unsigned.credentialId(), unsigned.sentAt(), unsigned.nonce(), unsigned.payload(), signature);
    }
    private void assertInvalid(String target, byte[] message) {
        assertThatThrownBy(() -> service.receive(target, message)).isInstanceOf(BadRequestException.class);
    }
    private void noSideEffects() {
        verifyNoInteractions(acks, alerts, heartbeats);
        verify(processed, never()).save(any());
        assertThat(key.getVerifiedAt()).isNull();
    }
}
