SET NAMES utf8mb4;
SET time_zone = '+07:00';

START TRANSACTION;

INSERT INTO users (id, display_name, username, password_hash, created_at, role, phone, name)
VALUES
    (1, 'Quản trị viên', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NOW(3) - INTERVAL 180 DAY, 'ADMIN', '0901000001', 'Nguyễn Minh Admin'),
    (2, 'Nguyễn An', 'nguyen.an', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NOW(3) - INTERVAL 120 DAY, 'USER', '0901000002', 'Nguyễn Hoàng An'),
    (3, 'Trần Bình', 'tran.binh', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NOW(3) - INTERVAL 90 DAY, 'USER', '0901000003', 'Trần Gia Bình'),
    (4, 'Lê Chi', 'le.chi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NOW(3) - INTERVAL 60 DAY, 'USER', '0901000004', 'Lê Minh Chi'),
    (5, 'Khách thử nghiệm', NULL, NULL, NOW(3) - INTERVAL 7 DAY, 'USER', NULL, 'Người dùng thử');

INSERT INTO rfid_credentials (id, uid, status, issued_at, revoked_at, user_id)
VALUES
    (1, '04-A1-B2-C3-D4-5E-80', 'ACTIVE', NOW(3) - INTERVAL 170 DAY, NULL, 1),
    (2, '04-B2-C3-D4-E5-6F-91', 'ACTIVE', NOW(3) - INTERVAL 110 DAY, NULL, 2),
    (3, '04-C3-D4-E5-F6-70-A2', 'ACTIVE', NOW(3) - INTERVAL 80 DAY, NULL, 3),
    (4, '04-D4-E5-F6-07-81-B3', 'REVOKED', NOW(3) - INTERVAL 55 DAY, NOW(3) - INTERVAL 5 DAY, 4),
    (5, '04-E5-F6-07-18-92-C4', 'EXPIRED', NOW(3) - INTERVAL 365 DAY, NULL, 2);

INSERT INTO doors (id, name, operating_mode, updated_at)
VALUES
    (1, 'Cửa chính', 'SECURITY', NOW(3)),
    (2, 'Cửa phòng kỹ thuật', 'SECURITY', NOW(3) - INTERVAL 1 HOUR),
    (3, 'Cửa thoát hiểm', 'AUTOMATIC', NOW(3) - INTERVAL 30 MINUTE);

INSERT INTO door_configs (id, max_failed_attempts, lockout_seconds, temperature_threshold, open_timeout_seconds)
VALUES
    (1, 5, 300, 55.00, 30),
    (2, 3, 600, 50.00, 20),
    (3, 5, 180, 60.00, 45);

INSERT INTO system_pins (id, pin_hash, status, changed_at)
VALUES
    (1, '$2a$10$7EqJtq98hPqEX7fNZaFWoO5uT6nS0u5xQfZJY4hQKzYw8Cj5M3J6e', FALSE, NOW(3) - INTERVAL 90 DAY),
    (2, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE, NOW(3) - INTERVAL 30 DAY);

INSERT INTO devices (id, device_code, node_type, status, last_heartbeat_at, registered_at)
VALUES
    (1, 'SD-NODE1-ENTRANCE-01', 'NODE_1', 'ACTIVE', NOW(3) - INTERVAL 15 SECOND, NOW(3) - INTERVAL 180 DAY),
    (2, 'SD-NODE2-LOCK-01', 'NODE_2', 'ACTIVE', NOW(3) - INTERVAL 10 SECOND, NOW(3) - INTERVAL 180 DAY),
    (3, 'SD-NODE1-TECH-01', 'NODE_1', 'INACTIVE', NOW(3) - INTERVAL 2 DAY, NOW(3) - INTERVAL 60 DAY),
    (4, 'SD-NODE2-BACKUP-01', 'NODE_2', 'DISABLED', NULL, NOW(3) - INTERVAL 30 DAY);

INSERT INTO access_logs (id, event_id, method, result, reason, occurred_at)
VALUES
    (1, 'EVT-RFID-0001', 'RFID', 'GRANTED', 'Thẻ hợp lệ', NOW(3) - INTERVAL 2 DAY),
    (2, 'EVT-PIN-0001', 'PIN', 'DENIED', 'Mã PIN không chính xác', NOW(3) - INTERVAL 1 DAY - INTERVAL 2 HOUR),
    (3, 'EVT-PIN-0002', 'PIN', 'DENIED', 'Mã PIN không chính xác', NOW(3) - INTERVAL 1 DAY - INTERVAL 1 HOUR),
    (4, 'EVT-APP-0001', 'APP', 'GRANTED', 'Mở cửa từ ứng dụng', NOW(3) - INTERVAL 20 HOUR),
    (5, 'EVT-RFID-0002', 'RFID', 'DENIED', 'Thẻ đã bị thu hồi', NOW(3) - INTERVAL 10 HOUR),
    (6, 'EVT-AUTO-0001', 'AUTO_PROXIMITY', 'GRANTED', 'Phát hiện người dùng trong phạm vi cho phép', NOW(3) - INTERVAL 6 HOUR),
    (7, 'EVT-FIRE-0001', 'FIRE_EMERGENCY', 'GRANTED', 'Mở khóa khẩn cấp do nhiệt độ cao', NOW(3) - INTERVAL 3 HOUR),
    (8, 'EVT-RFID-0003', 'RFID', 'FAILED', 'Không thể đọc dữ liệu thẻ', NOW(3) - INTERVAL 45 MINUTE),
    (9, 'EVT-PIN-0003', 'PIN', 'GRANTED', 'Mã PIN hợp lệ', NOW(3) - INTERVAL 20 MINUTE),
    (10, 'EVT-APP-0002', 'APP', 'FAILED', 'Thiết bị điều khiển không phản hồi', NOW(3) - INTERVAL 5 MINUTE);

INSERT INTO lockout_statuses (id, method, failed_count, locked_until, updated_at)
VALUES
    (1, 'RFID', 1, NULL, NOW(3) - INTERVAL 45 MINUTE),
    (2, 'PIN', 3, NOW(3) + INTERVAL 10 MINUTE, NOW(3));

INSERT INTO alerts (id, type, status, triggered_at, resolved_at)
VALUES
    (1, 'TOO_MANY_FAILURES', 'NEW', NOW(3) - INTERVAL 10 MINUTE, NULL),
    (2, 'HIGH_TEMPERATURE', 'RESOLVED', NOW(3) - INTERVAL 3 HOUR, NOW(3) - INTERVAL 2 HOUR - INTERVAL 45 MINUTE),
    (3, 'DOOR_OPEN_TOO_LONG', 'ACKNOWLEDGED', NOW(3) - INTERVAL 1 HOUR, NULL),
    (4, 'TOO_MANY_FAILURES', 'RESOLVED', NOW(3) - INTERVAL 7 DAY, NOW(3) - INTERVAL 7 DAY + INTERVAL 15 MINUTE);

COMMIT;
