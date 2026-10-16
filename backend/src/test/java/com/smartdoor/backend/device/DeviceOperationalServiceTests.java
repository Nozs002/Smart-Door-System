package com.smartdoor.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.device.service.DeviceOperationalService;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeviceOperationalServiceTests {
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DeviceOperationalService service = new DeviceOperationalService(devices);
	private final AuthenticatedDeviceIdentity identity = new AuthenticatedDeviceIdentity("n1", 10L, Device.NodeType.NODE_1);

	@Test
	void acceptsOnlyActiveDeviceAndReturnsImmutableIdentity() {
		Device node = node(Device.LifecycleStatus.ACTIVE);
		when(devices.findByDeviceCode("n1")).thenReturn(Optional.of(node));
		assertThat(service.requireActive(identity).deviceId()).isEqualTo(11L);
		assertThat(service.requireActive(identity).nodeType()).isEqualTo(Device.NodeType.NODE_1);
	}

	@Test
	void onlineDoesNotAllowProvisionedDisabledOrRetiredDeviceToOperate() {
		for (Device.LifecycleStatus status : new Device.LifecycleStatus[] {
				Device.LifecycleStatus.PROVISIONED, Device.LifecycleStatus.DISABLED, Device.LifecycleStatus.RETIRED}) {
			Device node = node(status);
			node.setLastHeartbeatAt(LocalDateTime.parse("2026-10-16T05:00:00"));
			when(devices.findByDeviceCode("n1")).thenReturn(Optional.of(node));
			assertThatThrownBy(() -> service.requireActive(identity)).isInstanceOf(ConflictException.class);
		}
	}

	@Test
	void rejectsIdentityFromWrongDoorOrRole() {
		Device node = node(Device.LifecycleStatus.ACTIVE);
		when(devices.findByDeviceCode("n1")).thenReturn(Optional.of(node));
		assertThatThrownBy(() -> service.requireActive(new AuthenticatedDeviceIdentity("n1", 20L, Device.NodeType.NODE_1)))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.requireActive(new AuthenticatedDeviceIdentity("n1", 10L, Device.NodeType.NODE_2)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private Device node(Device.LifecycleStatus status) {
		Device node = new Device();
		node.setId(11L);
		node.setDoorId(10L);
		node.setDeviceCode("n1");
		node.setNodeType(Device.NodeType.NODE_1);
		node.setLifecycleStatus(status);
		return node;
	}
}
