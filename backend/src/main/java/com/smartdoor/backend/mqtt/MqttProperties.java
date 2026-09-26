package com.smartdoor.backend.mqtt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

	private String brokerUrl;
	private String username;
	private String password;
	private String clientId;
	private List<String> subscriptionTopics = new ArrayList<>();

	public String getBrokerUrl() {
		return brokerUrl;
	}

	public void setBrokerUrl(String brokerUrl) {
		this.brokerUrl = brokerUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public List<String> getSubscriptionTopics() {
		return subscriptionTopics;
	}

	public void setSubscriptionTopics(List<String> subscriptionTopics) {
		this.subscriptionTopics = subscriptionTopics;
	}
}
