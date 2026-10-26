package com.smartdoor.backend.alert.dto;

import com.smartdoor.backend.alert.entity.Alert;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlertView(Long id, String eventId, Long doorId, String doorName, Long sourceDeviceId,
		Alert.Type type, Alert.Severity severity, Alert.Status status, BigDecimal observedTemperature,
		LocalDateTime triggeredAt, LocalDateTime resolvedAt) {
	public static AlertView from(Alert alert, String doorName) {
		return new AlertView(alert.getId(), alert.getEventId(), alert.getDoorId(), doorName,
				alert.getSourceDeviceId(), alert.getType(), alert.getSeverity(), alert.getStatus(),
				alert.getObservedTemperature(), alert.getTriggeredAt(), alert.getResolvedAt());
	}
}
