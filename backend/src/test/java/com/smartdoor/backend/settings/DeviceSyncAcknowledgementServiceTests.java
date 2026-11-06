package com.smartdoor.backend.settings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.settings.service.DeviceSyncAcknowledgementService;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceSyncAcknowledgementServiceTests {
	private final DoorRepository doors = mock(DoorRepository.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DoorConfigRepository configs = mock(DoorConfigRepository.class);
	private final DeviceSyncAcknowledgementService service = new DeviceSyncAcknowledgementService(doors, devices, configs);
	private Device node;
	private DoorConfig config;
	private final AuthenticatedDeviceIdentity identity = new AuthenticatedDeviceIdentity("SETUP-N1", 5L, Device.NodeType.NODE_1);

	@BeforeEach void setup() {
		Door door = new Door(); door.setId(5L); door.setOwnerAccountId(7L);
		node = new Device(); node.setId(8L); node.setDoorId(5L); node.setDeviceCode("SETUP-N1");
		node.setNodeType(Device.NodeType.NODE_1); node.setLifecycleStatus(Device.LifecycleStatus.PROVISIONED);
		config = new DoorConfig(); config.setDoorId(5L); config.setVersion(3L); config.setCredentialsVersion(4L);
		when(doors.findByIdForUpdate(5L)).thenReturn(Optional.of(door));
		when(devices.findByDeviceCode("SETUP-N1")).thenReturn(Optional.of(node));
		when(devices.findByIdAndDoorIdForUpdate(8L, 5L)).thenReturn(Optional.of(node));
		when(configs.findById(5L)).thenReturn(Optional.of(config));
	}

	@Test void setupNodeCanAcknowledgeBeforeActivationAndOldAckNeverRollsBack() {
		service.acknowledgeConfiguration(identity, 3);
		service.acknowledgeConfiguration(identity, 1);
		assertThat(node.getAppliedConfigVersion()).isEqualTo(3L);
		assertThat(node.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
		var order = inOrder(doors, devices);
		order.verify(doors).findByIdForUpdate(5L);
		order.verify(devices).findByDeviceCode("SETUP-N1");
		order.verify(devices).findByIdAndDoorIdForUpdate(8L, 5L);
	}

	@Test void disabledNodeCanSynchronizeWithoutReceivingBusinessPermission() {
		node.setLifecycleStatus(Device.LifecycleStatus.DISABLED);
		service.acknowledgeCredentials(identity, 4);
		assertThat(node.getAppliedPinVersion()).isEqualTo(4L);
		assertThat(node.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.DISABLED);
	}

	@Test void futureAckIsRejectedWithoutMutatingVersion() {
		assertThatThrownBy(() -> service.acknowledgeConfiguration(identity, 4)).isInstanceOf(BadRequestException.class);
		assertThat(node.getAppliedConfigVersion()).isNull();
	}

	@Test void retiredNodeCannotAcknowledge() {
		node.setLifecycleStatus(Device.LifecycleStatus.RETIRED);
		assertThatThrownBy(() -> service.acknowledgeConfiguration(identity, 3)).isInstanceOf(ConflictException.class);
		assertThat(node.getAppliedConfigVersion()).isNull();
	}

	@Test void wrongDoorOrRoleCannotAcknowledge() {
		node.setDoorId(6L);
		assertThatThrownBy(() -> service.acknowledgeConfiguration(identity, 3)).isInstanceOf(ResourceNotFoundException.class);
		node.setDoorId(5L); node.setNodeType(Device.NodeType.NODE_2);
		assertThatThrownBy(() -> service.acknowledgeConfiguration(identity, 3)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test void nodeTwoCannotAcknowledgeCredentials() {
		node.setNodeType(Device.NodeType.NODE_2);
		var nodeTwo = new AuthenticatedDeviceIdentity("SETUP-N1", 5L, Device.NodeType.NODE_2);
		assertThatThrownBy(() -> service.acknowledgeCredentials(nodeTwo, 4)).isInstanceOf(ConflictException.class);
	}
}
