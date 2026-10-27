package com.smartdoor.backend.alert.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

@Configuration
public class AlertIntegrationConfiguration {
	@Bean
	@ConditionalOnMissingBean(OwnerAlertNotifier.class)
	OwnerAlertNotifier ownerAlertNotifier(ApplicationEventPublisher events) {
		return (accountId, alert) -> events.publishEvent(new OwnerAlertNotification(accountId, alert));
	}

	public record OwnerAlertNotification(Long accountId, com.smartdoor.backend.alert.dto.AlertView alert) { }
}
