package com.smartdoor.backend.device.entity;

import com.smartdoor.backend.device.entity.Device.LifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng device_lifecycle_events; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "device_lifecycle_events", uniqueConstraints = {
		@UniqueConstraint(name = "uk_lifecycle_event", columnNames = {"event_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class DeviceLifecycleEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "event_id", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String eventId;

	@Column(name = "device_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long deviceId;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "actor_account_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long actorAccountId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, columnDefinition = "ENUM('PROVISIONED','ACTIVATED','DISABLED','REACTIVATED','RETIRED','REPLACED')")
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", nullable = true, columnDefinition = "ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED')")
	private LifecycleStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, columnDefinition = "ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED')")
	private LifecycleStatus toStatus;

	@Column(name = "replacement_device_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long replacementDeviceId;

	@Column(name = "reason", nullable = true, columnDefinition = "varchar(500)", length = 500)
	private String reason;

	@Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime occurredAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (occurredAt == null) {
			occurredAt = now;
		}
	}

	public enum EventType {
		PROVISIONED, ACTIVATED, DISABLED, REACTIVATED, RETIRED, REPLACED
	}
}
