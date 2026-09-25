SET NAMES utf8mb4;
SET time_zone = '+07:00';

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    display_name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NULL,
    password_hash VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    role ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    phone VARCHAR(20) NULL,
    name VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_phone (phone)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rfid_credentials (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uid VARCHAR(128) NOT NULL,
    status ENUM('ACTIVE', 'REVOKED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    issued_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rfid_credentials_uid (uid),
    KEY idx_rfid_credentials_user_id (user_id),
    CONSTRAINT fk_rfid_credentials_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_rfid_credentials_revoked_at
        CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE doors (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    operating_mode ENUM('AUTOMATIC', 'SECURITY') NOT NULL DEFAULT 'SECURITY',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE door_configs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    max_failed_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    lockout_seconds INT UNSIGNED NOT NULL DEFAULT 300,
    temperature_threshold DECIMAL(5, 2) NOT NULL,
    open_timeout_seconds INT UNSIGNED NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_door_configs_max_failed_attempts
        CHECK (max_failed_attempts > 0),
    CONSTRAINT chk_door_configs_lockout_seconds
        CHECK (lockout_seconds > 0),
    CONSTRAINT chk_door_configs_open_timeout_seconds
        CHECK (open_timeout_seconds IS NULL OR open_timeout_seconds > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE system_pins (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pin_hash VARCHAR(255) NOT NULL,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_system_pins_status (status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_code VARCHAR(100) NOT NULL,
    node_type ENUM('NODE_1', 'NODE_2') NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE', 'DISABLED') NOT NULL DEFAULT 'INACTIVE',
    last_heartbeat_at DATETIME(3) NULL,
    registered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_device_code (device_code),
    KEY idx_devices_status (status),
    KEY idx_devices_last_heartbeat_at (last_heartbeat_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE access_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NOT NULL,
    method ENUM(
        'RFID',
        'PIN',
        'APP',
        'AUTO_PROXIMITY',
        'FIRE_EMERGENCY'
    ) NOT NULL,
    result ENUM('GRANTED', 'DENIED', 'FAILED') NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_logs_event_id (event_id),
    KEY idx_access_logs_occurred_at (occurred_at),
    KEY idx_access_logs_method_result (method, result)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lockout_statuses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    method ENUM('RFID', 'PIN') NOT NULL,
    failed_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lockout_statuses_method (method),
    KEY idx_lockout_statuses_locked_until (locked_until)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE alerts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type ENUM(
        'TOO_MANY_FAILURES',
        'HIGH_TEMPERATURE',
        'DOOR_OPEN_TOO_LONG'
    ) NOT NULL,
    status ENUM('NEW', 'ACKNOWLEDGED', 'RESOLVED') NOT NULL DEFAULT 'NEW',
    triggered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_alerts_status_triggered_at (status, triggered_at),
    CONSTRAINT chk_alerts_resolved_at
        CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
