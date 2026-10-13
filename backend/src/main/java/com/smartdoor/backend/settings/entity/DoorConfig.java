package com.smartdoor.backend.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng door_configs; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "door_configs")
@Getter
@Setter
@NoArgsConstructor
public class DoorConfig {

	@Id
	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "max_failed_attempts", nullable = false, columnDefinition = "int unsigned")
	private Integer maxFailedAttempts = 3;

	@Column(name = "lockout_seconds", nullable = false, columnDefinition = "int unsigned")
	private Integer lockoutSeconds = 60;

	@Column(name = "temperature_threshold", nullable = false, columnDefinition = "decimal(5,2)", precision = 5, scale = 2)
	private BigDecimal temperatureThreshold = new BigDecimal("50.00");

	@Column(name = "open_timeout_seconds", nullable = true, columnDefinition = "int unsigned")
	private Integer openTimeoutSeconds;

	@Column(name = "version", nullable = false, columnDefinition = "bigint unsigned")
	private Long version = 1L;

	@Column(name = "updated_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime updatedAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void updateTimestamps() {
		updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
	}
}
