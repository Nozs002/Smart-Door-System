package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.dto.DeviceView.ConnectionStatus;
import com.smartdoor.backend.device.entity.Device;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeviceConnectionPolicy {

	private final Clock clock;
	private final Duration timeout;

	public DeviceConnectionPolicy(Clock clock,
			@Value("${app.device.heartbeat-timeout-seconds:90}") long timeoutSeconds) {
		if (timeoutSeconds <= 0) {
			throw new IllegalArgumentException("Heartbeat timeout must be positive");
		}
		this.clock = clock;
		this.timeout = Duration.ofSeconds(timeoutSeconds);
	}

	public ConnectionStatus status(LocalDateTime lastHeartbeatAt) {
		if (lastHeartbeatAt == null) {
			return ConnectionStatus.UNKNOWN;
		}
		Duration elapsed = Duration.between(lastHeartbeatAt.toInstant(ZoneOffset.UTC), clock.instant());
		return elapsed.compareTo(timeout) > 0 ? ConnectionStatus.OFFLINE : ConnectionStatus.ONLINE;
	}

	public DeviceView view(Device device) {
		return new DeviceView(device.getId(), device.getDoorId(), device.getDeviceCode(),
				device.getNodeType(), device.getLifecycleStatus(), status(device.getLastHeartbeatAt()),
				device.getLastHeartbeatAt(), device.getFirmwareVersion(),
				device.getAppliedConfigVersion(), device.getAppliedPinVersion());
	}
}
