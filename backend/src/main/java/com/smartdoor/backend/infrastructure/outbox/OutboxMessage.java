package com.smartdoor.backend.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "integration_outbox")
@Getter @Setter @NoArgsConstructor
public class OutboxMessage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(columnDefinition = "bigint unsigned")
	private Long id;
	@Column(nullable = false, length = 32)
	private String kind;
	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;
	@Column(name = "device_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long deviceId;
	@Column(name = "device_code", nullable = false, length = 100)
	private String deviceCode;
	@Column(nullable = false, columnDefinition = "longtext")
	private String payload;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "ENUM('PENDING','PROCESSING','SENT','FAILED')")
	private Status status = Status.PENDING;
	@Column(nullable = false)
	private int attempts;
	@Column(name = "next_attempt_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime nextAttemptAt;
	@Column(name = "lease_until", columnDefinition = "datetime(3)")
	private LocalDateTime leaseUntil;
	@Column(name = "lease_token", length = 36)
	private String leaseToken;
	@Column(name = "last_error", length = 200)
	private String lastError;
	@Column(name = "created_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime createdAt;
	@Column(name = "sent_at", columnDefinition = "datetime(3)")
	private LocalDateTime sentAt;
	@Version
	@Column(nullable = false, columnDefinition = "bigint unsigned")
	private Long version;

	public enum Status { PENDING, PROCESSING, SENT, FAILED }
}
