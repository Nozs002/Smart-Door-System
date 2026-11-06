ALTER TABLE door_configs
    ADD COLUMN fire_alarm_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN door_open_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE door_configs SET door_open_alert_enabled = TRUE WHERE open_timeout_seconds IS NOT NULL;

ALTER TABLE door_configs ADD CONSTRAINT chk_open_alert_timeout
    CHECK (door_open_alert_enabled = FALSE OR open_timeout_seconds IS NOT NULL);

-- New snapshot fields must be acknowledged before being shown as applied.
UPDATE door_configs SET version = version + 1;
