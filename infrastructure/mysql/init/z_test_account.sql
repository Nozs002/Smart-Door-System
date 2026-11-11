-- Người sửa: Sơn
-- Thời gian sửa: 21/10/2026 17:21 (UTC+07:00, Việt Nam)
-- Mục đích: Cung cấp dữ liệu mẫu local/demo để kiểm thử đăng nhập và quản lý cửa qua backend thật.
-- Tóm tắt thay đổi:
--   + Tạo tài khoản test với mật khẩu demo được băm BCrypt.
--   + Bổ sung 3 cửa, 8 thiết bị, lịch sử lifecycle, Cardholder, RFID giả, access log và cảnh báo.
--   + Cho phép nạp lại script mà không tạo trùng dữ liệu mẫu; đặt lại mật khẩu và tên hiển thị tài khoản test.
--   + Không tạo heartbeat, PIN, mã kích hoạt, credential MQTT hoặc ACK giả.
--
-- LOCAL/DEMO ONLY: public test account, never import into production.
-- Run after init.sql. Re-running resets ONLY test's password/display name;
-- existing devices, lifecycle actions and logs are preserved.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

INSERT INTO accounts (display_name, username, password_hash, status)
VALUES ('Người dùng kiểm thử', 'test',
'$2a$10$OBcs5Ygw7p57CpuyZfcOkuXxB3PvS.AnPOyRMvwN8etoFsNZ0oDTW', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = 'Người dùng kiểm thử',
    password_hash = '$2a$10$OBcs5Ygw7p57CpuyZfcOkuXxB3PvS.AnPOyRMvwN8etoFsNZ0oDTW';
SET @test_account = (SELECT id FROM accounts WHERE username = 'test');

INSERT INTO doors (owner_account_id, name, location, status, commanded_lock_state)
SELECT @test_account, 'Cửa chính - test', 'Tầng 1 · Nhà mẫu', 'ACTIVE', 'LOCKED'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE device_code = 'TEST-MAIN-N1');
SET @test_main = COALESCE((SELECT door_id FROM devices WHERE device_code='TEST-MAIN-N1'),
    (SELECT id FROM doors WHERE owner_account_id=@test_account AND name='Cửa chính - test' ORDER BY id LIMIT 1));

INSERT INTO doors (owner_account_id, name, location, status)
SELECT @test_account, 'Cửa kho - test', 'Tầng 1 · Kho mẫu', 'SETUP_PENDING'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE device_code = 'TEST-STORE-N1');
SET @test_store = COALESCE((SELECT door_id FROM devices WHERE device_code='TEST-STORE-N1'),
    (SELECT id FROM doors WHERE owner_account_id=@test_account AND name='Cửa kho - test' ORDER BY id LIMIT 1));

INSERT INTO doors (owner_account_id, name, location, status)
SELECT @test_account, 'Cửa sân - test', 'Sân sau · Nhà mẫu', 'DISABLED'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE device_code = 'TEST-YARD-N1');
SET @test_yard = COALESCE((SELECT door_id FROM devices WHERE device_code='TEST-YARD-N1'),
    (SELECT id FROM doors WHERE owner_account_id=@test_account AND name='Cửa sân - test' ORDER BY id LIMIT 1));

INSERT INTO door_configs (door_id)
SELECT d.id FROM doors d WHERE d.id IN (@test_main,@test_store,@test_yard)
AND NOT EXISTS (SELECT 1 FROM door_configs c WHERE c.door_id=d.id);

INSERT INTO devices (door_id, device_code, node_type, lifecycle_status,
    firmware_version, last_heartbeat_at, activated_at, retired_at)
SELECT d.door_id,d.code,d.node,d.lifecycle,'demo-1.0',NULL,
    CASE WHEN d.lifecycle IN ('ACTIVE','DISABLED','RETIRED')
        THEN UTC_TIMESTAMP(3) - INTERVAL 7 DAY ELSE NULL END,
    CASE WHEN d.lifecycle='RETIRED' THEN UTC_TIMESTAMP(3) - INTERVAL 1 DAY ELSE NULL END
