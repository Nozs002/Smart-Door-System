package com.smartdoor.backend.device.dto;

import com.smartdoor.backend.device.entity.Device;
import java.time.LocalDateTime;

/** Lifecycle and connectivity are independent, including for disabled nodes. */
public record DeviceView(Long id, Long doorId, String deviceCode, Device.NodeType nodeType,
		Device.LifecycleStatus lifecycleStatus, ConnectionStatus connectionStatus,
		LocalDateTime lastHeartbeatAt, String firmwareVersion,
		Long appliedConfigVersion, Long appliedPinVersion) {

	public enum ConnectionStatus {
		UNKNOWN, ONLINE, OFFLINE
	}
}
