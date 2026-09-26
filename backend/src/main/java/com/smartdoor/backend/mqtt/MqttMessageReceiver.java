package com.smartdoor.backend.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.smartdoor.backend.door.DoorSignalService;

@Component
public class MqttMessageReceiver {

	private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessageReceiver.class);
	private final DoorSignalService doorSignalService;

	public MqttMessageReceiver(DoorSignalService doorSignalService) {
		this.doorSignalService = doorSignalService;
	}

	@ServiceActivator(inputChannel = "mqttInboundChannel")
	public void receive(Message<String> message) {
		String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
		LOGGER.info("Received MQTT message from topic {}: {}", topic, message.getPayload());
		if (topic == null) {
			throw new IllegalArgumentException("MQTT message does not contain a received topic");
		}
		doorSignalService.process(topic, message.getPayload());
	}
}
