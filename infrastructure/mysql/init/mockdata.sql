-- Local/demo fixtures only. Never import into production.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- Public test hash, not a real customer credential. Set a new password via
-- the future Account service before testing login.
INSERT INTO accounts (id, display_name, username, password_hash) VALUES
(1,'Khách hàng A','demo.a','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
(2,'Khách hàng B','demo.b','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
(3,'Khách hàng chưa có cửa','demo.empty','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

INSERT INTO cardholders (id, account_id, display_name, status) VALUES
(1,1,'Người giữ thẻ A','ACTIVE'),(2,2,'Người giữ thẻ B','ACTIVE'),
(3,1,'Người giữ thẻ đã vô hiệu hóa','DISABLED');

INSERT INTO doors (id, owner_account_id, name, status, commanded_lock_state) VALUES
(1,1,'Cửa demo vật lý','ACTIVE','LOCKED'),
(2,1,'Cửa kho mô phỏng','ACTIVE',NULL),
(3,2,'Cửa khách B mô phỏng','ACTIVE',NULL),
(4,NULL,'Bộ cửa chưa claim','UNCLAIMED',NULL);

INSERT INTO door_configs (door_id) VALUES (1),(2),(3),(4);
INSERT INTO system_pins (door_id, pin_hash) VALUES
(1,'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
(2,'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
(3,'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

INSERT INTO devices
(id, door_id, device_code, node_type, lifecycle_status, last_heartbeat_at, activated_at, retired_at) VALUES
(1,1,'DEMO-D1-N1','NODE_1','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),NULL),
(2,1,'DEMO-D1-N2','NODE_2','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),NULL),
(3,2,'SIM-D2-N1','NODE_1','ACTIVE',UTC_TIMESTAMP(3) - INTERVAL 1 DAY,UTC_TIMESTAMP(3),NULL),
(4,2,'SIM-D2-N2','NODE_2','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),NULL),
(5,3,'SIM-D3-N1','NODE_1','ACTIVE',NULL,UTC_TIMESTAMP(3),NULL),
(6,3,'SIM-D3-N2','NODE_2','ACTIVE',NULL,UTC_TIMESTAMP(3),NULL),
(7,4,'SIM-D4-N1','NODE_1','PROVISIONED',NULL,NULL,NULL),
(8,4,'SIM-D4-N2','NODE_2','PROVISIONED',NULL,NULL,NULL),
(9,1,'DEMO-D1-N1-OLD','NODE_1','RETIRED',NULL,UTC_TIMESTAMP(3) - INTERVAL 30 DAY,UTC_TIMESTAMP(3));

INSERT INTO device_lifecycle_events
(event_id, device_id, door_id, actor_account_id, event_type, from_status, to_status, replacement_device_id, reason) VALUES
('LIFE-D1-OLD-REPLACED',9,1,1,'REPLACED','ACTIVE','RETIRED',1,'Thay Node, giữ lịch sử bản cũ'),
('LIFE-D1-N1-ACTIVE',1,1,1,'ACTIVATED','PROVISIONED','ACTIVE',NULL,'Thiết bị mới đã sẵn sàng'),
('LIFE-D4-N1-PROVISION',7,4,NULL,'PROVISIONED',NULL,'PROVISIONED',NULL,'Provision kỹ thuật');

INSERT INTO rfid_credentials
(id, door_id, account_id, cardholder_id, uid_lookup_value, status, revoked_at) VALUES
(1,1,1,1,'DEMO-UID-001','ACTIVE',NULL),
(2,2,1,1,'DEMO-UID-001','ACTIVE',NULL),
(3,3,2,2,'DEMO-UID-001','ACTIVE',NULL),
(4,1,1,3,'DEMO-UID-REVOKED','REVOKED',UTC_TIMESTAMP(3));

INSERT INTO access_logs
(event_id, correlation_id, door_id, actor_account_id, rfid_credential_id, source_device_id, method, result, reason) VALUES
('ACCESS-RFID-01','ATTEMPT-01',1,NULL,1,1,'RFID','GRANTED','Xác thực hợp lệ'),
('ACCESS-EXEC-01','ATTEMPT-01',1,NULL,NULL,2,'RFID','EXECUTED','Node phản hồi thực thi'),
('ACCESS-APP-01','ATTEMPT-02',1,1,NULL,NULL,'APP','UNKNOWN','Đang chờ xác minh kết quả'),
('ACCESS-EXIT-01',NULL,1,NULL,NULL,2,'EXIT_BUTTON','EXECUTED','Mở từ bên trong');

INSERT INTO lockout_statuses (door_id, method) VALUES
(1,'RFID'),(1,'PIN'),(2,'RFID'),(2,'PIN'),
(3,'RFID'),(3,'PIN'),(4,'RFID'),(4,'PIN');

INSERT INTO alerts
(door_id, source_device_id, type, severity, status, observed_temperature, resolved_at) VALUES
(1,2,'HIGH_TEMPERATURE','CRITICAL','RESOLVED',55.00,UTC_TIMESTAMP(3)),
(2,3,'TOO_MANY_FAILURES','WARNING','ACTIVE',NULL,NULL);

-- No activation code or MQTT secret is seeded. Provision must issue fresh
-- random credentials/code and store only the protected values.
COMMIT;
