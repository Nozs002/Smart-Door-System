package com.smartdoor.backend.door.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Mapping bảng door_activation_codes; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "door_activation_codes", uniqueConstraints = {
		@UniqueConstraint(name = "uk_activation_code_hash", columnNames = "code_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class DoorActivationCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "code_hash", nullable = false, columnDefinition = "varchar(255)", length = 255)
	private String codeHash;

	@Column(name = "expires_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime createdAt;

	@Column(name = "consumed_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime consumedAt;

	@Column(name = "consumed_by_account_id", nullable = true, columnDefinition = "bigint unsigned")
	private Long consumedByAccountId;

	@Column(name = "revoked_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime revokedAt;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (createdAt == null) {
			createdAt = now;
		}
	}
}
