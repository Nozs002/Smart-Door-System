package com.smartdoor.backend.alert.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.smartdoor.backend.alert.notification.NotificationService;

@Configuration
public class AlertIntegrationConfiguration {
	@Bean
	@ConditionalOnMissingBean(OwnerAlertNotifier.class)
	OwnerAlertNotifier ownerAlertNotifier(NotificationService notifications) {
		return notifications::notifyOwner;
	}

	public record OwnerAlertNotification(Long accountId, com.smartdoor.backend.alert.dto.AlertView alert) { }
}
