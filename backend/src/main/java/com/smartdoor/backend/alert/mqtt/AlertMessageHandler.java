package com.smartdoor.backend.alert.mqtt;

import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.service.HardwareAlertEvent;
import com.smartdoor.backend.alert.service.HardwareAlertService;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** Adapter module-level; shared MQTT dispatcher supplies an already authenticated identity. */
@Component
public class AlertMessageHandler {
	private final HardwareAlertService alerts;

	public AlertMessageHandler(HardwareAlertService alerts) {
		this.alerts = alerts;
	}

	public AlertView handle(AuthenticatedDeviceIdentity identity, AlertMessage message) {
		if (message == null) throw new BadRequestException("Sự kiện cảnh báo không hợp lệ.");
		return alerts.receive(identity, new HardwareAlertEvent(message.eventId(), message.type(),
				message.observedTemperature(), message.occurredAt()));
	}

	public record AlertMessage(String eventId, Alert.Type type, BigDecimal observedTemperature,
			LocalDateTime occurredAt) { }
}
