package com.smartdoor.backend.alert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.alert.entity.Alert;
import com.smartdoor.backend.alert.repository.AlertRepository;
import com.smartdoor.backend.alert.service.HardwareAlertEvent;
import com.smartdoor.backend.alert.service.HardwareAlertService;
import com.smartdoor.backend.alert.service.OwnerAlertNotifier;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.device.service.DeviceOperationalService;
import com.smartdoor.backend.device.service.DeviceOperationalService.OperationalDevice;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.settings.entity.DoorConfig;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import com.smartdoor.backend.shared.error.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HardwareAlertServiceTests {

	private final DeviceOperationalService operational = mock(DeviceOperationalService.class);
	private final AlertRepository alerts = mock(AlertRepository.class);
	private final DoorRepository doors = mock(DoorRepository.class);
	private final DoorConfigRepository configs = mock(DoorConfigRepository.class);
	private final OwnerAlertNotifier notifier = mock(OwnerAlertNotifier.class);
	private final HardwareAlertService service = new HardwareAlertService(operational, alerts, doors, configs, notifier);
	private final AuthenticatedDeviceIdentity identity = new AuthenticatedDeviceIdentity("NODE2", 5L, Device.NodeType.NODE_2);
	private final LocalDateTime occurred = LocalDateTime.of(2026, 10, 28, 1, 2, 3, 123_000_000);
	private DoorConfig config;

	@BeforeEach void setup() {
		Door door = new Door(); door.setId(5L); door.setName("Cửa trước"); door.setOwnerAccountId(7L);
		config = new DoorConfig(); config.setDoorId(5L);
		when(doors.findByIdForUpdate(5L)).thenReturn(Optional.of(door));
		when(operational.requireActive(identity)).thenReturn(new OperationalDevice(9L, 5L, "NODE2", Device.NodeType.NODE_2));
		when(configs.findById(5L)).thenReturn(Optional.of(config));
		when(alerts.saveAndFlush(any())).thenAnswer(invocation -> {
			Alert alert = invocation.getArgument(0); alert.setId(11L); return alert;
		});
	}

	@Test void replayIsNormalizedAndAcceptedBeforeRecheckingChangedTemperatureThreshold() {
		Alert existing = stored("event-1", "55.50");
		when(alerts.findByEventId("event-1")).thenReturn(Optional.of(existing));
		config.setTemperatureThreshold(new BigDecimal("70.00"));
		var view = service.receive(identity, new HardwareAlertEvent(" event-1 ", Alert.Type.HIGH_TEMPERATURE,
				new BigDecimal("55.5"), occurred.plusNanos(999_999)));
		assertThat(view.id()).isEqualTo(11L);
		verify(configs, never()).findById(any());
		verify(alerts, never()).saveAndFlush(any());
		verifyNoInteractions(notifier);
	}

	@Test void replayCannotReuseEventIdForDifferentSourceOrPayload() {
		Alert existing = stored("event-1", "55.50"); existing.setSourceDeviceId(99L);
		when(alerts.findByEventId("event-1")).thenReturn(Optional.of(existing));
		assertThatThrownBy(() -> service.receive(identity, event("event-1", "55.50")))
				.isInstanceOf(ConflictException.class);
		existing.setSourceDeviceId(9L);
		assertThatThrownBy(() -> service.receive(identity, event("event-1", "56.00")))
				.isInstanceOf(ConflictException.class);
		verifyNoInteractions(notifier);
	}

	@Test void newAlertMustExceedThresholdAndNotifiesItsOwnerOnce() {
		assertThatThrownBy(() -> service.receive(identity, event("not-hot", "50.00")))
				.isInstanceOf(BadRequestException.class);
		var view = service.receive(identity, event("hot", "55.505"));
		assertThat(view.observedTemperature()).isEqualByComparingTo("55.51");
		verify(notifier).notifyOwner(eq(7L), eq(view));
		verify(doors, times(2)).findByIdForUpdate(5L);
	}

	@Test void roleAndEventShapeAreCheckedEvenForDuplicateIds() {
		assertThatThrownBy(() -> service.receive(identity,
				new HardwareAlertEvent("wrong-role", Alert.Type.TOO_MANY_FAILURES, null, occurred)))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> service.receive(identity,
				new HardwareAlertEvent("wrong-shape", Alert.Type.DOOR_OPEN_TOO_LONG, BigDecimal.ONE, occurred)))
				.isInstanceOf(BadRequestException.class);
		verify(alerts, never()).findByEventId(any());
	}

	@Test void notifierFailurePropagatesSoTransactionCanRollBack() {
		doThrow(new IllegalStateException("inbox unavailable")).when(notifier).notifyOwner(anyLong(), any());
		assertThatThrownBy(() -> service.receive(identity, event("hot", "55.00")))
				.isInstanceOf(IllegalStateException.class);
	}

	private HardwareAlertEvent event(String id, String temperature) {
		return new HardwareAlertEvent(id, Alert.Type.HIGH_TEMPERATURE, new BigDecimal(temperature), occurred);
	}
	private Alert stored(String id, String temperature) {
		Alert alert = new Alert(); alert.setId(11L); alert.setEventId(id); alert.setDoorId(5L);
		alert.setSourceDeviceId(9L); alert.setType(Alert.Type.HIGH_TEMPERATURE);
		alert.setObservedTemperature(new BigDecimal(temperature)); alert.setTriggeredAt(occurred);
		alert.setSeverity(Alert.Severity.CRITICAL); return alert;
	}
}
