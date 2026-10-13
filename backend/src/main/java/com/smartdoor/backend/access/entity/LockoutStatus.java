package com.smartdoor.backend.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng lockout_statuses; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "lockout_statuses")
@Getter
@Setter
@NoArgsConstructor
public class LockoutStatus {

	@EmbeddedId
	private LockoutStatusId id;

	@Column(name = "failed_count", nullable = false, columnDefinition = "int unsigned")
	private Integer failedCount = 0;

	@Column(name = "locked_until", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime lockedUntil;

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
