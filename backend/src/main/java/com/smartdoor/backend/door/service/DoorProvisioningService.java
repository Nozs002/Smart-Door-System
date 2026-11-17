package com.smartdoor.backend.door.service;

import com.smartdoor.backend.access.entity.LockoutStatus;
import com.smartdoor.backend.access.entity.LockoutStatusId;
import com.smartdoor.backend.access.repository.LockoutStatusRepository;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.entity.DoorActivationCode;
import com.smartdoor.backend.door.repository.DoorActivationCodeRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Công cụ kỹ thuật gọi service này; không expose thành API cho Account khách hàng. */
@Service
@Transactional
public class DoorProvisioningService {
	private final DoorRepository doors;
	private final DeviceRepository devices;
	private final DoorActivationCodeRepository codes;
	private final DeviceLifecycleEventRepository events;
	private final DoorConfigRepository configs;
	private final LockoutStatusRepository lockouts;
	private final ActivationCodes activationCodes;
	private final Clock clock;

	public DoorProvisioningService(DoorRepository doors, DeviceRepository devices,
			DoorActivationCodeRepository codes, DeviceLifecycleEventRepository events,
			DoorConfigRepository configs, LockoutStatusRepository lockouts, ActivationCodes activationCodes, Clock clock) {
		this.doors = doors; this.devices = devices; this.codes = codes; this.events = events;
		this.configs = configs; this.lockouts = lockouts; this.activationCodes = activationCodes; this.clock = clock;
	}

	public ProvisionResult provision(String name, String location, String node1Code, String node2Code) {
		if (name == null || name.isBlank() || name.length() > 100 || (location != null && location.length() > 255)) {
			throw new BadRequestException("Tên cửa bắt buộc, tối đa 100 ký tự; vị trí tối đa 255 ký tự.");
		}
		validateDeviceCode(node1Code);
		validateDeviceCode(node2Code);
		if (node1Code.equalsIgnoreCase(node2Code) || devices.findByDeviceCode(node1Code).isPresent()
				|| devices.findByDeviceCode(node2Code).isPresent()) {
			throw new ConflictException("Mã thiết bị đã tồn tại hoặc hai Node trùng mã.");
		}
		Door door = new Door();
		door.setName(name.strip());
		door.setLocation(location == null || location.isBlank() ? null : location.strip());
		door = doors.saveAndFlush(door);
		registerNode(door.getId(), node1Code, Device.NodeType.NODE_1);
		registerNode(door.getId(), node2Code, Device.NodeType.NODE_2);
		DoorConfig config = new DoorConfig();
		config.setDoorId(door.getId());
		configs.save(config);
		for (LockoutStatusId.Method method : LockoutStatusId.Method.values()) {
			LockoutStatus status = new LockoutStatus();
			status.setId(new LockoutStatusId(door.getId(), method));
			lockouts.save(status);
		}
		return issueCode(door.getId());
	}

	public ProvisionResult reissue(Long doorId) {
		Door door = doors.findByIdForUpdate(doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa."));
		if (door.getOwnerAccountId() != null || door.getStatus() != Door.Status.UNCLAIMED) {
			throw new ConflictException("Chỉ được cấp lại mã cho cửa chưa có chủ.");
		}
		LocalDateTime now = now();
		for (DoorActivationCode code : codes.findAllByDoorIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(doorId, now)) {
			code.setRevokedAt(now);
			codes.save(code);
		}
		return issueCode(doorId);
	}

	private ProvisionResult issueCode(Long doorId) {
		String plainCode;
		String hash;
		do {
			plainCode = activationCodes.generate();
			hash = activationCodes.hash(plainCode);
		} while (codes.existsByCodeHash(hash));
		DoorActivationCode code = new DoorActivationCode();
		code.setDoorId(doorId);
		code.setCodeHash(hash);
		code.setCreatedAt(now());
		code.setExpiresAt(code.getCreatedAt().plusDays(7));
		codes.saveAndFlush(code);
		return new ProvisionResult(doorId, plainCode, code.getExpiresAt());
	}

	private void registerNode(Long doorId, String deviceCode, Device.NodeType type) {
		Device device = new Device();
		device.setDoorId(doorId);
		device.setDeviceCode(deviceCode);
		device.setNodeType(type);
		device.setRegisteredAt(now());
		device = devices.saveAndFlush(device);
		DeviceLifecycleEvent event = new DeviceLifecycleEvent();
		event.setEventId(UUID.randomUUID().toString());
		event.setDeviceId(device.getId());
		event.setDoorId(doorId);
		event.setEventType(DeviceLifecycleEvent.EventType.PROVISIONED);
		event.setToStatus(Device.LifecycleStatus.PROVISIONED);
		event.setReason("Technical provisioning");
		event.setOccurredAt(now());
		events.save(event);
	}

	private void validateDeviceCode(String code) {
		if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
			throw new BadRequestException("Mã Node phải có 1–100 ký tự chữ/số, dấu chấm, gạch dưới hoặc gạch ngang.");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
	}

	/** Mã rõ chỉ trả cho CLI lúc cấp, không log hoặc expose qua REST. */
	public record ProvisionResult(Long doorId, String activationCode, LocalDateTime expiresAt) {
		@Override public String toString() { return "ProvisionResult[doorId=" + doorId + ", activationCode=REDACTED]"; }
	}
}
