package com.smartdoor.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smartdoor.backend.device.dto.DeviceView.ConnectionStatus;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeviceConnectionPolicyTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
	private final DeviceConnectionPolicy policy = new DeviceConnectionPolicy(CLOCK, 90);

	@Test
	void unknownBeforeFirstHeartbeat() {
		assertThat(policy.status(null)).isEqualTo(ConnectionStatus.UNKNOWN);
	}

	@Test
	void onlineThroughTimeoutBoundaryAndOfflineOnlyAfterIt() {
		LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
		assertThat(policy.status(now.minusSeconds(89))).isEqualTo(ConnectionStatus.ONLINE);
		assertThat(policy.status(now.minusSeconds(90))).isEqualTo(ConnectionStatus.ONLINE);
		assertThat(policy.status(now.minusSeconds(90).minusNanos(1_000_000)))
				.isEqualTo(ConnectionStatus.OFFLINE);
	}

	@Test
	void connectivityDoesNotActivateDisabledDeviceOrChangeItsSibling() {
		Device disabled = new Device();
		disabled.setLifecycleStatus(Device.LifecycleStatus.DISABLED);
		disabled.setLastHeartbeatAt(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
		Device sibling = new Device();
		assertThat(policy.view(disabled).connectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
		assertThat(disabled.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.DISABLED);
		assertThat(policy.view(sibling).connectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
	}

	@Test
	void rejectsNonpositiveTimeout() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DeviceConnectionPolicy(CLOCK, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new DeviceConnectionPolicy(CLOCK, -1));
	}
}
