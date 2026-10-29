package com.smartdoor.backend.alert.notification;

import com.smartdoor.backend.alert.dto.AlertView;
import com.smartdoor.backend.door.entity.Door;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.shared.api.PageResponse;
import com.smartdoor.backend.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable in-app inbox. This does not claim delivery through Android/FCM background push. */
@Service
public class NotificationService {
	private final NotificationRepository notifications;
	private final DoorRepository doors;
	private final Clock clock;
	public NotificationService(NotificationRepository notifications, DoorRepository doors, Clock clock) {
		this.notifications = notifications; this.doors = doors; this.clock = clock;
	}

	@Transactional
	public NotificationView notifyOwner(Long accountId, AlertView alert) {
		Door door = doors.findByIdForUpdate(alert.doorId()).orElseThrow(this::notFound);
		if (!Objects.equals(door.getOwnerAccountId(), accountId)) throw notFound();
		OwnerNotification existing = notifications.findByAlertIdAndAccountId(alert.id(), accountId).orElse(null);
		if (existing != null) return NotificationView.from(existing);
		OwnerNotification notification = new OwnerNotification();
		notification.setAccountId(accountId); notification.setAlertId(alert.id()); notification.setDoorId(alert.doorId());
		notification.setTitle(switch (alert.type()) {
			case HIGH_TEMPERATURE -> "Cảnh báo nhiệt độ";
			case TOO_MANY_FAILURES -> "Xác thực sai nhiều lần";
			case DOOR_OPEN_TOO_LONG -> "Cửa mở quá lâu";
		});
		notification.setBody(door.getName() + ": " + notification.getTitle()
				+ (alert.observedTemperature() == null ? "." : " (" + alert.observedTemperature().toPlainString() + "°C)."));
		notification.setCreatedAt(now());
		return NotificationView.from(notifications.saveAndFlush(notification));
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationView> list(Long accountId, boolean unread, int page, int size) {
		var pageable = PageResponse.pageable(page, size);
		return PageResponse.from((unread ? notifications.findAllByAccountIdAndReadAtIsNull(accountId, pageable)
				: notifications.findAllByAccountId(accountId, pageable)).map(NotificationView::from));
	}

	@Transactional
	public NotificationView markRead(Long accountId, Long notificationId) {
		OwnerNotification notification = notifications.lockOwned(notificationId, accountId).orElseThrow(this::notFound);
		if (notification.getReadAt() == null) notification.setReadAt(now());
		return NotificationView.from(notification);
	}

	private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
	private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Không tìm thấy thông báo."); }
}
