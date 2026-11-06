package com.smartdoor.backend.settings.dto;

import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.door.entity.Door.OperatingMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DoorConfigView(Long doorId, Integer maxFailedAttempts, Integer lockoutSeconds,
		BigDecimal temperatureThreshold, Integer openTimeoutSeconds, Long version,
		Long credentialsVersion, LocalDateTime updatedAt, List<DeviceSyncView> devices,
		OperatingMode operatingMode, boolean fireAlarmEnabled, boolean doorOpenAlertEnabled) {

	public static DoorConfigView from(DoorConfig config, List<DeviceSyncView> devices, OperatingMode mode) {
		return new DoorConfigView(config.getDoorId(), config.getMaxFailedAttempts(),
				config.getLockoutSeconds(), config.getTemperatureThreshold(),
				config.getOpenTimeoutSeconds(), config.getVersion(), config.getCredentialsVersion(),
				config.getUpdatedAt(), devices, mode, config.isFireAlarmEnabled(), config.isDoorOpenAlertEnabled());
	}
}
