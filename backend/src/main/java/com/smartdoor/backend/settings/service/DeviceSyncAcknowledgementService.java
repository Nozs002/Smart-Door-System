package com.smartdoor.backend.settings.service;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.device.service.DeviceOperationalService;
import com.smartdoor.backend.device.service.DeviceOperationalService.OperationalDevice;
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
	private final DeviceOperationalService operational;
	private final DeviceRepository devices;
	private final DoorConfigRepository configs;

	public DeviceSyncAcknowledgementService(DeviceOperationalService operational,
			DeviceRepository devices, DoorConfigRepository configs) {
		this.operational = operational; this.devices = devices; this.configs = configs;
	}

	@Transactional
	public void acknowledgeConfiguration(AuthenticatedDeviceIdentity identity, long version) {
		OperationalDevice verified = operational.requireActive(identity);
		DoorConfig config = config(verified.doorId());
		if (version < 1 || version > config.getVersion()) throw invalidVersion();
		Device device = locked(verified);
		if (device.getAppliedConfigVersion() == null || version > device.getAppliedConfigVersion()) {
			device.setAppliedConfigVersion(version);
		}
	}

	@Transactional
	public void acknowledgeCredentials(AuthenticatedDeviceIdentity identity, long version) {
		OperationalDevice verified = operational.requireActive(identity);
		if (verified.nodeType() != Device.NodeType.NODE_1) {
			throw new ConflictException("Chỉ Node 1 được xác nhận phiên bản PIN/RFID.");
		}
		DoorConfig config = config(verified.doorId());
		if (version < 1 || version > config.getCredentialsVersion()) throw invalidVersion();
		Device device = locked(verified);
		if (device.getAppliedPinVersion() == null || version > device.getAppliedPinVersion()) {
			device.setAppliedPinVersion(version);
		}
	}

	private DoorConfig config(Long doorId) {
		return configs.findById(doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."));
	}

	private Device locked(OperationalDevice device) {
		return devices.findByIdAndDoorIdForUpdate(device.deviceId(), device.doorId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
	}

	private BadRequestException invalidVersion() {
		return new BadRequestException("Phiên bản ACK không hợp lệ hoặc chưa từng được phát hành.");
	}
}
