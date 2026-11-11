package com.smartdoor.backend.alert.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "owner_notifications", uniqueConstraints = @UniqueConstraint(name = "uk_notification_alert_owner", columnNames = {"alert_id", "account_id"}))
@Getter @Setter @NoArgsConstructor
public class OwnerNotification {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "bigint unsigned")
	private Long id;
	@Column(name = "account_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long accountId;
	@Column(name = "alert_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long alertId;
	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;
	@Column(nullable = false, length = 120)
	private String title;
	@Column(nullable = false, length = 500)
	private String body;
	@Column(name = "created_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime createdAt;
	@Column(name = "read_at", columnDefinition = "datetime(3)")
	private LocalDateTime readAt;
}
