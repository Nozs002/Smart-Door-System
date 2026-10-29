package com.smartdoor.backend.infrastructure.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.settings-mqtt")
public record SettingsMqttProperties(boolean enabled, boolean tls, String deviceMasterKey,
        Integer maxClockSkewSeconds, Integer maxPayloadBytes) {
    public SettingsMqttProperties {
        if (maxClockSkewSeconds == null) maxClockSkewSeconds = 60;
        if (maxPayloadBytes == null) maxPayloadBytes = 65536;
        if (maxClockSkewSeconds < 1 || maxClockSkewSeconds > 300 || maxPayloadBytes < 1024 || maxPayloadBytes > 1048576) {
            throw new IllegalArgumentException("Invalid settings MQTT message limits");
        }
    }
}
