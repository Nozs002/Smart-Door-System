package com.smartdoor.backend.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfiguration {

	@Bean
	MqttPahoClientFactory mqttClientFactory(MqttProperties properties) {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setServerURIs(new String[] { properties.getBrokerUrl() });
		options.setAutomaticReconnect(true);
		options.setCleanSession(false);
		options.setConnectionTimeout(10);
		options.setKeepAliveInterval(30);

		if (StringUtils.hasText(properties.getUsername())) {
			options.setUserName(properties.getUsername());
		}
		if (StringUtils.hasText(properties.getPassword())) {
			options.setPassword(properties.getPassword().toCharArray());
		}

		DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
		factory.setConnectionOptions(options);
		return factory;
	}

	@Bean
	MessageChannel mqttInboundChannel() {
		return new DirectChannel();
	}

	@Bean
	MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(
			MqttProperties properties,
			MqttPahoClientFactory clientFactory) {
		String[] topics = properties.getSubscriptionTopics().toArray(String[]::new);
		MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
				properties.getClientId() + "-subscriber",
				clientFactory,
				topics);
		adapter.setQos(1);
		adapter.setCompletionTimeout(5_000);
		adapter.setOutputChannel(mqttInboundChannel());
		return adapter;
	}

	@Bean
	MessageChannel mqttOutboundChannel() {
		return new DirectChannel();
	}

	@Bean
	@ServiceActivator(inputChannel = "mqttOutboundChannel")
	MessageHandler mqttOutboundHandler(MqttProperties properties, MqttPahoClientFactory clientFactory) {
		MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
				properties.getClientId() + "-publisher",
				clientFactory);
		handler.setAsync(true);
		handler.setDefaultQos(1);
		handler.setDefaultRetained(false);
		return handler;
	}
}
