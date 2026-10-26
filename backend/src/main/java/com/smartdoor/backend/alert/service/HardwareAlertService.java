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
		OperationalDevice source = operational.requireActive(identity);
		validate(source, event);
		Alert existing = alerts.findByEventId(event.eventId()).orElse(null);
		if (existing != null) {
			if (!existing.getDoorId().equals(source.doorId())) throw new ConflictException("eventId đã thuộc cửa khác.");
			Door door = door(source.doorId());
			return AlertView.from(existing, door.getName());
		}
		Door door = door(source.doorId());
		Alert alert = new Alert();
		alert.setEventId(event.eventId().strip());
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

	private void validate(OperationalDevice source, HardwareAlertEvent event) {
		if (event == null || event.eventId() == null || event.eventId().isBlank() || event.eventId().length() > 100
				|| event.type() == null) throw new BadRequestException("Sự kiện cảnh báo không hợp lệ.");
		if (event.type() == Alert.Type.HIGH_TEMPERATURE) {
			if (source.nodeType() != Device.NodeType.NODE_2 || event.observedTemperature() == null) {
				throw new ConflictException("Cảnh báo nhiệt độ chỉ được nhận từ Node 2.");
			}
			DoorConfig config = configs.findById(source.doorId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."));
			if (event.observedTemperature().compareTo(config.getTemperatureThreshold()) <= 0) {
				throw new BadRequestException("Nhiệt độ chưa vượt ngưỡng đang cấu hình.");
			}
		} else if (event.observedTemperature() != null) {
			throw new BadRequestException("Chỉ cảnh báo nhiệt độ được chứa observedTemperature.");
		}
		if (event.type() == Alert.Type.TOO_MANY_FAILURES && source.nodeType() != Device.NodeType.NODE_1) {
			throw new ConflictException("Cảnh báo xác thực sai chỉ được nhận từ Node 1.");
		}
	}

	private Door door(Long id) {
		return doors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
	}
}
