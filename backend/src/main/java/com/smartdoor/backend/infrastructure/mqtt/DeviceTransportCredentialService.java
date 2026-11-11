package com.smartdoor.backend.infrastructure.mqtt;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Technical credential issuance. No public HTTP route can mint a device identity. */
@Service
public class DeviceTransportCredentialService {
    private final DeviceTransportCredentialRepository keys;
    private final DeviceRepository devices;
    private final DoorRepository doors;
    private final SettingsMqttProperties properties;
    private final Clock clock;

    public DeviceTransportCredentialService(DeviceTransportCredentialRepository keys, DeviceRepository devices,
            DoorRepository doors, SettingsMqttProperties properties, Clock clock) {
        this.keys=keys; this.devices=devices; this.doors=doors; this.properties=properties; this.clock=clock;
    }

    @Transactional
    public DeviceCredentialExport issue(Long deviceId) {
        Device candidate=devices.findById(deviceId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
        doors.findByIdForUpdate(candidate.getDoorId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
        Device device=devices.findByIdAndDoorIdForUpdate(deviceId,candidate.getDoorId()).orElseThrow();
        if (device.getLifecycleStatus()==Device.LifecycleStatus.RETIRED) throw new ConflictException("Không cấp khóa cho Node đã ngừng sử dụng.");
        DeviceTransportCredential key=keys.findById(deviceId).orElseGet(DeviceTransportCredential::new);
        key.setDeviceId(deviceId); key.setCredentialId(UUID.randomUUID().toString());
        key.setCreatedAt(now()); key.setVerifiedAt(null); key.setRevokedAt(null);
        // Fail before saving if the server master key is not configured.
        DeviceCredentialExport result=export(device,key);
        // A new identity must prove that it applied the snapshots itself.
        device.setAppliedConfigVersion(null); device.setAppliedPinVersion(null);
        keys.saveAndFlush(key);
        return result;
    }

    @Transactional(readOnly=true)
    public List<DeviceCredentialExport> exportBrokerUsers() {
        return keys.findAllByRevokedAtIsNull().stream().map(key -> {
            Device d=devices.findById(key.getDeviceId()).orElseThrow();
            return d.getLifecycleStatus()==Device.LifecycleStatus.RETIRED ? null : export(d,key);
        }).filter(java.util.Objects::nonNull).toList();
    }

    public byte[] signingKey(DeviceTransportCredential key) {
        return derive("message-signing",key.getCredentialId());
    }

    private DeviceCredentialExport export(Device d,DeviceTransportCredential key) {
        return new DeviceCredentialExport(d.getId(),d.getDoorId(),d.getDeviceCode(),d.getNodeType().name(),
                key.getCredentialId(),"device-"+d.getId()+"-"+key.getCredentialId(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(derive("broker-password",key.getCredentialId())),
                Base64.getEncoder().encodeToString(signingKey(key)));
    }

    private byte[] derive(String purpose,String id) {
        byte[] master;
        try { master=Base64.getDecoder().decode(properties.deviceMasterKey()==null ? "" : properties.deviceMasterKey()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("DEVICE_MASTER_KEY phải là Base64 của ít nhất 32 byte ngẫu nhiên."); }
        if (master.length<32) throw new BadRequestException("Thiếu DEVICE_MASTER_KEY; tạo khóa bằng scripts/setup-mqtt.py.");
        return hmac(master,purpose+":"+id);
    }

    static byte[] hmac(byte[] secret,String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("HMAC unavailable",e); }
    }

    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }

    public record DeviceCredentialExport(Long deviceId,Long doorId,String deviceCode,String nodeType,
            String credentialId,String username,String password,String signingKey) {
        @Override public String toString() { return "DeviceCredentialExport[deviceId="+deviceId+", secrets=REDACTED]"; }
    }
}
