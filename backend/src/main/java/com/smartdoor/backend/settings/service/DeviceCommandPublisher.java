package com.smartdoor.backend.settings.service;

import java.math.BigDecimal;

/** Adapter outbound; triển khai MQTT chỉ được publish tới topic của deviceCode đích. */
public interface DeviceCommandPublisher {
	void publishConfiguration(ConfigurationCommand command);
	void publishCredentials(CredentialsCommand command);

	record ConfigurationCommand(Long doorId, String deviceCode, Long version,
			Integer maxFailedAttempts, Integer lockoutSeconds, BigDecimal temperatureThreshold,
			Integer openTimeoutSeconds) { }

	record CredentialsCommand(Long doorId, String deviceCode, Long version, ChangeReason reason) { }

	enum ChangeReason { PIN_CHANGED, RFID_CHANGED, RFID_REVOKED, FULL_REFRESH }
}
