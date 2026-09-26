package com.smartdoor.backend.door;

import java.time.Instant;

public record DoorSignalResponse(
		Long id,
		String deviceId,
		String signalType,
		String status,
		String requestId,
		Instant receivedAt) {

	static DoorSignalResponse from(DoorSignalHistory history) {
		return new DoorSignalResponse(
				history.getId(),
				history.getDeviceId(),
				history.getSignalType(),
				history.getStatus(),
				history.getRequestId(),
				history.getReceivedAt());
	}
}
