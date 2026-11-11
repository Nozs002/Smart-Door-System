package com.smartdoor.backend.device.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DeviceIntegrationConfiguration {

	/** Fail closed until the identity/configuration modules supply verified evidence. */
	@Bean
	@ConditionalOnMissingBean(DeviceReadinessPolicy.class)
	DeviceReadinessPolicy pendingDeviceReadinessPolicy() {
		return device -> false;
	}
}
