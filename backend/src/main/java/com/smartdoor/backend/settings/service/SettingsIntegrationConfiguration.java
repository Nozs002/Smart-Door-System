package com.smartdoor.backend.settings.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.smartdoor.backend.infrastructure.outbox.OutboxService;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SettingsIntegrationConfiguration {
	@Bean
	@ConditionalOnMissingBean(DeviceCommandPublisher.class)
	DeviceCommandPublisher deviceCommandPublisher(OutboxService outbox, ObjectMapper json) {
		return new DeviceCommandPublisher() {
			@Override public void publishConfiguration(ConfigurationCommand command) {
				outbox.enqueue(OutboxService.CONFIGURATION, command.doorId(), command.deviceCode(), json.writeValueAsString(command));
			}
			@Override public void publishCredentials(CredentialsCommand command) {
				outbox.enqueue(OutboxService.CREDENTIALS, command.doorId(), command.deviceCode(), json.writeValueAsString(command));
			}
		};
	}
}
