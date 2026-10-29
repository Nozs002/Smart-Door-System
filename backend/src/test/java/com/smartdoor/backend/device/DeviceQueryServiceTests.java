package com.smartdoor.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdoor.backend.device.dto.DeviceView.ConnectionStatus;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import com.smartdoor.backend.device.service.DeviceQueryService;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DeviceQueryServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
	private final DoorOwnershipService ownership = mock(DoorOwnershipService.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DeviceLifecycleEventRepository events = mock(DeviceLifecycleEventRepository.class);
	private final DeviceQueryService service = new DeviceQueryService(ownership, devices, events,
			new DeviceConnectionPolicy(CLOCK, 90));

	@Test
	void listsBothRolesAndTheirIndependentConnectivity() {
		Device first = device(11L, Device.NodeType.NODE_1);
		first.setLastHeartbeatAt(LocalDateTime.parse("2026-10-16T04:58:29"));
		Device second = device(12L, Device.NodeType.NODE_2);
		second.setLastHeartbeatAt(LocalDateTime.parse("2026-10-16T05:00:00"));
		when(devices.findAllByDoorIdOrderByNodeTypeAscRegisteredAtDescIdDesc(10L))
				.thenReturn(List.of(first, second));
		assertThat(service.listDevices(1L, 10L)).extracting(view -> view.connectionStatus())
				.containsExactly(ConnectionStatus.OFFLINE, ConnectionStatus.ONLINE);
		verify(ownership).requireOwner(1L, 10L);
	}

	@Test
	void ownerCannotInspectDeviceOfDifferentDoor() {
		assertThatThrownBy(() -> service.getDevice(1L, 10L, 99L))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void checksOwnershipBeforeReturningDevicesOrHistory() {
		doThrow(new ResourceNotFoundException("Door not found")).when(ownership).requireOwner(2L, 10L);
		assertThatThrownBy(() -> service.listDevices(2L, 10L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.history(2L, 10L, null, 0, 20))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(devices, never()).findAllByDoorIdOrderByNodeTypeAscRegisteredAtDescIdDesc(any());
		verify(events, never()).findAllByDoorIdOrderByOccurredAtDescIdDesc(any(), any());
	}

	@Test
	void historyFiltersToValidatedDeviceAndPreservesPaginationMetadata() {
		when(devices.findByIdAndDoorId(11L, 10L)).thenReturn(Optional.of(device(11L, Device.NodeType.NODE_1)));
		DeviceLifecycleEvent event = new DeviceLifecycleEvent();
		event.setId(100L);
		event.setDoorId(10L);
		event.setDeviceId(11L);
		when(events.findAllByDeviceIdAndDoorIdOrderByOccurredAtDescIdDesc(eq(11L), eq(10L), any()))
				.thenReturn(new PageImpl<>(List.of(event), PageRequest.of(1, 20), 21));
		var page = service.history(1L, 10L, 11L, 1, 20);
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalElements()).isEqualTo(21);
		assertThat(page.totalPages()).isEqualTo(2);
		assertThat(page.items()).extracting(view -> view.deviceId()).containsExactly(11L);
		verify(events, never()).findAllByDoorIdOrderByOccurredAtDescIdDesc(any(), any());
	}

	@Test
	void deviceHistoryDoesNotExposeEventsAcrossDoors() {
		assertThatThrownBy(() -> service.history(1L, 10L, 99L, 0, 20))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(events, never()).findAllByDeviceIdAndDoorIdOrderByOccurredAtDescIdDesc(any(), any(), any());
	}

	@Test
	void rejectsUnboundedOrNegativePagination() {
		assertThatThrownBy(() -> service.history(1L, 10L, null, -1, 20))
				.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service.history(1L, 10L, null, 0, 101))
				.isInstanceOf(BadRequestException.class);
	}

	private Device device(Long id, Device.NodeType role) {
		Device value = new Device();
		value.setId(id);
		value.setDoorId(10L);
		value.setNodeType(role);
		return value;
	}
}
