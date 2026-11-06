package com.smartdoor.backend.infrastructure.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.service.DoorOwnershipService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxServiceTests {
	private final OutboxRepository messages = mock(OutboxRepository.class);
	private final DeviceRepository devices = mock(DeviceRepository.class);
	private final DoorOwnershipService ownership = mock(DoorOwnershipService.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T01:02:03Z"), ZoneOffset.UTC);
	private final OutboxService service = new OutboxService(messages, devices, ownership, clock, 2);

	@Test void claimedDeliveryCanRetryThenBecomeFailedWithoutLeakingExceptionPayload() {
		OutboxMessage row = row();
		when(messages.ready(any(), any(), any(), any(), any())).thenReturn(List.of(row));
		when(messages.lock(1L)).thenReturn(Optional.of(row));
		var first = service.claim(List.of(OutboxService.CREDENTIALS), 10).getFirst();
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.PROCESSING);
		assertThat(row.getAttempts()).isEqualTo(1);
		service.failed(first, new IOException("sensitive credential value"));
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.PENDING);
		assertThat(row.getLastError()).doesNotContain("sensitive");
		assertThat(row.getNextAttemptAt()).isAfter(row.getCreatedAt());
		var second = service.claim(List.of(OutboxService.CREDENTIALS), 10).getFirst();
		service.failed(second, new IOException());
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.FAILED);
		assertThat(row.getLeaseToken()).isNull(); assertThat(row.getLeaseUntil()).isNull();
	}

	@Test void expiredWorkerCannotOverwriteNewWorkersLease() {
		OutboxMessage row = row();
		when(messages.ready(any(), any(), any(), any(), any())).thenReturn(List.of(row));
		when(messages.lock(1L)).thenReturn(Optional.of(row));
		var old = service.claim(List.of(OutboxService.CONFIGURATION), 1).getFirst();
		var current = service.claim(List.of(OutboxService.CONFIGURATION), 1).getFirst();
		service.delivered(old);
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.PROCESSING);
		service.failed(old, new IOException());
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.PROCESSING);
		service.delivered(current);
		assertThat(row.getStatus()).isEqualTo(OutboxMessage.Status.SENT);
		assertThat(row.getSentAt()).isNotNull();
	}

	@Test void enqueueAvoidsDuplicatingPendingWorkButAllowsExplicitResendOfSentWork() {
		Device node = new Device(); node.setId(9L); node.setDoorId(5L); node.setDeviceCode("NODE1");
		when(devices.findByDeviceCode("NODE1")).thenReturn(Optional.of(node));
		when(messages.existsByDeviceIdAndKindAndPayloadAndStatusIn(any(), any(), any(), any())).thenReturn(true);
		service.enqueue(OutboxService.CREDENTIALS, 5L, "NODE1", "{}");
		verify(messages, never()).save(any());
		when(messages.existsByDeviceIdAndKindAndPayloadAndStatusIn(any(), any(), any(), any())).thenReturn(false);
		service.enqueue(OutboxService.CREDENTIALS, 5L, "NODE1", "{}");
		verify(messages).save(any());
	}

	@Test void absenceOfTransportLeavesMessagesPending() {
		OutboxService outbox = mock(OutboxService.class);
		new OutboxDispatcher(outbox, List.of()).dispatch();
		verifyNoInteractions(outbox);
	}

	@Test void dispatcherRecordsTransportFailureWithoutClaimingItWasDelivered() throws Exception {
		OutboxService outbox = mock(OutboxService.class);
		OutboxTransport transport = mock(OutboxTransport.class);
		when(transport.supports(OutboxService.CONFIGURATION)).thenReturn(true);
		var claim = new OutboxService.Claim(new OutboxDelivery(1L, OutboxService.CONFIGURATION, 5L, "NODE2", "{}"), "lease");
		when(outbox.claim(any(), anyInt())).thenReturn(List.of(claim));
		doThrow(new IOException()).when(transport).deliver(claim.delivery());
		new OutboxDispatcher(outbox, List.of(transport)).dispatch();
		verify(outbox).failed(eq(claim), any(IOException.class));
		verify(outbox, never()).delivered(any());
	}

	private OutboxMessage row() {
		OutboxMessage row = new OutboxMessage(); row.setId(1L); row.setDoorId(5L); row.setDeviceId(9L);
		row.setDeviceCode("NODE1"); row.setKind(OutboxService.CREDENTIALS); row.setPayload("{}");
		row.setCreatedAt(LocalDateTime.now(clock)); row.setNextAttemptAt(row.getCreatedAt()); return row;
	}
}
