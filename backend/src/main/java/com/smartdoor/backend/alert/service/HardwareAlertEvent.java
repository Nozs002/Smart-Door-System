package com.smartdoor.backend.alert.service;

import com.smartdoor.backend.alert.entity.Alert;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HardwareAlertEvent(String eventId, Alert.Type type, BigDecimal observedTemperature,
		LocalDateTime occurredAt) { }
