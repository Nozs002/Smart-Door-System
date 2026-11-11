CREATE TABLE device_transport_credentials (
    device_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    credential_id VARCHAR(36) NOT NULL,
    verified_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_transport_credential (credential_id),
    CONSTRAINT fk_transport_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processed_device_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    device_id BIGINT UNSIGNED NOT NULL,
    received_at DATETIME(3) NOT NULL,
    KEY idx_processed_message_time (received_at),
    CONSTRAINT fk_processed_message_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
