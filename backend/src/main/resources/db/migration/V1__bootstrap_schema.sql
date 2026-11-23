SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_accounts_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cardholders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_cardholders_account (id, account_id),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE doors (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_account_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(255) NULL,
    status ENUM('UNCLAIMED','SETUP_PENDING','ACTIVE','DISABLED') NOT NULL DEFAULT 'UNCLAIMED',
    operating_mode ENUM('AUTOMATIC','SECURITY') NOT NULL DEFAULT 'SECURITY',
    commanded_lock_state ENUM('LOCKED','UNLOCKED') NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uk_doors_owner (id, owner_account_id),
    FOREIGN KEY (owner_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_doors_owner CHECK (
        (status = 'UNCLAIMED' AND owner_account_id IS NULL)
        OR (status <> 'UNCLAIMED' AND owner_account_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE door_configs (
    door_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    max_failed_attempts INT UNSIGNED NOT NULL DEFAULT 3,
    lockout_seconds INT UNSIGNED NOT NULL DEFAULT 60,
    temperature_threshold DECIMAL(5,2) NOT NULL DEFAULT 50.00,
    open_timeout_seconds INT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    CHECK (max_failed_attempts > 0 AND lockout_seconds > 0 AND version > 0),
    CHECK (open_timeout_seconds IS NULL OR open_timeout_seconds > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_pins (
    door_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    pin_hash VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    door_id BIGINT UNSIGNED NOT NULL,
    device_code VARCHAR(100) NOT NULL,
    node_type ENUM('NODE_1','NODE_2') NOT NULL,
    lifecycle_status ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED') NOT NULL DEFAULT 'PROVISIONED',
    active_node_type VARCHAR(6) GENERATED ALWAYS AS (
        CASE WHEN lifecycle_status = 'ACTIVE' THEN node_type ELSE NULL END) STORED,
    firmware_version VARCHAR(50) NULL,
    last_heartbeat_at DATETIME(3) NULL,
    applied_config_version BIGINT UNSIGNED NULL,
    applied_pin_version BIGINT UNSIGNED NULL,
    registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uk_devices_code (device_code),
    UNIQUE KEY uk_devices_scope (id, door_id),
    UNIQUE KEY uk_devices_active_slot (door_id, active_node_type),
    KEY idx_devices_heartbeat (last_heartbeat_at),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    CHECK (lifecycle_status <> 'ACTIVE' OR activated_at IS NOT NULL),
    CHECK (lifecycle_status <> 'RETIRED' OR retired_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE door_activation_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    door_id BIGINT UNSIGNED NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    consumed_at DATETIME(3) NULL,
    consumed_by_account_id BIGINT UNSIGNED NULL,
    revoked_at DATETIME(3) NULL,
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    FOREIGN KEY (consumed_by_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CHECK (expires_at > created_at),
    CHECK ((consumed_at IS NULL AND consumed_by_account_id IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_by_account_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE device_lifecycle_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    door_id BIGINT UNSIGNED NOT NULL,
    actor_account_id BIGINT UNSIGNED NULL,
    event_type ENUM('PROVISIONED','ACTIVATED','DISABLED','REACTIVATED','RETIRED','REPLACED') NOT NULL,
    from_status ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED') NULL,
    to_status ENUM('PROVISIONED','ACTIVE','DISABLED','RETIRED') NOT NULL,
    replacement_device_id BIGINT UNSIGNED NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_lifecycle_event (event_id),
    KEY idx_lifecycle_history (device_id, occurred_at),
    FOREIGN KEY (device_id, door_id) REFERENCES devices(id, door_id) ON DELETE RESTRICT,
    FOREIGN KEY (replacement_device_id, door_id) REFERENCES devices(id, door_id) ON DELETE RESTRICT,
    FOREIGN KEY (door_id, actor_account_id) REFERENCES doors(id, owner_account_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rfid_credentials (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    door_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    cardholder_id BIGINT UNSIGNED NOT NULL,
    uid_lookup_value VARCHAR(128) NOT NULL,
    status ENUM('ACTIVE','REVOKED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    issued_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at DATETIME(3) NULL,
    expires_at DATETIME(3) NULL,
    UNIQUE KEY uk_rfid_door_uid (door_id, uid_lookup_value),
    UNIQUE KEY uk_rfid_scope (id, door_id),
    FOREIGN KEY (door_id, account_id) REFERENCES doors(id, owner_account_id) ON DELETE RESTRICT,
    FOREIGN KEY (cardholder_id, account_id) REFERENCES cardholders(id, account_id) ON DELETE RESTRICT,
    CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    door_id BIGINT UNSIGNED NOT NULL,
    actor_account_id BIGINT UNSIGNED NULL,
    rfid_credential_id BIGINT UNSIGNED NULL,
    source_device_id BIGINT UNSIGNED NULL,
    method ENUM('RFID','PIN','APP','EXIT_BUTTON','AUTO_PROXIMITY','FIRE_EMERGENCY') NOT NULL,
    result ENUM('GRANTED','DENIED','EXECUTED','FAILED','UNKNOWN') NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(3) NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_access_event (event_id),
    KEY idx_access_door_time (door_id, received_at),
    KEY idx_access_correlation (correlation_id),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    FOREIGN KEY (door_id, actor_account_id) REFERENCES doors(id, owner_account_id) ON DELETE RESTRICT,
    FOREIGN KEY (rfid_credential_id, door_id) REFERENCES rfid_credentials(id, door_id) ON DELETE RESTRICT,
    FOREIGN KEY (source_device_id, door_id) REFERENCES devices(id, door_id) ON DELETE RESTRICT,
    CHECK (method <> 'APP' OR actor_account_id IS NOT NULL),
    CHECK (method = 'APP' OR actor_account_id IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lockout_statuses (
    door_id BIGINT UNSIGNED NOT NULL,
    method ENUM('RFID','PIN') NOT NULL,
    failed_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (door_id, method),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alerts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    door_id BIGINT UNSIGNED NOT NULL,
    source_device_id BIGINT UNSIGNED NULL,
    type ENUM('TOO_MANY_FAILURES','HIGH_TEMPERATURE','DOOR_OPEN_TOO_LONG') NOT NULL,
    severity ENUM('WARNING','CRITICAL') NOT NULL,
    status ENUM('ACTIVE','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    observed_temperature DECIMAL(5,2) NULL,
    triggered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    KEY idx_alerts_door_status (door_id, status, triggered_at),
    FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT,
    FOREIGN KEY (source_device_id, door_id) REFERENCES devices(id, door_id) ON DELETE RESTRICT,
    CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
