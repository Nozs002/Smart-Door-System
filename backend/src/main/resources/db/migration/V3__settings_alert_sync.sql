ALTER TABLE door_configs
    ADD COLUMN credentials_version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER version;

ALTER TABLE alerts
    ADD COLUMN event_id VARCHAR(100) NULL AFTER id,
    ADD UNIQUE KEY uk_alert_event (event_id);
