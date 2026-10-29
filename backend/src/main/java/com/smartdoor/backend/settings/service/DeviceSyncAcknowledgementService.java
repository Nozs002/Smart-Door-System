package com.smartdoor.backend.settings.service;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Điểm nhận ACK tin cậy từ MQTT adapter; không mở thành endpoint người dùng. */
@Service
public class DeviceSyncAcknowledgementService {
	private final DoorRepository doors;
	private final DeviceRepository devices;
	private final DoorConfigRepository configs;

	public DeviceSyncAcknowledgementService(DoorRepository doors,
			DeviceRepository devices, DoorConfigRepository configs) {
		this.doors = doors; this.devices = devices; this.configs = configs;
	}

	@Transactional
	public void acknowledgeConfiguration(AuthenticatedDeviceIdentity identity, long version) {
		Device device = locked(identity);
		DoorConfig config = config(device.getDoorId());
		if (version < 1 || version > config.getVersion()) throw invalidVersion();
		if (device.getAppliedConfigVersion() == null || version > device.getAppliedConfigVersion()) {
			device.setAppliedConfigVersion(version);
		}
	}

	@Transactional
	public void acknowledgeCredentials(AuthenticatedDeviceIdentity identity, long version) {
		Device device = locked(identity);
		if (device.getNodeType() != Device.NodeType.NODE_1) {
			throw new ConflictException("Chỉ Node 1 được xác nhận phiên bản PIN/RFID.");
		}
		DoorConfig config = config(device.getDoorId());
		if (version < 1 || version > config.getCredentialsVersion()) throw invalidVersion();
		if (device.getAppliedPinVersion() == null || version > device.getAppliedPinVersion()) {
			device.setAppliedPinVersion(version);
		}
	}

	private DoorConfig config(Long doorId) {
		return configs.findById(doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."));
	}

	private Device locked(AuthenticatedDeviceIdentity identity) {
		if (identity == null || identity.doorId() == null || identity.deviceCode() == null || identity.nodeType() == null) {
			throw new BadRequestException("Thiếu danh tính thiết bị đã xác thực.");
		}
		Door door = doors.findByIdForUpdate(identity.doorId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		if (door.getOwnerAccountId() == null) throw new ConflictException("Bộ cửa chưa được nhận quyền sở hữu.");
		Device candidate = devices.findByDeviceCode(identity.deviceCode())
				.filter(d -> identity.doorId().equals(d.getDoorId()) && identity.nodeType() == d.getNodeType())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		Device device = devices.findByIdAndDoorIdForUpdate(candidate.getId(), identity.doorId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		if (device.getLifecycleStatus() == Device.LifecycleStatus.RETIRED) {
			throw new ConflictException("Thiết bị đã ngừng sử dụng không được xác nhận cấu hình.");
		}
		return device;
	}

	private BadRequestException invalidVersion() {
		return new BadRequestException("Phiên bản ACK không hợp lệ hoặc chưa từng được phát hành.");
	}
}
