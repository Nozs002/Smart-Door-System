package com.smartdoor.backend.alert.notification;

import java.time.LocalDateTime;

public record NotificationView(Long id, Long alertId, Long doorId, String title, String body,
		LocalDateTime createdAt, LocalDateTime readAt) {
	public static NotificationView from(OwnerNotification notification) {
		return new NotificationView(notification.getId(), notification.getAlertId(), notification.getDoorId(),
				notification.getTitle(), notification.getBody(), notification.getCreatedAt(), notification.getReadAt());
	}
}
