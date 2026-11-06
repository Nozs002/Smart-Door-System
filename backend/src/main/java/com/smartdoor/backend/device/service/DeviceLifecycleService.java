package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.Device.LifecycleStatus;
import com.smartdoor.backend.device.entity.Device.NodeType;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent.EventType;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every writer takes the Door lock before Device locks, including the technical CLI. */
@Service
@Transactional
public class DeviceLifecycleService {

	private final DoorOwnershipService ownership;
	private final DoorRepository doors;
	private final DeviceRepository devices;
	private final DeviceLifecycleEventRepository events;
	private final DeviceReadinessPolicy readiness;
	private final DeviceConnectionPolicy connections;
	private final Clock clock;

	public DeviceLifecycleService(DoorOwnershipService ownership, DoorRepository doors,
			DeviceRepository devices, DeviceLifecycleEventRepository events,
			DeviceReadinessPolicy readiness, DeviceConnectionPolicy connections, Clock clock) {
		this.ownership = ownership;
		this.doors = doors;
		this.devices = devices;
		this.events = events;
		this.readiness = readiness;
		this.connections = connections;
		this.clock = clock;
	}

	public DeviceView changeLifecycle(Long accountId, Long doorId, Long deviceId, Action action,
			String reason) {
		Door door = ownership.requireOwnerForUpdate(accountId, doorId);
		Device device = requireDeviceForUpdate(deviceId, doorId);
		if (action == null) {
			throw new BadRequestException("Vui lòng chọn thao tác vòng đời thiết bị.");
		}
		String validatedReason = validateReason(reason, action == Action.RETIRE);
		LifecycleStatus before = device.getLifecycleStatus();
		EventType eventType;
		LifecycleStatus target;
		switch (action) {
			case ACTIVATE -> {
				requireStatus(device, LifecycleStatus.PROVISIONED);
				requireReady(door, device);
				requireFreeSlot(device);
				target = LifecycleStatus.ACTIVE;
				eventType = EventType.ACTIVATED;
			}
			case DISABLE -> {
				requireStatus(device, LifecycleStatus.ACTIVE);
				target = LifecycleStatus.DISABLED;
				eventType = EventType.DISABLED;
			}
			case REACTIVATE -> {
				requireStatus(device, LifecycleStatus.DISABLED);
				requireReady(door, device);
				requireFreeSlot(device);
				target = LifecycleStatus.ACTIVE;
				eventType = EventType.REACTIVATED;
			}
			case RETIRE -> {
				requireReplaceable(device);
				target = LifecycleStatus.RETIRED;
				eventType = EventType.RETIRED;
			}
			default -> throw new BadRequestException("Thao tác vòng đời thiết bị không được hỗ trợ.");
		}
		applyStatus(device, target);
		devices.saveAndFlush(device);
		recordEvent(device, accountId, eventType, before, target, null, validatedReason);
		reconcileDoorStatus(door);
		return connections.view(device);
	}

	/**
	 * Replaces a technical-registered PROVISIONED device. Database work is atomic;
	 * this does not claim to revoke Broker credentials or erase local firmware data.
	 */
	public DeviceView replaceDevice(Long accountId, Long doorId, Long oldDeviceId,
			Long replacementDeviceId, String reason) {
		Door door = ownership.requireOwnerForUpdate(accountId, doorId);
		String validatedReason = validateReason(reason, true);
		if (oldDeviceId == null || oldDeviceId.equals(replacementDeviceId)) {
			throw new BadRequestException("Thiết bị thay thế phải khác thiết bị cũ.");
		}
		Device previous = requireDeviceForUpdate(oldDeviceId, doorId);
		Device replacement = requireDeviceForUpdate(replacementDeviceId, doorId);
		requireReplaceable(previous);
		requireStatus(replacement, LifecycleStatus.PROVISIONED);
		if (previous.getNodeType() != replacement.getNodeType()) {
			throw new ConflictException("Thiết bị thay thế phải có cùng vai trò Node với thiết bị cũ.");
		}
		requireReady(door, replacement);
		if (!readiness.canReplace(previous, replacement)) {
			throw new ConflictException("Chưa xác minh việc thu hồi quyền kết nối của thiết bị cũ.");
		}
		List<Device> activeSlot = devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(
				doorId, previous.getNodeType(), LifecycleStatus.ACTIVE);
		if (activeSlot.stream().anyMatch(device -> !device.getId().equals(previous.getId()))) {
			throw new ConflictException("Đã có thiết bị khác đang hoạt động ở vai trò Node này.");
		}
		LifecycleStatus previousStatus = previous.getLifecycleStatus();
		applyStatus(previous, LifecycleStatus.RETIRED);
		// Flush old slot before activating the new one: MySQL's generated-column
		// unique constraint must never observe two ACTIVE devices in the same slot.
		devices.saveAndFlush(previous);
		applyStatus(replacement, LifecycleStatus.ACTIVE);
		devices.saveAndFlush(replacement);
		recordEvent(previous, accountId, EventType.REPLACED, previousStatus,
				LifecycleStatus.RETIRED, replacement.getId(), validatedReason);
		recordEvent(replacement, accountId, EventType.ACTIVATED, LifecycleStatus.PROVISIONED,
				LifecycleStatus.ACTIVE, null, validatedReason);
		reconcileDoorStatus(door);
		return connections.view(replacement);
	}

