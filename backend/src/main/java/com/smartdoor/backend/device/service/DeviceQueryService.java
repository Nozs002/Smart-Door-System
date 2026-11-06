package com.smartdoor.backend.device.service;

import com.smartdoor.backend.device.dto.DeviceView;
import com.smartdoor.backend.device.dto.LifecycleEventView;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceQueryService {

	private final DoorOwnershipService ownership;
	private final DeviceRepository devices;
	private final DeviceLifecycleEventRepository events;
	private final DeviceConnectionPolicy connections;

	public DeviceQueryService(DoorOwnershipService ownership, DeviceRepository devices,
			DeviceLifecycleEventRepository events, DeviceConnectionPolicy connections) {
		this.ownership = ownership;
		this.devices = devices;
		this.events = events;
		this.connections = connections;
	}

	public List<DeviceView> listDevices(Long accountId, Long doorId) {
		ownership.requireOwner(accountId, doorId);
		return devices.findAllByDoorIdOrderByNodeTypeAscRegisteredAtDescIdDesc(doorId).stream()
				.map(connections::view).toList();
	}

	public DeviceView getDevice(Long accountId, Long doorId, Long deviceId) {
		ownership.requireOwner(accountId, doorId);
		return connections.view(requireDevice(deviceId, doorId));
	}

	public PageResponse<LifecycleEventView> history(Long accountId, Long doorId, Long deviceId,
			int page, int size) {
		ownership.requireOwner(accountId, doorId);
		var pageable = PageResponse.pageable(page, size);
		if (deviceId != null) {
			requireDevice(deviceId, doorId);
			return PageResponse.from(events.findAllByDeviceIdAndDoorIdOrderByOccurredAtDescIdDesc(
					deviceId, doorId, pageable).map(LifecycleEventView::from));
		}
		return PageResponse.from(events.findAllByDoorIdOrderByOccurredAtDescIdDesc(doorId, pageable)
				.map(LifecycleEventView::from));
	}

	private Device requireDevice(Long deviceId, Long doorId) {
		return devices.findByIdAndDoorId(deviceId, doorId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị."));
	}
}
