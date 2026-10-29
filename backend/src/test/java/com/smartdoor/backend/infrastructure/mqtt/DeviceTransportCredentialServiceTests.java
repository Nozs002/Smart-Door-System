package com.smartdoor.backend.infrastructure.mqtt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceTransportCredentialServiceTests {
    private final DeviceTransportCredentialRepository keys=mock(DeviceTransportCredentialRepository.class);
    private final DeviceRepository devices=mock(DeviceRepository.class);
    private final DoorRepository doors=mock(DoorRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),ZoneOffset.UTC);
    private final Device device=new Device();
    private final DeviceTransportCredential previous=new DeviceTransportCredential();
    @BeforeEach void setup() {
        device.setId(1L); device.setDoorId(5L); device.setDeviceCode("ISSUE-NODE-1"); device.setNodeType(Device.NodeType.NODE_1);
        device.setAppliedConfigVersion(9L); device.setAppliedPinVersion(7L);
        previous.setDeviceId(1L); previous.setCredentialId(UUID.randomUUID().toString());
        previous.setVerifiedAt(LocalDateTime.now(clock));
        when(devices.findById(1L)).thenReturn(Optional.of(device));
        when(doors.findByIdForUpdate(5L)).thenReturn(Optional.of(new Door()));
        when(devices.findByIdAndDoorIdForUpdate(1L,5L)).thenReturn(Optional.of(device));
        when(keys.findById(1L)).thenReturn(Optional.of(previous));
    }
    @Test void rotatingIdentityInvalidatesEveryOldAcknowledgementAndSecret() {
        String oldId=previous.getCredentialId();
        var result=service(Base64.getEncoder().encodeToString(new byte[32])).issue(1L);
        assertThat(result.credentialId()).isNotEqualTo(oldId);
        assertThat(previous.getVerifiedAt()).isNull();
        assertThat(device.getAppliedConfigVersion()).isNull();
        assertThat(device.getAppliedPinVersion()).isNull();
        assertThat(Base64.getDecoder().decode(result.signingKey())).hasSize(32);
        assertThat(result.password()).isNotEqualTo(result.signingKey());
        assertThat(result.toString()).doesNotContain(result.password(),result.signingKey());
        verify(keys).saveAndFlush(previous);
    }
    @Test void missingMasterKeyDoesNotPersistOrClearAcknowledgements() {
        assertThatThrownBy(() -> service("").issue(1L)).isInstanceOf(BadRequestException.class);
        verify(keys,never()).saveAndFlush(any());
        assertThat(device.getAppliedConfigVersion()).isEqualTo(9L);
    }
    @Test void retiredNodeCannotObtainANewBrokerIdentity() {
        device.setLifecycleStatus(Device.LifecycleStatus.RETIRED);
        assertThatThrownBy(() -> service(Base64.getEncoder().encodeToString(new byte[32])).issue(1L)).isInstanceOf(ConflictException.class);
        verify(keys,never()).saveAndFlush(any());
    }
    private DeviceTransportCredentialService service(String master) {
        return new DeviceTransportCredentialService(keys,devices,doors,new SettingsMqttProperties(false,false,master,60,4096),clock);
    }
}
