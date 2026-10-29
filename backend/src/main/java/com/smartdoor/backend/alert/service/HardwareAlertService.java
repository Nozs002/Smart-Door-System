package com.smartdoor.backend.alert.service;

import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.repository.AlertRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.device.service.DeviceOperationalService;
import com.smartdoor.backend.device.service.DeviceOperationalService.OperationalDevice;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumer nghiệp vụ được MQTT adapter gọi sau khi xác thực certificate/ACL của thiết bị. */
@Service
public class HardwareAlertService {
	private final DeviceOperationalService operational;
	private final AlertRepository alerts;
	private final DoorRepository doors;
	private final DoorConfigRepository configs;
	private final OwnerAlertNotifier notifier;

	public HardwareAlertService(DeviceOperationalService operational, AlertRepository alerts,
			DoorRepository doors, DoorConfigRepository configs, OwnerAlertNotifier notifier) {
		this.operational = operational; this.alerts = alerts; this.doors = doors;
		this.configs = configs; this.notifier = notifier;
	}

	@Transactional
	public AlertView receive(AuthenticatedDeviceIdentity identity, HardwareAlertEvent event) {
		event = normalize(event);
		if (identity == null || identity.doorId() == null) throw new BadRequestException("Thiếu danh tính thiết bị đã xác thực.");
		// All writers serialize on Door first, so dedupe and owner notification are atomic.
		Door door = doors.findByIdForUpdate(identity.doorId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
		OperationalDevice source = operational.requireActive(identity);
		validateShapeAndRole(source, event);
		Alert existing = alerts.findByEventId(event.eventId()).orElse(null);
		if (existing != null) {
			if (!existing.getDoorId().equals(source.doorId()) || !Objects.equals(existing.getSourceDeviceId(), source.deviceId())
					|| existing.getType() != event.type()
					|| !Objects.equals(existing.getObservedTemperature(), event.observedTemperature())
					|| (event.occurredAt() != null && !event.occurredAt().equals(existing.getTriggeredAt()))) {
				throw new ConflictException("eventId đã được sử dụng cho một sự kiện khác.");
			}
			return AlertView.from(existing, door.getName());
		}
		validateNewEvent(source, event);
		Alert alert = new Alert();
		alert.setEventId(event.eventId());
		alert.setDoorId(source.doorId());
		alert.setSourceDeviceId(source.deviceId());
		alert.setType(event.type());
		alert.setSeverity(event.type() == Alert.Type.HIGH_TEMPERATURE ? Alert.Severity.CRITICAL : Alert.Severity.WARNING);
		alert.setObservedTemperature(event.observedTemperature());
		alert.setTriggeredAt(event.occurredAt());
		alert = alerts.saveAndFlush(alert);
		AlertView view = AlertView.from(alert, door.getName());
		if (door.getOwnerAccountId() != null) notifier.notifyOwner(door.getOwnerAccountId(), view);
		return view;
	}

	private HardwareAlertEvent normalize(HardwareAlertEvent event) {
		if (event == null || event.eventId() == null || event.eventId().isBlank() || event.eventId().length() > 100
				|| event.type() == null) throw new BadRequestException("Sự kiện cảnh báo không hợp lệ.");
		BigDecimal temperature = event.observedTemperature() == null ? null
				: event.observedTemperature().setScale(2, RoundingMode.HALF_UP);
		if (temperature != null && temperature.abs().compareTo(new BigDecimal("999.99")) > 0) {
			throw new BadRequestException("Giá trị nhiệt độ vượt phạm vi lưu trữ.");
		}
		return new HardwareAlertEvent(event.eventId().strip(), event.type(), temperature,
				event.occurredAt() == null ? null : event.occurredAt().truncatedTo(ChronoUnit.MILLIS));
	}

	private void validateShapeAndRole(OperationalDevice source, HardwareAlertEvent event) {
		if (event.type() == Alert.Type.HIGH_TEMPERATURE) {
			if (source.nodeType() != Device.NodeType.NODE_2 || event.observedTemperature() == null) {
				throw new ConflictException("Cảnh báo nhiệt độ chỉ được nhận từ Node 2.");
			}
		} else if (event.observedTemperature() != null) {
			throw new BadRequestException("Chỉ cảnh báo nhiệt độ được chứa observedTemperature.");
		}
		if (event.type() == Alert.Type.TOO_MANY_FAILURES && source.nodeType() != Device.NodeType.NODE_1) {
			throw new ConflictException("Cảnh báo xác thực sai chỉ được nhận từ Node 1.");
		}
		if (event.type() == Alert.Type.DOOR_OPEN_TOO_LONG && source.nodeType() != Device.NodeType.NODE_2) {
			throw new ConflictException("Cảnh báo cửa mở lâu chỉ được nhận từ Node 2.");
		}
	}

	private void validateNewEvent(OperationalDevice source, HardwareAlertEvent event) {
		if (event.type() == Alert.Type.HIGH_TEMPERATURE) {
			DoorConfig config = configs.findById(source.doorId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."));
			if (event.observedTemperature().compareTo(config.getTemperatureThreshold()) <= 0) {
				throw new BadRequestException("Nhiệt độ chưa vượt ngưỡng đang cấu hình.");
			}
		}
	}
}
