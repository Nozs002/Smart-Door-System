package com.smartdoor.backend.settings.mqtt;

import com.smartdoor.backend.device.service.DeviceHeartbeatService.AuthenticatedDeviceIdentity;
import com.smartdoor.backend.settings.service.DeviceSyncAcknowledgementService;
import com.smartdoor.backend.shared.error.BadRequestException;
import org.springframework.stereotype.Component;

/** Adapter module-level cho topic sync-ack; QoS 1 an toàn nhờ cập nhật đơn điệu. */
@Component
public class SyncAckMessageHandler {
	private final DeviceSyncAcknowledgementService acknowledgements;

	public SyncAckMessageHandler(DeviceSyncAcknowledgementService acknowledgements) {
		this.acknowledgements = acknowledgements;
	}

	public void handle(AuthenticatedDeviceIdentity identity, SyncAckMessage message) {
		if (message == null || message.kind() == null || message.eventId() == null
				|| message.eventId().isBlank() || message.eventId().length() > 100) {
			throw new BadRequestException("ACK không hợp lệ.");
		}
		switch (message.kind()) {
			case CONFIGURATION -> acknowledgements.acknowledgeConfiguration(identity, message.version());
			case CREDENTIALS -> acknowledgements.acknowledgeCredentials(identity, message.version());
		}
	}

	public record SyncAckMessage(String eventId, Kind kind, long version) { }
	public enum Kind { CONFIGURATION, CREDENTIALS }
}
