package com.smartdoor.backend.door.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng doors; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "doors", uniqueConstraints = {
		@UniqueConstraint(name = "uk_doors_owner", columnNames = {"id", "owner_account_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Door {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "owner_account_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long ownerAccountId;

	@Column(name = "name", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String name;

	@Column(name = "location", nullable = true, columnDefinition = "varchar(255)", length = 255)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "ENUM('UNCLAIMED','SETUP_PENDING','ACTIVE','DISABLED')")
	private Status status = Status.UNCLAIMED;

	@Enumerated(EnumType.STRING)
	@Column(name = "operating_mode", nullable = false, columnDefinition = "ENUM('AUTOMATIC','SECURITY')")
	private OperatingMode operatingMode = OperatingMode.SECURITY;

	@Enumerated(EnumType.STRING)
	@Column(name = "commanded_lock_state", nullable = true, columnDefinition = "ENUM('LOCKED','UNLOCKED')")
	private LockState commandedLockState;

	@Column(name = "created_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime updatedAt;

	@Version
	@Column(name = "version", nullable = false, columnDefinition = "bigint unsigned")
	private Long version;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void updateTimestamps() {
		updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
	}

	public enum Status {
		UNCLAIMED, SETUP_PENDING, ACTIVE, DISABLED
	}

	public enum OperatingMode {
		AUTOMATIC, SECURITY
	}

	public enum LockState {
		LOCKED, UNLOCKED
	}
}
