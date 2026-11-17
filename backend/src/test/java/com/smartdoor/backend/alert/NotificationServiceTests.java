package com.smartdoor.backend.alert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartdoor.backend.alert.notification.*;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationServiceTests {
	private final NotificationRepository notifications = mock(NotificationRepository.class);
	private final DoorRepository doors = mock(DoorRepository.class);
	private final NotificationService service = new NotificationService(notifications, doors,
			Clock.fixed(Instant.parse("2026-10-28T01:02:03Z"), ZoneOffset.UTC));

	@Test void markingReadIsIdempotentAndDoesNotAffectAnotherAccountsInbox() {
		OwnerNotification notification = new OwnerNotification(); notification.setId(4L); notification.setAccountId(7L);
		when(notifications.lockOwned(4L, 7L)).thenReturn(Optional.of(notification));
		var first = service.markRead(7L, 4L);
		var again = service.markRead(7L, 4L);
		assertThat(first.readAt()).isNotNull(); assertThat(again.readAt()).isEqualTo(first.readAt());
		assertThatThrownBy(() -> service.markRead(8L, 4L)).isInstanceOf(ResourceNotFoundException.class);
	}
}
