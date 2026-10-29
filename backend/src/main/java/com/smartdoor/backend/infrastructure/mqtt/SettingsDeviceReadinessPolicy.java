package com.smartdoor.backend.infrastructure.mqtt;

import com.smartdoor.backend.credentials.entity.SystemPin;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import org.springframework.stereotype.Component;

/** Readiness is proven by signed setup ACKs, never by a caller-supplied ready flag. */
@Component
public class SettingsDeviceReadinessPolicy implements DeviceReadinessPolicy {
    private final DeviceTransportCredentialRepository keys;
    private final DoorConfigRepository configs;
    private final SystemPinRepository pins;
    public SettingsDeviceReadinessPolicy(DeviceTransportCredentialRepository keys,DoorConfigRepository configs,SystemPinRepository pins) {
        this.keys=keys; this.configs=configs; this.pins=pins;
    }
    @Override public boolean canActivate(Device device) {
        var key=keys.findById(device.getId()).orElse(null);
        var config=configs.findById(device.getDoorId()).orElse(null);
        if (key==null || key.getRevokedAt()!=null || key.getVerifiedAt()==null || config==null
                || !config.getVersion().equals(device.getAppliedConfigVersion())) return false;
        if (device.getNodeType()==Device.NodeType.NODE_2) return true;
        var pin=pins.findById(device.getDoorId()).orElse(null);
        return pin!=null && pin.getStatus()==SystemPin.Status.ACTIVE
                && config.getCredentialsVersion().equals(device.getAppliedPinVersion());
    }
    // Replacement still needs the technical Broker-revocation workflow owned by module Device.
    // Database key revocation alone cannot certify that an old Broker session was removed.
}
