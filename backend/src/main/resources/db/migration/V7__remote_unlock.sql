CREATE TABLE control_commands (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    door_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    device_code VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY idx_control_door_time (door_id, created_at),
    FOREIGN KEY (door_id, account_id) REFERENCES doors(id, owner_account_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
