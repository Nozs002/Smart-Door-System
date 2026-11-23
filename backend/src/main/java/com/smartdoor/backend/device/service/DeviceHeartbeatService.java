package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trusted transport boundary only. No public API or payload can assert identity or ACKs. */
@Service
public class DeviceHeartbeatService {

	private final DoorRepository doors;
	private final DeviceRepository devices;
	private final DeviceConnectionPolicy connections;
	private final Clock clock;

	public DeviceHeartbeatService(DoorRepository doors, DeviceRepository devices,
			DeviceConnectionPolicy connections, Clock clock) {
		this.doors = doors;
		this.devices = devices;
		this.connections = connections;
		this.clock = clock;
	}

	/**
	 * Caller must derive identity from authenticated transport credentials and ACL,
	 * never from doorId/deviceCode fields asserted by the heartbeat payload.
	 * Heartbeat deliberately cannot modify applied_config_version/applied_pin_version.
	 */
	@Transactional
	public DeviceView recordHeartbeat(AuthenticatedDeviceIdentity identity, String firmwareVersion) {
		if (identity == null || identity.doorId() == null || identity.deviceCode() == null
				|| identity.deviceCode().isBlank() || identity.nodeType() == null) {
			throw new BadRequestException("Thiếu danh tính thiết bị đã xác thực.");
		}
		if (firmwareVersion != null && firmwareVersion.strip().length() > 50) {
			throw new BadRequestException("Phiên bản firmware không được vượt quá 50 ký tự.");
		}
		doors.findByIdForUpdate(identity.doorId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		Device device = devices.findByDeviceCode(identity.deviceCode())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		if (!Objects.equals(device.getDoorId(), identity.doorId())
				|| device.getNodeType() != identity.nodeType()) {
			throw new ResourceNotFoundException("Không tìm thấy thiết bị.");
		}
		if (device.getLifecycleStatus() == Device.LifecycleStatus.RETIRED) {
			throw new ConflictException("Thiết bị đã ngừng sử dụng không được gửi heartbeat.");
		}
		device.setLastHeartbeatAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
				.truncatedTo(ChronoUnit.MILLIS));
		if (firmwareVersion != null && !firmwareVersion.isBlank()) {
			device.setFirmwareVersion(firmwareVersion.strip());
		}
		devices.save(device);
		return connections.view(device);
	}

	public record AuthenticatedDeviceIdentity(String deviceCode, Long doorId, Device.NodeType nodeType) {
	}
}
