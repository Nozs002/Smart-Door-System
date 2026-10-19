package com.smartdoor.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdoor.backend.device.dto.LifecycleRequest.Action;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.Device.LifecycleStatus;
import com.smartdoor.backend.device.entity.Device.NodeType;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent.EventType;
import com.smartdoor.backend.device.repository.DeviceLifecycleEventRepository;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.device.service.DeviceConnectionPolicy;
import com.smartdoor.backend.device.service.DeviceLifecycleService;
import com.smartdoor.backend.device.service.DeviceReadinessPolicy;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DeviceLifecycleServiceTests {
	private static final Long ACCOUNT = 1L;
	private static final Long DOOR_ID = 10L;
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
	private final DoorOwnershipService ownership = mock(DoorOwnershipService.class);
	private final DoorRepository doors = mock(DoorRepository.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DeviceLifecycleEventRepository events = mock(DeviceLifecycleEventRepository.class);
	private final DeviceReadinessPolicy readiness = mock(DeviceReadinessPolicy.class);
	private final DeviceLifecycleService service = new DeviceLifecycleService(ownership, doors, devices,
			events, readiness, new DeviceConnectionPolicy(CLOCK, 90), CLOCK);
	private Door door;
	private Device node;

	@BeforeEach
	void setUp() {
		door = new Door();
		door.setId(DOOR_ID);
		door.setOwnerAccountId(ACCOUNT);
		door.setStatus(Door.Status.SETUP_PENDING);
		node = device(11L, NodeType.NODE_1, LifecycleStatus.PROVISIONED);
		when(ownership.requireOwnerForUpdate(ACCOUNT, DOOR_ID)).thenReturn(door);
		when(doors.findByIdForUpdate(DOOR_ID)).thenReturn(Optional.of(door));
		when(devices.findByIdAndDoorIdForUpdate(node.getId(), DOOR_ID)).thenReturn(Optional.of(node));
		when(devices.findAllByDoorId(DOOR_ID)).thenReturn(List.of(node));
		when(devices.saveAndFlush(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void activationFailsClosedWithoutReadinessEvidenceAndDoesNotWrite() {
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null))
				.isInstanceOf(ConflictException.class);
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.PROVISIONED);
		verify(devices, never()).saveAndFlush(any());
		verify(events, never()).save(any());
	}

	@Test
	void activatesWithEvidenceAndCapturesActorAndServerTimeInHistory() {
		when(readiness.canActivate(node)).thenReturn(true);
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, "Ready");
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		assertThat(node.getActivatedAt()).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
		assertThat(door.getStatus()).isEqualTo(Door.Status.SETUP_PENDING);
		ArgumentCaptor<DeviceLifecycleEvent> capture = ArgumentCaptor.forClass(DeviceLifecycleEvent.class);
		verify(events).save(capture.capture());
		DeviceLifecycleEvent event = capture.getValue();
		assertThat(event.getActorAccountId()).isEqualTo(ACCOUNT);
		assertThat(event.getDeviceId()).isEqualTo(node.getId());
		assertThat(event.getDoorId()).isEqualTo(DOOR_ID);
		assertThat(event.getEventType()).isEqualTo(EventType.ACTIVATED);
		assertThat(event.getFromStatus()).isEqualTo(LifecycleStatus.PROVISIONED);
		assertThat(event.getToStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		assertThat(event.getEventId()).isNotBlank();
		assertThat(event.getOccurredAt()).isEqualTo(node.getActivatedAt());
		InOrder order = inOrder(ownership, devices, events);
		order.verify(ownership).requireOwnerForUpdate(ACCOUNT, DOOR_ID);
		order.verify(devices).findByIdAndDoorIdForUpdate(node.getId(), DOOR_ID);
		order.verify(devices).findAllByDoorIdAndNodeTypeAndLifecycleStatus(DOOR_ID, NodeType.NODE_1,
				LifecycleStatus.ACTIVE);
		order.verify(devices).saveAndFlush(node);
		order.verify(events).save(event);
	}

	@Test
	void doorBecomesActiveOnlyWhenBothDistinctRolesAreActiveAndReady() {
		Device sibling = device(12L, NodeType.NODE_2, LifecycleStatus.ACTIVE);
		when(readiness.canActivate(node)).thenReturn(true);
		when(readiness.canActivate(sibling)).thenReturn(true);
		when(devices.findAllByDoorId(DOOR_ID)).thenReturn(List.of(node, sibling));
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null);
		assertThat(door.getStatus()).isEqualTo(Door.Status.ACTIVE);
	}

	@Test
	void doorStaysPendingWhenSiblingConfigurationIsNotReady() {
		Device sibling = device(12L, NodeType.NODE_2, LifecycleStatus.ACTIVE);
		when(readiness.canActivate(node)).thenReturn(true);
		when(devices.findAllByDoorId(DOOR_ID)).thenReturn(List.of(node, sibling));
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null);
		assertThat(door.getStatus()).isEqualTo(Door.Status.SETUP_PENDING);
	}

	@Test
	void refusesOccupiedSlotEvenWithReadinessEvidence() {
		when(readiness.canActivate(node)).thenReturn(true);
		when(devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(DOOR_ID, NodeType.NODE_1,
				LifecycleStatus.ACTIVE)).thenReturn(List.of(device(99L, NodeType.NODE_1, LifecycleStatus.ACTIVE)));
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null))
				.isInstanceOf(ConflictException.class);
		verify(events, never()).save(any());
	}

	@Test
	void disableDoesNotNeedReadinessAndReturnsDoorToSetupPending() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		door.setStatus(Door.Status.ACTIVE);
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.DISABLE, "Maintenance");
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.DISABLED);
		assertThat(door.getStatus()).isEqualTo(Door.Status.SETUP_PENDING);
	}

	@Test
	void reactivationRequiresEvidenceAndPreservesFirstActivationTimestamp() {
		node.setLifecycleStatus(LifecycleStatus.DISABLED);
		node.setActivatedAt(LocalDateTime.parse("2026-10-15T01:00:00"));
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.REACTIVATE, null))
				.isInstanceOf(ConflictException.class);
		when(readiness.canActivate(node)).thenReturn(true);
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.REACTIVATE, null);
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		assertThat(node.getActivatedAt()).isEqualTo(LocalDateTime.parse("2026-10-15T01:00:00"));
	}

	@Test
	void rejectsActivationOfDisabledDoor() {
		door.setStatus(Door.Status.DISABLED);
		when(readiness.canActivate(node)).thenReturn(true);
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void retireRequiresReasonAndCannotBeReversed() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.RETIRE, " "))
				.isInstanceOf(BadRequestException.class);
		service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.RETIRE, "Broken hardware");
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.RETIRED);
		assertThat(node.getRetiredAt()).isEqualTo(LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
		for (Action action : Action.values()) {
			assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), action, "Retry"))
					.isInstanceOf(ConflictException.class);
		}
	}

	@Test
	void provisionedDeviceCannotBeDisabledOrRetired() {
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.DISABLE, null))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.RETIRE, "Unused"))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void inaccessibleDoorCannotReadOrMutateDevices() {
		when(ownership.requireOwnerForUpdate(ACCOUNT, DOOR_ID)).thenThrow(new ResourceNotFoundException("Door not found"));
		assertThatThrownBy(() -> service.changeLifecycle(ACCOUNT, DOOR_ID, node.getId(), Action.ACTIVATE, null))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(devices, never()).findByIdAndDoorIdForUpdate(any(), any());
		verify(events, never()).save(any());
	}

	@Test
	void replacementCannotRetireOldDeviceBeforeNewDeviceIsReady() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		Device replacement = device(13L, NodeType.NODE_1, LifecycleStatus.PROVISIONED);
		when(devices.findByIdAndDoorIdForUpdate(13L, DOOR_ID)).thenReturn(Optional.of(replacement));
		assertThatThrownBy(() -> service.replaceDevice(ACCOUNT, DOOR_ID, node.getId(), 13L, "Replace broken node"))
				.isInstanceOf(ConflictException.class);
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		verify(devices, never()).saveAndFlush(any());
		verify(events, never()).save(any());
	}

	@Test
	void replacementFlushesOldSlotBeforeNewSlotAndKeepsLinkedHistory() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		Device replacement = device(13L, NodeType.NODE_1, LifecycleStatus.PROVISIONED);
		when(devices.findByIdAndDoorIdForUpdate(13L, DOOR_ID)).thenReturn(Optional.of(replacement));
		when(readiness.canActivate(replacement)).thenReturn(true);
		when(readiness.canReplace(node, replacement)).thenReturn(true);
		when(devices.findAllByDoorIdAndNodeTypeAndLifecycleStatus(DOOR_ID, NodeType.NODE_1,
				LifecycleStatus.ACTIVE)).thenReturn(List.of(node));
		service.replaceDevice(ACCOUNT, DOOR_ID, node.getId(), 13L, "Replacement");
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.RETIRED);
		assertThat(replacement.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		InOrder order = inOrder(devices, events);
		order.verify(devices).saveAndFlush(node);
		order.verify(devices).saveAndFlush(replacement);
		ArgumentCaptor<DeviceLifecycleEvent> capture = ArgumentCaptor.forClass(DeviceLifecycleEvent.class);
		verify(events, org.mockito.Mockito.times(2)).save(capture.capture());
		assertThat(capture.getAllValues()).extracting(DeviceLifecycleEvent::getEventType)
				.containsExactly(EventType.REPLACED, EventType.ACTIVATED);
		assertThat(capture.getAllValues().getFirst().getReplacementDeviceId()).isEqualTo(13L);
		assertThat(capture.getAllValues().getFirst().getFromStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		assertThat(capture.getAllValues().getFirst().getToStatus()).isEqualTo(LifecycleStatus.RETIRED);
	}

	@Test
	void replacementRequiresConfirmedRevocationEvenWhenNewNodeIsReady() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		Device replacement = device(13L, NodeType.NODE_1, LifecycleStatus.PROVISIONED);
		when(devices.findByIdAndDoorIdForUpdate(13L, DOOR_ID)).thenReturn(Optional.of(replacement));
		when(readiness.canActivate(replacement)).thenReturn(true);
		assertThatThrownBy(() -> service.replaceDevice(ACCOUNT, DOOR_ID, node.getId(), 13L, "Replacement"))
				.isInstanceOf(ConflictException.class).hasMessageContaining("thu hồi");
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		assertThat(replacement.getLifecycleStatus()).isEqualTo(LifecycleStatus.PROVISIONED);
		verify(events, never()).save(any());
		verify(devices, never()).saveAndFlush(any());
	}

	@Test
	void refusesCrossRoleAndCrossDoorReplacement() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		Device wrongRole = device(13L, NodeType.NODE_2, LifecycleStatus.PROVISIONED);
		when(devices.findByIdAndDoorIdForUpdate(13L, DOOR_ID)).thenReturn(Optional.of(wrongRole));
		assertThatThrownBy(() -> service.replaceDevice(ACCOUNT, DOOR_ID, node.getId(), 13L, "Replacement"))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.replaceDevice(ACCOUNT, DOOR_ID, node.getId(), 99L, "Replacement"))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(events, never()).save(any());
	}

	@Test
	void technicalRegistrationCreatesSameRoleWithoutRetiringOrActivatingAnything() {
		node.setLifecycleStatus(LifecycleStatus.ACTIVE);
		when(devices.saveAndFlush(any(Device.class))).thenAnswer(invocation -> {
			Device value = invocation.getArgument(0);
			value.setId(13L);
			return value;
		});
		var view = service.registerReplacement(DOOR_ID, node.getId(), "new-node", "Replace failed node");
		assertThat(view.nodeType()).isEqualTo(NodeType.NODE_1);
		assertThat(view.lifecycleStatus()).isEqualTo(LifecycleStatus.PROVISIONED);
		assertThat(node.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
		ArgumentCaptor<DeviceLifecycleEvent> capture = ArgumentCaptor.forClass(DeviceLifecycleEvent.class);
		verify(events).save(capture.capture());
		assertThat(capture.getValue().getActorAccountId()).isNull();
		assertThat(capture.getValue().getEventType()).isEqualTo(EventType.PROVISIONED);
		assertThat(capture.getValue().getDeviceId()).isEqualTo(13L);
	}

	@Test
	void technicalRegistrationRejectsDuplicateIdentity() {
		node.setLifecycleStatus(LifecycleStatus.DISABLED);
		when(devices.existsByDeviceCode("existing-node")).thenReturn(true);
		assertThatThrownBy(() -> service.registerReplacement(DOOR_ID, node.getId(), "existing-node", "Replacement"))
				.isInstanceOf(ConflictException.class);
		verify(devices, never()).saveAndFlush(any());
	}

	private Device device(Long id, NodeType role, LifecycleStatus status) {
		Device value = new Device();
		value.setId(id);
		value.setDoorId(DOOR_ID);
		value.setDeviceCode("node-" + id);
		value.setNodeType(role);
		value.setLifecycleStatus(status);
		return value;
	}
}
