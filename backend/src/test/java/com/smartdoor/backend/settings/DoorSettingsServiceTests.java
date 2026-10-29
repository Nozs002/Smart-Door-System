package com.smartdoor.backend.settings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.infrastructure.outbox.OutboxService;
import com.smartdoor.backend.settings.dto.DeviceSyncView;
import com.smartdoor.backend.settings.dto.UpdateDoorConfigRequest;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher;
import com.smartdoor.backend.settings.service.DeviceCommandPublisher.ChangeReason;
import com.smartdoor.backend.settings.service.DoorSettingsService;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoorSettingsServiceTests {
	private final DoorConfigRepository configs = mock(DoorConfigRepository.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DoorOwnershipService ownership = mock(DoorOwnershipService.class);
	private final DeviceCommandPublisher publisher = mock(DeviceCommandPublisher.class);
	private final OutboxService outbox = mock(OutboxService.class);
	private final DoorSettingsService service = new DoorSettingsService(configs, devices, ownership, publisher, outbox);
	private DoorConfig config;
	private List<Device> nodes;

	@BeforeEach void setup() {
		config = new DoorConfig(); config.setDoorId(3L);
		nodes = List.of(node(1L, Device.NodeType.NODE_1, Device.LifecycleStatus.PROVISIONED),
				node(2L, Device.NodeType.NODE_2, Device.LifecycleStatus.ACTIVE),
				node(3L, Device.NodeType.NODE_1, Device.LifecycleStatus.DISABLED),
				node(4L, Device.NodeType.NODE_1, Device.LifecycleStatus.RETIRED));
		when(configs.findById(3L)).thenReturn(Optional.of(config));
		when(configs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(devices.findAllByDoorId(3L)).thenReturn(nodes);
		when(devices.findAllByDoorIdOrderByNodeTypeAscRegisteredAtDescIdDesc(3L)).thenReturn(nodes);
	}

	@Test void updatingConfigurationQueuesSetupActiveAndDisabledButNeverRetired() {
		var view = service.update(7L, 3L, new UpdateDoorConfigRequest(4, 120, new BigDecimal("52.00"), 30));
		assertThat(view.version()).isEqualTo(2);
		assertThat(config.getMaxFailedAttempts()).isEqualTo(4);
		var commands = org.mockito.ArgumentCaptor.forClass(DeviceCommandPublisher.ConfigurationCommand.class);
		verify(publisher, times(3)).publishConfiguration(commands.capture());
		assertThat(commands.getAllValues()).extracting(DeviceCommandPublisher.ConfigurationCommand::deviceCode)
				.containsExactly("NODE-1", "NODE-2", "NODE-3");
		verify(ownership).requireOwnerForUpdate(7L, 3L);
	}

	@Test void retryDoesNotIncreaseDesiredVersions() {
		config.setVersion(9L); config.setCredentialsVersion(12L);
		var view = service.retrySync(7L, 3L);
		assertThat(view.version()).isEqualTo(9); assertThat(view.credentialsVersion()).isEqualTo(12);
		verify(outbox).retry(7L, 3L);
		verify(publisher, times(3)).publishConfiguration(any());
		verify(publisher, times(2)).publishCredentials(any());
		verify(configs, never()).saveAndFlush(any());
	}

	@Test void credentialChangeIncreasesOneCommonVersionAndOnlyTargetsNodeOne() {
		service.requestCredentialsSync(7L, 3L, ChangeReason.RFID_REVOKED);
		assertThat(config.getCredentialsVersion()).isEqualTo(2);
		verify(publisher, times(2)).publishCredentials(any());
		verify(publisher, never()).publishConfiguration(any());
	}

	@Test void anotherOwnerCannotReadOrUpdateConfiguration() {
		doThrow(new ResourceNotFoundException("Không tìm thấy cửa.")).when(ownership).requireOwner(8L, 3L);
		assertThatThrownBy(() -> service.get(8L, 3L)).isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(publisher);
	}

	@Test void unknownOrFutureVersionCannotBePresentedAsApplied() {
		nodes.getFirst().setAppliedConfigVersion(99L);
		var view = service.get(7L, 3L);
		assertThat(view.devices().getFirst().configState()).isEqualTo(DeviceSyncView.SyncState.PENDING);
		assertThat(view.devices().get(1).configState()).isEqualTo(DeviceSyncView.SyncState.UNKNOWN);
	}

	private Device node(Long id, Device.NodeType role, Device.LifecycleStatus status) {
		Device node = new Device(); node.setId(id); node.setDoorId(3L); node.setDeviceCode("NODE-" + id);
		node.setNodeType(role); node.setLifecycleStatus(status); return node;
	}
}
