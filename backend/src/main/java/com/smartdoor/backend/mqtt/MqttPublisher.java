package com.smartdoor.backend.mqtt;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MqttPublisher {

	private final MessageChannel mqttOutboundChannel;

	public MqttPublisher(@Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
		this.mqttOutboundChannel = mqttOutboundChannel;
	}

	public void publish(String topic, String payload) {
		publish(topic, payload, 1, false);
	}

	public void publish(String topic, String payload, int qos, boolean retained) {
		mqttOutboundChannel.send(MessageBuilder.withPayload(payload)
				.setHeader(MqttHeaders.TOPIC, topic)
				.setHeader(MqttHeaders.QOS, qos)
				.setHeader(MqttHeaders.RETAINED, retained)
				.build());
	}
}
