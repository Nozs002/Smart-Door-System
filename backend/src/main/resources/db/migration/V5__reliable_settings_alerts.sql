-- Durable delivery and owner inbox. Existing business data is preserved.
ALTER TABLE alerts ADD UNIQUE KEY uk_alert_scope (id, door_id);

CREATE TABLE integration_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    door_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    device_code VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status ENUM('PENDING','PROCESSING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    lease_until DATETIME(3) NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(200) NULL,
    created_at DATETIME(3) NOT NULL,
    sent_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    KEY idx_outbox_ready (status, next_attempt_at, id),
    KEY idx_outbox_door (door_id, id),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    FOREIGN KEY (device_id, door_id) REFERENCES devices(id, door_id) ON DELETE RESTRICT,
    CHECK (kind IN ('CONFIGURATION','CREDENTIALS')),
    CHECK (attempts >= 0),
    CHECK ((status = 'PROCESSING' AND lease_until IS NOT NULL AND lease_token IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_until IS NULL AND lease_token IS NULL)),
    CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE owner_notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    alert_id BIGINT UNSIGNED NOT NULL,
    door_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    UNIQUE KEY uk_notification_alert_owner (alert_id, account_id),
    KEY idx_notifications_owner (account_id, read_at, id),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    FOREIGN KEY (alert_id, door_id) REFERENCES alerts(id, door_id) ON DELETE RESTRICT,
    FOREIGN KEY (door_id, account_id) REFERENCES doors(id, owner_account_id) ON DELETE RESTRICT,
    CHECK (read_at IS NULL OR read_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