	/** Technical-only CLI entry point, never a customer HTTP route. */
	public DeviceView registerReplacement(Long doorId, Long oldDeviceId, String newDeviceCode,
			String reason) {
		doors.findByIdForUpdate(doorId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
		Device previous = requireDeviceForUpdate(oldDeviceId, doorId);
		requireReplaceable(previous);
		String validatedReason = validateReason(reason, true);
		if (newDeviceCode == null || !newDeviceCode.strip().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
			throw new BadRequestException("Mã thiết bị phải có 1–100 ký tự chữ cái không dấu, chữ số, dấu chấm, gạch dưới hoặc gạch ngang.");
		}
		String code = newDeviceCode.strip();
		if (devices.existsByDeviceCode(code)) {
			throw new ConflictException("Mã thiết bị đã được đăng ký.");
		}
		Device replacement = new Device();
		replacement.setDoorId(doorId);
		replacement.setDeviceCode(code);
		replacement.setNodeType(previous.getNodeType());
		replacement.setLifecycleStatus(LifecycleStatus.PROVISIONED);
		replacement.setRegisteredAt(now());
		devices.saveAndFlush(replacement);
		recordEvent(replacement, null, EventType.PROVISIONED, null, LifecycleStatus.PROVISIONED,
				null, validatedReason);
		return connections.view(replacement);
	}

	private void requireReady(Door door, Device device) {
		if (door.getOwnerAccountId() == null || door.getStatus() == Door.Status.UNCLAIMED
				|| door.getStatus() == Door.Status.DISABLED) {
			throw new ConflictException("Cửa chưa đủ điều kiện để kích hoạt thiết bị.");
		}
		if (!readiness.canActivate(device)) {
			throw new ConflictException("Chưa xác minh danh tính thiết bị hoặc xác nhận áp dụng cấu hình/PIN.");
		}
	}

	private void requireFreeSlot(Device device) {
		if (!devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(device.getDoorId(),
				device.getNodeType(), LifecycleStatus.ACTIVE).isEmpty()) {
			throw new ConflictException("Đã có thiết bị đang hoạt động ở vai trò Node này.");
		}
	}

	private static void requireStatus(Device device, LifecycleStatus expected) {
		if (device.getLifecycleStatus() != expected) {
			throw new ConflictException("Thao tác không phù hợp với trạng thái vòng đời hiện tại của thiết bị.");
		}
	}

	private static void requireReplaceable(Device device) {
		if (device.getLifecycleStatus() != LifecycleStatus.ACTIVE
				&& device.getLifecycleStatus() != LifecycleStatus.DISABLED) {
			throw new ConflictException("Chỉ có thể ngừng sử dụng hoặc thay thế thiết bị đang hoạt động hay đã vô hiệu hóa.");
		}
	}

	private Device requireDeviceForUpdate(Long deviceId, Long doorId) {
		if (deviceId == null) {
			throw new BadRequestException("Vui lòng cung cấp ID thiết bị.");
		}
		return devices.findByIdAndDoorIdForUpdate(deviceId, doorId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
	}

	private void applyStatus(Device device, LifecycleStatus status) {
		device.setLifecycleStatus(status);
		if (status == LifecycleStatus.ACTIVE && device.getActivatedAt() == null) {
			device.setActivatedAt(now());
		} else if (status == LifecycleStatus.RETIRED) {
			device.setRetiredAt(now());
		}
	}

	private void recordEvent(Device device, Long actorAccountId, EventType eventType,
			LifecycleStatus from, LifecycleStatus to, Long replacementDeviceId, String reason) {
		DeviceLifecycleEvent event = new DeviceLifecycleEvent();
		event.setEventId(UUID.randomUUID().toString());
		event.setDeviceId(device.getId());
		event.setDoorId(device.getDoorId());
		event.setActorAccountId(actorAccountId);
		event.setEventType(eventType);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setReplacementDeviceId(replacementDeviceId);
		event.setReason(reason);
		event.setOccurredAt(now());
		events.save(event);
	}

	private void reconcileDoorStatus(Door door) {
		if (door.getOwnerAccountId() == null || door.getStatus() == Door.Status.DISABLED) {
			return;
		}
		List<Device> active = devices.findAllByDoorId(door.getId()).stream()
				.filter(device -> device.getLifecycleStatus() == LifecycleStatus.ACTIVE).toList();
		boolean bothRolesReady = active.size() == 2
				&& active.stream().anyMatch(device -> device.getNodeType() == NodeType.NODE_1)
				&& active.stream().anyMatch(device -> device.getNodeType() == NodeType.NODE_2)
				&& active.stream().allMatch(readiness::canActivate);
		door.setStatus(bothRolesReady ? Door.Status.ACTIVE : Door.Status.SETUP_PENDING);
	}

	private static String validateReason(String reason, boolean required) {
		String value = reason == null ? null : reason.strip();
		if (required && (value == null || value.isEmpty())) {
			throw new BadRequestException("Vui lòng nhập lý do khi ngừng sử dụng hoặc thay thế thiết bị.");
		}
		if (value != null && value.length() > 500) {
			throw new BadRequestException("Lý do không được vượt quá 500 ký tự.");
		}
		return value == null || value.isEmpty() ? null : value;
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
	}
}
