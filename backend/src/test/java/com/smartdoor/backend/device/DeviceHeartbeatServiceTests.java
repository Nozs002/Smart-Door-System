package com.smartdoor.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdoor.backend.device.dto.DeviceView.ConnectionStatus;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import com.smartdoor.backend.device.service.DeviceHeartbeatService;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceHeartbeatServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00.123Z"), ZoneOffset.UTC);
	private final DoorRepository doors = mock(DoorRepository.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DeviceHeartbeatService service = new DeviceHeartbeatService(doors, devices,
			new DeviceConnectionPolicy(CLOCK, 90), CLOCK);
	private final AuthenticatedDeviceIdentity identity = new AuthenticatedDeviceIdentity("n1", 10L, Device.NodeType.NODE_1);
	private Device node;

	@BeforeEach
	void setUp() {
		node = new Device();
		node.setId(11L);
		node.setDoorId(10L);
		node.setDeviceCode("n1");
		node.setNodeType(Device.NodeType.NODE_1);
		when(doors.findByIdForUpdate(10L)).thenReturn(Optional.of(new Door()));
		when(devices.findByDeviceCode("n1")).thenReturn(Optional.of(node));
	}

	@Test
	void recordsBackendReceiveTimeAndNeverTreatsHeartbeatAsConfigurationAck() {
		node.setAppliedConfigVersion(3L);
		node.setAppliedPinVersion(2L);
		var result = service.recordHeartbeat(identity, " 1.2.3 ");
		assertThat(result.connectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
		assertThat(node.getLastHeartbeatAt()).isEqualTo(LocalDateTime.parse("2026-10-16T05:00:00.123"));
		assertThat(node.getFirmwareVersion()).isEqualTo("1.2.3");
		assertThat(node.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
		assertThat(node.getAppliedConfigVersion()).isEqualTo(3L);
		assertThat(node.getAppliedPinVersion()).isEqualTo(2L);
	}

	@Test
	void disabledNodeMayBeOnlineWithoutRegainingPermission() {
		node.setLifecycleStatus(Device.LifecycleStatus.DISABLED);
		var result = service.recordHeartbeat(identity, null);
		assertThat(result.connectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
		assertThat(result.lifecycleStatus()).isEqualTo(Device.LifecycleStatus.DISABLED);
	}

	@Test
	void rejectsRetiredNodeWithoutChangingLastSeen() {
		node.setLifecycleStatus(Device.LifecycleStatus.RETIRED);
		assertThatThrownBy(() -> service.recordHeartbeat(identity, "1.0"))
				.isInstanceOf(ConflictException.class);
		assertThat(node.getLastHeartbeatAt()).isNull();
		verify(devices, never()).save(any());
	}

	@Test
	void rejectsIdentityMappedToDifferentDoorOrRole() {
		node.setDoorId(20L);
		assertThatThrownBy(() -> service.recordHeartbeat(identity, null))
				.isInstanceOf(ResourceNotFoundException.class);
		node.setDoorId(10L);
		node.setNodeType(Device.NodeType.NODE_2);
		assertThatThrownBy(() -> service.recordHeartbeat(identity, null))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(devices, never()).save(any());
	}

	@Test
	void rejectsMissingIdentityOrOversizedFirmware() {
		assertThatThrownBy(() -> service.recordHeartbeat(null, null)).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.recordHeartbeat(identity, "x".repeat(51)))
				.isInstanceOf(BadRequestException.class);
		verify(doors, never()).findByIdForUpdate(any());
		verify(devices, never()).save(any());
	}

	@Test
	void repeatHeartbeatDoesNotActivateNodeOrInventAnAck() {
		service.recordHeartbeat(identity, "1.0");
		service.recordHeartbeat(identity, "1.0");
		assertThat(node.getAppliedConfigVersion()).isNull();
		assertThat(node.getAppliedPinVersion()).isNull();
		assertThat(node.getActivatedAt()).isNull();
		assertThat(node.getLifecycleStatus()).isEqualTo(Device.LifecycleStatus.PROVISIONED);
	}
}
