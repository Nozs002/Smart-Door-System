package com.smartdoor.backend.infrastructure.mqtt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.smartdoor.backend.credentials.repository.SystemPinRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsDeviceReadinessPolicyTests {
    @Test void replacementNeedsBrokerConfirmationInAdditionToDatabaseRevocation() {
        var keys=mock(DeviceTransportCredentialRepository.class);
        var policy=new SettingsDeviceReadinessPolicy(keys,mock(DoorConfigRepository.class),mock(SystemPinRepository.class));
        var old=new Device(); old.setId(1L);
        var replacement=new Device(); replacement.setId(2L);
        var key=new DeviceTransportCredential();
        when(keys.findById(1L)).thenReturn(Optional.of(key));
        assertThat(policy.canReplace(old,replacement)).isFalse();
        var now=LocalDateTime.of(2026,11,11,12,0);
        key.setRevokedAt(now);
        assertThat(policy.canReplace(old,replacement)).isFalse();
        key.setBrokerRevokedAt(now.minusSeconds(1));
        assertThat(policy.canReplace(old,replacement)).isFalse();
        key.setBrokerRevokedAt(now.plusSeconds(1));
        assertThat(policy.canReplace(old,replacement)).isTrue();
        assertThat(policy.canReplace(old,old)).isFalse();
        assertThat(policy.canActivate(old)).isFalse();
    }
}
