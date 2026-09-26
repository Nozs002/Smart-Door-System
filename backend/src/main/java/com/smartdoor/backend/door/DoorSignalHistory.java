package com.smartdoor.backend.door;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "door_signal_history")
public class DoorSignalHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String deviceId;

	@Column(nullable = false, length = 16)
	private String signalType;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(length = 64)
	private String requestId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(nullable = false)
	private Instant receivedAt;

	protected DoorSignalHistory() {
	}

	public DoorSignalHistory(
			String deviceId,
			String signalType,
			String status,
			String requestId,
			String payload,
			Instant receivedAt) {
		this.deviceId = deviceId;
		this.signalType = signalType;
		this.status = status;
		this.requestId = requestId;
		this.payload = payload;
		this.receivedAt = receivedAt;
	}

	public Long getId() {
		return id;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getSignalType() {
		return signalType;
	}

	public String getStatus() {
		return status;
	}

	public String getRequestId() {
		return requestId;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}
}
