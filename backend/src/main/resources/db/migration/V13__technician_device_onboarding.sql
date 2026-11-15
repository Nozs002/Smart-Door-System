ALTER TABLE accounts ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD CONSTRAINT chk_account_role CHECK (role IN ('CUSTOMER','TECHNICIAN'));

CREATE TABLE device_inventory (
    unit_code VARCHAR(40) NOT NULL PRIMARY KEY,
    node_type VARCHAR(10) NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NULL UNIQUE,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_inventory_creator FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_node_type CHECK (node_type IN ('NODE_1','NODE_2'))
);
CREATE TABLE installation_kits (
    request_id VARCHAR(36) NOT NULL PRIMARY KEY,
    technician_id BIGINT UNSIGNED NOT NULL,
    door_id BIGINT UNSIGNED NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_kit_technician FOREIGN KEY (technician_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_kit_door FOREIGN KEY (door_id) REFERENCES doors(id) ON DELETE RESTRICT
);
