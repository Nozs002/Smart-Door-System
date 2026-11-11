package com.smartdoor.backend.credentials.entity;

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

/** Mapping bảng rfid_credentials; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "rfid_credentials", uniqueConstraints = {
		@UniqueConstraint(name = "uk_rfid_door_uid", columnNames = {"door_id", "uid_lookup_value"}),
		@UniqueConstraint(name = "uk_rfid_scope", columnNames = {"id", "door_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class RfidCredential {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "account_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long accountId;

	@Column(name = "cardholder_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long cardholderId;

	@Column(name = "uid_lookup_value", nullable = false, columnDefinition = "varchar(128)", length = 128)
	private String uidLookupValue;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','REVOKED','EXPIRED')")
	private Status status = Status.ACTIVE;

	@Column(name = "issued_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime issuedAt;

	@Column(name = "revoked_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime revokedAt;

	@Column(name = "expires_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime expiresAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (issuedAt == null) {
			issuedAt = now;
		}
	}

	public enum Status {
		ACTIVE, REVOKED, EXPIRED
	}
}
