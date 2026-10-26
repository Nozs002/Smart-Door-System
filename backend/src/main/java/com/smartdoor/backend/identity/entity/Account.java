package com.smartdoor.backend.identity.entity;

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

/** Mapping bảng accounts; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "accounts", uniqueConstraints = {
		@UniqueConstraint(name = "uk_accounts_username", columnNames = {"username"})
})
@Getter
@Setter
@NoArgsConstructor
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "display_name", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String displayName;

	@Column(name = "username", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String username;

	@Column(name = "password_hash", nullable = false, columnDefinition = "varchar(255)", length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','DISABLED')")
	private Status status = Status.ACTIVE;

	@Column(name = "created_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime createdAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (createdAt == null) {
			createdAt = now;
		}
	}

	public enum Status {
		ACTIVE, DISABLED
	}
}
