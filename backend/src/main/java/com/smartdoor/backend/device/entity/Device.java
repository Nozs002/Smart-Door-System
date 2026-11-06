package com.smartdoor.backend.device.entity;

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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mapping bảng devices; quyền truy cập và workflow được kiểm tra tại service. */
@Entity
@Table(name = "devices", uniqueConstraints = {
		@UniqueConstraint(name = "uk_devices_code", columnNames = {"device_code"}),
		@UniqueConstraint(name = "uk_devices_scope", columnNames = {"id", "door_id"}),
		@UniqueConstraint(name = "uk_devices_active_slot", columnNames = {"door_id", "active_node_type"})
})
@Getter
@Setter
@NoArgsConstructor
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
	private Long id;

	@Column(name = "door_id", nullable = false, columnDefinition = "bigint unsigned")
	private Long doorId;

	@Column(name = "device_code", nullable = false, columnDefinition = "varchar(100)", length = 100)
	private String deviceCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "node_type", nullable = false, columnDefinition = "ENUM('NODE_1','NODE_2')")
	private NodeType nodeType;

	@Enumerated(EnumType.STRING)
	@Column(name = "lifecycle_status", nullable = false, columnDefinition = "ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED')")
	private LifecycleStatus lifecycleStatus = LifecycleStatus.PROVISIONED;

	@Setter(AccessLevel.NONE)
	@Column(name = "active_node_type", nullable = true, columnDefinition = "varchar(6)", length = 6, insertable = false, updatable = false)
	private String activeNodeType;

	@Column(name = "firmware_version", nullable = true, columnDefinition = "varchar(50)", length = 50)
	private String firmwareVersion;

	@Column(name = "last_heartbeat_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime lastHeartbeatAt;

	@Column(name = "applied_config_version", nullable = true, columnDefinition = "bigint unsigned")
	private Long appliedConfigVersion;

	@Column(name = "applied_pin_version", nullable = true, columnDefinition = "bigint unsigned")
	private Long appliedPinVersion;

	@Column(name = "registered_at", nullable = false, columnDefinition = "datetime(3)")
	private LocalDateTime registeredAt;

	@Column(name = "activated_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime activatedAt;

	@Column(name = "retired_at", nullable = true, columnDefinition = "datetime(3)")
	private LocalDateTime retiredAt;

	@Version
	@Column(name = "version", nullable = false, columnDefinition = "bigint unsigned")
	private Long version;

	@PrePersist
	void initializeTimestamps() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
		if (registeredAt == null) {
			registeredAt = now;
		}
	}

	public enum NodeType {
		NODE_1, NODE_2
	}

	public enum LifecycleStatus {
		PROVISIONED, ACTIVE, DISABLED, RETIRED
	}
}
