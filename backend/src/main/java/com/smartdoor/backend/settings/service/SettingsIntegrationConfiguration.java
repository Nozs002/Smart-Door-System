package com.smartdoor.backend.settings.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

@Configuration
public class SettingsIntegrationConfiguration {
	@Bean
	@ConditionalOnMissingBean(DeviceCommandPublisher.class)
	DeviceCommandPublisher deviceCommandPublisher(ApplicationEventPublisher events) {
		return new DeviceCommandPublisher() {
			@Override public void publishConfiguration(ConfigurationCommand command) {
				events.publishEvent(command);
			}
			@Override public void publishCredentials(CredentialsCommand command) {
				events.publishEvent(command);
			}
		};
	}
}
