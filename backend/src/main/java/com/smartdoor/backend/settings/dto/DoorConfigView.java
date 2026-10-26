package com.smartdoor.backend.settings.dto;

import com.smartdoor.backend.settings.entity.DoorConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DoorConfigView(Long doorId, Integer maxFailedAttempts, Integer lockoutSeconds,
		BigDecimal temperatureThreshold, Integer openTimeoutSeconds, Long version,
		Long credentialsVersion, LocalDateTime updatedAt, List<DeviceSyncView> devices) {

	public static DoorConfigView from(DoorConfig config, List<DeviceSyncView> devices) {
		return new DoorConfigView(config.getDoorId(), config.getMaxFailedAttempts(),
				config.getLockoutSeconds(), config.getTemperatureThreshold(),
				config.getOpenTimeoutSeconds(), config.getVersion(), config.getCredentialsVersion(),
				config.getUpdatedAt(), devices);
	}
}
