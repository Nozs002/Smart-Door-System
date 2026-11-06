package com.smartdoor.backend.settings.dto;

import com.smartdoor.backend.device.entity.Device;
import java.time.LocalDateTime;

public record DeviceSyncView(Long deviceId, String deviceCode, Device.NodeType nodeType,
		Device.LifecycleStatus lifecycleStatus, Long appliedConfigVersion,
		Long appliedCredentialsVersion, SyncState configState, SyncState credentialsState,
		LocalDateTime lastHeartbeatAt) {

	public enum SyncState { SYNCED, PENDING, UNKNOWN, NOT_APPLICABLE }
}
