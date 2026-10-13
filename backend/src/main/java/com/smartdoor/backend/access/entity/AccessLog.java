package com.smartdoor.backend.access.entity;

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

/** Mapping bảng access_logs; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "access_logs", uniqueConstraints = {
		@UniqueConstraint(name = "uk_access_event", columnNames = {"event_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class AccessLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "event_id", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String eventId;

	@Column(name = "correlation_id", nullable = true, columnDefinition = "varchar(100)", length = 100)
	private String correlationId;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "actor_account_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long actorAccountId;

	@Column(name = "rfid_credential_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long rfidCredentialId;

	@Column(name = "source_device_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long sourceDeviceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, columnDefinition = "ENUM('RFID','PIN','APP','EXIT_BUTTON','AUTO_PROXIMITY','FIRE_EMERGENCY')")
	private Method method;

	@Enumerated(EnumType.STRING)
	@Column(name = "result", nullable = false, columnDefinition = "ENUM('GRANTED','DENIED','EXECUTED','FAILED','UNKNOWN')")
	private Result result;

	@Column(name = "reason", nullable = true, columnDefinition = "varchar(500)", length = 500)
	private String reason;

	@Column(name = "occurred_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime occurredAt;

	@Column(name = "received_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime receivedAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (receivedAt == null) {
			receivedAt = now;
		}
	}

	public enum Method {
		RFID, PIN, APP, EXIT_BUTTON, AUTO_PROXIMITY, FIRE_EMERGENCY
	}

	public enum Result {
		GRANTED, DENIED, EXECUTED, FAILED, UNKNOWN
	}
}