FROM (
 SELECT @test_main door_id,'TEST-MAIN-N1' code,'NODE_1' node,'ACTIVE' lifecycle
 UNION ALL SELECT @test_main,'TEST-MAIN-N2','NODE_2','ACTIVE'
 UNION ALL SELECT @test_main,'TEST-MAIN-N1-OLD','NODE_1','RETIRED'
 UNION ALL SELECT @test_main,'TEST-MAIN-N1-SPARE','NODE_1','PROVISIONED'
 UNION ALL SELECT @test_store,'TEST-STORE-N1','NODE_1','PROVISIONED'
 UNION ALL SELECT @test_store,'TEST-STORE-N2','NODE_2','PROVISIONED'
 UNION ALL SELECT @test_yard,'TEST-YARD-N1','NODE_1','DISABLED'
 UNION ALL SELECT @test_yard,'TEST-YARD-N2','NODE_2','DISABLED'
) d WHERE NOT EXISTS (SELECT 1 FROM devices e WHERE e.device_code=d.code);

INSERT INTO device_lifecycle_events
(event_id,device_id,door_id,actor_account_id,event_type,from_status,to_status,reason)
SELECT CONCAT('TEST-SEED-',d.device_code),d.id,d.door_id,@test_account,
    CASE d.lifecycle_status WHEN 'ACTIVE' THEN 'ACTIVATED'
        WHEN 'DISABLED' THEN 'DISABLED' WHEN 'RETIRED' THEN 'RETIRED'
        ELSE 'PROVISIONED' END,
    CASE WHEN d.lifecycle_status='ACTIVE' THEN 'PROVISIONED'
        WHEN d.lifecycle_status IN ('DISABLED','RETIRED') THEN 'ACTIVE' ELSE NULL END,
    d.lifecycle_status,'Dữ liệu mẫu để kiểm thử giao diện qua REST API'
FROM devices d WHERE d.door_id IN (@test_main,@test_store,@test_yard)
AND d.device_code LIKE 'TEST-%'
AND NOT EXISTS (SELECT 1 FROM device_lifecycle_events e
    WHERE e.event_id=CONCAT('TEST-SEED-',d.device_code));

INSERT INTO cardholders (account_id,display_name,status)
SELECT @test_account,'Thành viên mẫu','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM cardholders WHERE account_id=@test_account
    AND display_name='Thành viên mẫu');
SET @test_cardholder = (SELECT id FROM cardholders WHERE account_id=@test_account
    AND display_name='Thành viên mẫu' ORDER BY id LIMIT 1);
INSERT INTO rfid_credentials (door_id,account_id,cardholder_id,uid_lookup_value,status)
SELECT @test_main,@test_account,@test_cardholder,'TEST-FAKE-UID-001','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM rfid_credentials WHERE door_id=@test_main
    AND uid_lookup_value='TEST-FAKE-UID-001');

INSERT INTO access_logs (event_id,door_id,actor_account_id,method,result,reason)
SELECT 'TEST-ACCESS-APP-01',@test_main,@test_account,'APP','UNKNOWN',
    'Bản ghi giả: chưa có ACK thực thi từ phần cứng'
WHERE NOT EXISTS (SELECT 1 FROM access_logs WHERE event_id='TEST-ACCESS-APP-01');

INSERT INTO alerts (door_id,source_device_id,type,severity,status,observed_temperature)
SELECT @test_main,d.id,'HIGH_TEMPERATURE','WARNING','ACTIVE',52.00
FROM devices d WHERE d.device_code='TEST-MAIN-N2'
AND NOT EXISTS (SELECT 1 FROM alerts a WHERE a.source_device_id=d.id
    AND a.type='HIGH_TEMPERATURE');

COMMIT;
-- Heartbeats are intentionally NULL: no hardware is connected.
-- No PIN, activation code, MQTT secret or fake readiness/ACK is seeded.
