package com.smartdoor.backend.door;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.smartdoor.backend.mqtt.MqttPublisher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DoorCommandService {

	private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private final MqttPublisher mqttPublisher;
	private final ObjectMapper objectMapper;

	public DoorCommandService(MqttPublisher mqttPublisher, ObjectMapper objectMapper) {
		this.mqttPublisher = mqttPublisher;
		this.objectMapper = objectMapper;
	}

	public DoorCommandResponse open(String deviceId) {
		validateDeviceId(deviceId);
		String requestId = UUID.randomUUID().toString();
		DoorCommand command = new DoorCommand("OPEN", requestId);
		mqttPublisher.publish(commandTopic(deviceId), toJson(command));
		return new DoorCommandResponse(requestId, "ACCEPTED");
	}

	private String commandTopic(String deviceId) {
		return "smart-door/" + deviceId + "/command";
	}

	private String toJson(DoorCommand command) {
		try {
			return objectMapper.writeValueAsString(command);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Cannot serialize MQTT door command", exception);
		}
	}

	private void validateDeviceId(String deviceId) {
		if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
			throw new IllegalArgumentException("Invalid device ID");
		}
	}
}
