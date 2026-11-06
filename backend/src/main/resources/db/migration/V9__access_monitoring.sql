ALTER TABLE access_logs ADD COLUMN cardholder_id BIGINT UNSIGNED NULL;
ALTER TABLE access_logs ADD COLUMN locked_until DATETIME(3) NULL;
ALTER TABLE access_logs ADD CONSTRAINT fk_access_cardholder FOREIGN KEY (cardholder_id) REFERENCES cardholders(id) ON DELETE RESTRICT;
ALTER TABLE control_commands ADD COLUMN access_method VARCHAR(24) NOT NULL DEFAULT 'APP';
ALTER TABLE integration_outbox MODIFY COLUMN status ENUM('PENDING','PROCESSING','SENT','FAILED','EXPIRED') NOT NULL;
CREATE TABLE door_telemetry (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    door_id BIGINT UNSIGNED NOT NULL,
    source_device_id BIGINT UNSIGNED NOT NULL,
    temperature DOUBLE NULL,
    humidity DOUBLE NULL,
    door_state VARCHAR(16) NOT NULL,
    lock_state VARCHAR(16) NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    KEY idx_telemetry_door_time (door_id, observed_at, id),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    FOREIGN KEY (source_device_id) REFERENCES devices(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
