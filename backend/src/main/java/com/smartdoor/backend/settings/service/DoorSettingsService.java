package com.smartdoor.backend.settings.service;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.infrastructure.outbox.OutboxService;
import com.smartdoor.backend.settings.dto.DeviceSyncView;
import com.smartdoor.backend.settings.dto.DeviceSyncView.SyncState;
import com.smartdoor.backend.settings.dto.DoorConfigView;
import com.smartdoor.backend.settings.dto.UpdateDoorConfigRequest;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ChangeReason;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ConfigurationCommand;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.CredentialsCommand;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DoorSettingsService {
	private final DoorConfigRepository configs;
	private final DeviceRepository devices;
	private final DoorOwnershipService ownership;
	private final DeviceCommandPublisher publisher;
	private final OutboxService outbox;

	public DoorSettingsService(DoorConfigRepository configs, DeviceRepository devices,
			DoorOwnershipService ownership, DeviceCommandPublisher publisher, OutboxService outbox) {
		this.configs = configs; this.devices = devices; this.ownership = ownership; this.publisher = publisher;
		this.outbox = outbox;
	}

	public DoorConfigView get(Long accountId, Long doorId) {
		ownership.requireOwner(accountId, doorId);
		DoorConfig config = config(doorId);
		return DoorConfigView.from(config, syncViews(config));
	}

	@Transactional
	public DoorConfigView update(Long accountId, Long doorId, UpdateDoorConfigRequest request) {
		ownership.requireOwnerForUpdate(accountId, doorId);
		DoorConfig config = config(doorId);
		config.setMaxFailedAttempts(request.maxFailedAttempts());
		config.setLockoutSeconds(request.lockoutSeconds());
		config.setTemperatureThreshold(request.temperatureThreshold());
		config.setOpenTimeoutSeconds(request.openTimeoutSeconds());
		config.setVersion(config.getVersion() + 1);
		config = configs.saveAndFlush(config);
		for (Device device : setupDevices(doorId)) {
			publisher.publishConfiguration(new ConfigurationCommand(doorId, device.getDeviceCode(),
					config.getVersion(), config.getMaxFailedAttempts(), config.getLockoutSeconds(),
					config.getTemperatureThreshold(), config.getOpenTimeoutSeconds()));
		}
		return DoorConfigView.from(config, syncViews(config));
	}

	/** Integration point cho module credentials sau khi đổi PIN/thu hồi RFID thành công. */
	@Transactional
	public DoorConfigView requestCredentialsSync(Long accountId, Long doorId, ChangeReason reason) {
		ownership.requireOwnerForUpdate(accountId, doorId);
		DoorConfig config = config(doorId);
		config.setCredentialsVersion(config.getCredentialsVersion() + 1);
		config = configs.saveAndFlush(config);
		for (Device device : setupDevices(doorId)) {
			if (device.getNodeType() == Device.NodeType.NODE_1) {
				publisher.publishCredentials(new CredentialsCommand(doorId, device.getDeviceCode(),
						config.getCredentialsVersion(), reason));
			}
		}
		return DoorConfigView.from(config, syncViews(config));
	}

	/** Resend the current desired state without inventing a new configuration version. */
	@Transactional
	public DoorConfigView retrySync(Long accountId, Long doorId) {
		ownership.requireOwnerForUpdate(accountId, doorId);
		outbox.retry(accountId, doorId);
		DoorConfig config = config(doorId);
		for (Device device : setupDevices(doorId)) {
			publisher.publishConfiguration(new ConfigurationCommand(doorId, device.getDeviceCode(),
					config.getVersion(), config.getMaxFailedAttempts(), config.getLockoutSeconds(),
					config.getTemperatureThreshold(), config.getOpenTimeoutSeconds()));
			if (device.getNodeType() == Device.NodeType.NODE_1) {
				publisher.publishCredentials(new CredentialsCommand(doorId, device.getDeviceCode(),
						config.getCredentialsVersion(), ChangeReason.FULL_REFRESH));
			}
		}
		return DoorConfigView.from(config, syncViews(config));
	}

	private DoorConfig config(Long doorId) {
		return configs.findById(doorId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình cửa."));
	}

	private List<Device> setupDevices(Long doorId) {
		return devices.findAllByDoorId(doorId).stream()
				.filter(device -> device.getLifecycleStatus() != Device.LifecycleStatus.RETIRED).toList();
	}

	private List<DeviceSyncView> syncViews(DoorConfig config) {
		return devices.findAllByDoorIdOrderByNodeTypeAscRegisteredAtDescIdDesc(config.getDoorId()).stream()
				.map(device -> new DeviceSyncView(device.getId(), device.getDeviceCode(), device.getNodeType(),
						device.getLifecycleStatus(), device.getAppliedConfigVersion(), device.getAppliedPinVersion(),
						state(device.getAppliedConfigVersion(), config.getVersion()),
						device.getNodeType() == Device.NodeType.NODE_1
								? state(device.getAppliedPinVersion(), config.getCredentialsVersion()) : SyncState.NOT_APPLICABLE,
						device.getLastHeartbeatAt())).toList();
	}

	private SyncState state(Long applied, Long expected) {
		if (applied == null) return SyncState.UNKNOWN;
		return applied.equals(expected) ? SyncState.SYNCED : SyncState.PENDING;
	}
}
