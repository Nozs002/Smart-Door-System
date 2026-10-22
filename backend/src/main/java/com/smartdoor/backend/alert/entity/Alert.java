package com.smartdoor.backend.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng alerts; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "source_device_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long sourceDeviceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "ENUM('TOO_MANY_FAILURES','HIGH_TEMPERATURE','DOOR_OPEN_TOO_LONG')")
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(name = "severity", nullable = false, columnDefinition = "ENUM('WARNING','CRITICAL')")
	private Severity severity;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','ACKNOWLEDGED','RESOLVED')")
	private Status status = Status.ACTIVE;

	@Column(name = "observed_temperature", nullable = true, columnDefinition = "decimal(5,2)", precision = 5, scale = 2)
	private BigDecimal observedTemperature;

	@Column(name = "triggered_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime triggeredAt;

	@Column(name = "resolved_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime resolvedAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (triggeredAt == null) {
			triggeredAt = now;
		}
	}

	public enum Type {
		TOO_MANY_FAILURES, HIGH_TEMPERATURE, DOOR_OPEN_TOO_LONG
	}

	public enum Severity {
		WARNING, CRITICAL
	}

	public enum Status {
		ACTIVE, ACKNOWLEDGED, RESOLVED
	}
}
