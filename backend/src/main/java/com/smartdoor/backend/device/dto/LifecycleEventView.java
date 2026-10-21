package com.smartdoor.backend.device.dto;

import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.entity.DeviceLifecycleEvent;
import java.time.LocalDateTime;

public record LifecycleEventView(Long id, String eventId, Long deviceId, Long doorId,
		Long actorAccountId, DeviceLifecycleEvent.EventType eventType,
		Device.LifecycleStatus fromStatus, Device.LifecycleStatus toStatus,
		Long replacementDeviceId, String reason, LocalDateTime occurredAt) {

	public static LifecycleEventView from(DeviceLifecycleEvent event) {
		return new LifecycleEventView(event.getId(), event.getEventId(), event.getDeviceId(),
				event.getDoorId(), event.getActorAccountId(), event.getEventType(), event.getFromStatus(),
				event.getToStatus(), event.getReplacementDeviceId(), event.getReason(), event.getOccurredAt());
	}
}
