package com.smartdoor.backend.credentials.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng system_pins; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "system_pins")
@Getter
@Setter
@NoArgsConstructor
public class SystemPin {

	@Id
	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "pin_hash", nullable = false, columnDefinition = "varchar(255)", length = 255)
	private String pinHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','DISABLED')")
	private Status status = Status.ACTIVE;

	@Column(name = "version", nullable = false, columnDefinition = "bigint unsigned")
	private Long version = 1L;

	@Column(name = "changed_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime changedAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (changedAt == null) {
			changedAt = now;
		}
	}

	public enum Status {
		ACTIVE, DISABLED
	}
}
