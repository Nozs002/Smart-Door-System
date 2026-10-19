package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal gate for access/configuration modules; connectivity never grants operational permission. */
@Service
@Transactional(readOnly = true)
public class DeviceOperationalService {
	private final DeviceRepository devices;

	public DeviceOperationalService(DeviceRepository devices) {
		this.devices = devices;
	}

	/**
	 * Transport callers must supply identity verified from credentials and ACL.
	 * The returned immutable identity is a point-in-time check. A caller performing
	 * a later write/publish must recheck lifecycle in its transaction/workflow and
	 * coordinate external revocation; this read cannot lock future Broker activity.
	 */
	public OperationalDevice requireActive(AuthenticatedDeviceIdentity identity) {
		if (identity == null || identity.doorId() == null || identity.deviceCode() == null
				|| identity.deviceCode().isBlank() || identity.nodeType() == null) {
			throw new BadRequestException("Thiếu danh tính thiết bị đã xác thực.");
		}
		Device device = devices.findByDeviceCode(identity.deviceCode())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
		if (!Objects.equals(device.getDoorId(), identity.doorId())
				|| device.getNodeType() != identity.nodeType()) {
			throw new ResourceNotFoundException("Không tìm thấy thiết bị.");
		}
		if (device.getLifecycleStatus() != Device.LifecycleStatus.ACTIVE) {
			throw new ConflictException("Thiết bị chưa ở trạng thái hoạt động.");
		}
		return new OperationalDevice(device.getId(), device.getDoorId(), device.getDeviceCode(), device.getNodeType());
	}

	public record OperationalDevice(Long deviceId, Long doorId, String deviceCode, Device.NodeType nodeType) {
	}
}
