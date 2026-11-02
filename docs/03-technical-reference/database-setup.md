---
title: Khởi tạo và chuyển đổi database
type: technical-reference
lang: vi
updated: 2026-10-28
---

# Khởi tạo và chuyển đổi database

Hiện tại Backend nâng cấp đến **Flyway V6**. V3 thêm phiên bản credentials và
event ID cảnh báo; V4 thêm phiên đăng nhập/thu hồi token; V5 thêm outbox và
inbox; V6 thêm danh tính MQTT và receipt chống replay. Xem
[bảng thay đổi và quy trình test](../05-backend/person1-person4-completion.md).

Với database của dự án đã ở V1/V2/V3, giữ volume, sao lưu rồi chạy Backend mới:

```powershell
python scripts/db-check.py --backup
python scripts/setup-mqtt.py init
docker compose up -d --build
python scripts/db-check.py --expect-version 6
```

Không sửa checksum các migration đã áp dụng, không xóa volume để nâng cấp.
Các hướng dẫn fixture/legacy phía dưới mô tả bootstrap cũ; sau bootstrap
Backend vẫn cần áp dụng V2–V6. Cổng MySQL từ Windows lấy theo `MYSQL_HOST_PORT`
trong `.env` (3307 trên máy phát triển hiện tại); trong Docker vẫn là 3306.

Schema mới dành cho MySQL 8.4. Xem [giải thích từng bảng](database-erd.md).
Thời gian SQL, Backend và session phải dùng UTC; chuyển múi giờ khi hiển thị.

## File và phạm vi sử dụng

- [init.sql](../../infrastructure/mysql/init/init.sql): schema v2 cho database rỗng.
- [mockdata.sql](../../infrastructure/mysql/init/mockdata.sql): dữ liệu local/demo,
  nạp sau schema; không chạy production.
- [z_test_account.sql](../../infrastructure/mysql/init/z_test_account.sql): tài khoản test / 123456789 và dữ liệu riêng; xem [cách nạp](../../infrastructure/mysql/README.md).
- [verify.sql](../../infrastructure/mysql/verify.sql): kiểm tra ràng buộc với seed
  trên database thử nghiệm chỉ nạp init.sql + mockdata.sql (không nạp z_test_account.sql vì kiểm tra số lượng fixture); có INSERT/UPDATE trong transaction rồi rollback,
  tạo và xóa một stored procedure kiểm thử.

Docker Compose hiện mount toàn bộ thư mục init. MySQL chạy SQL theo tên khi
data directory rỗng: init.sql trước mockdata.sql, sau cùng z_test_account.sql. Đây là bootstrap development.
Backend hiện có [Flyway V1/V2](../../backend/src/main/resources/db/migration):
V1 tạo schema trên database rỗng, V2 thêm unique hash cho mã claim.
Database đã bootstrap v2 có thể baseline ở version 1 rồi chạy V2; baseline
không chuyển mô hình dữ liệu cũ sang v2. Xem [bàn giao Người 2](../05-backend/door-device-handoff.md).

## Kiểm thử độc lập bằng Docker

Chạy các lệnh từ root repo khi Docker Desktop đang hoạt động. Container này
tách khỏi dịch vụ MySQL và volume hiện tại, dùng password chỉ dành cho test:

```powershell
docker run --name smartdoor-db-v2-check -d -e MYSQL_ROOT_PASSWORD=local-test-only -e MYSQL_DATABASE=smartdoor_check mysql:8.4
docker exec smartdoor-db-v2-check mysqladmin ping -uroot -plocal-test-only
```

Chờ ping thành công, rồi nạp lần lượt; kiểm tra exit code mỗi lệnh trước bước sau:

```powershell
docker cp infrastructure/mysql/init/init.sql smartdoor-db-v2-check:/tmp/init.sql
docker cp infrastructure/mysql/init/mockdata.sql smartdoor-db-v2-check:/tmp/mockdata.sql
docker cp infrastructure/mysql/verify.sql smartdoor-db-v2-check:/tmp/verify.sql
docker exec smartdoor-db-v2-check sh -c 'mysql -uroot -plocal-test-only smartdoor_check < /tmp/init.sql'
docker exec smartdoor-db-v2-check sh -c 'mysql -uroot -plocal-test-only smartdoor_check < /tmp/mockdata.sql'
docker exec smartdoor-db-v2-check sh -c 'mysql -uroot -plocal-test-only smartdoor_check < /tmp/verify.sql'
```

Kết quả cần có PASS. Sau khi kiểm tra có thể xóa đúng container test và volume
ẩn danh của nó bằng `docker rm -f -v smartdoor-db-v2-check`. Lệnh này chỉ dành
cho container thử nghiệm trên; không xóa volume mysql-data của dự án.

## Database cũ đã có dữ liệu

Sửa init.sql không cập nhật database cũ. Không chạy schema mới trực tiếp vào
database hiện có hoặc xóa volume để “nâng cấp”. Trước khi chuyển đổi:

1. Backup và kiểm tra phục hồi trên bản sao.
2. Xác định từng users cũ là Account đăng nhập, Cardholder hoặc cả hai.
   Người có cả hai vai trò cần hai bản ghi với mapping ID riêng.
3. Nhóm xác nhận chủ Door và cửa của từng Device, RFID, PIN, log và cảnh báo.
   Dữ liệu cũ không có các FK này; không tự gán tất cả về Door/Account ID 1.
4. Giải quyết UID, trạng thái INACTIVE mơ hồ, nguồn sự kiện và timezone:
   script cũ đặt +07:00 với DATETIME; dữ liệu cần xác định nguồn múi giờ
   trước chuyển sang UTC, không cộng/trừ giờ mù quáng.
5. Viết migration Flyway có version dựa trên mapping đã xác nhận, kiểm chứng
   số lượng và liên kết lịch sử trên bản sao. Chưa có migration chuyển đổi
   dữ liệu tự động trong lần cập nhật này vì mapping nghiệp vụ chưa tồn tại.
6. Nâng cấp entity/API và kiểm thử cùng schema; baseline chỉ ghi version,
   không thay thế migration.

Nếu chỉ cần phát triển App có thể khởi tạo một database mới riêng để dùng seed.
Các account demo/PIN cũ dùng hash fixture công khai. Tài khoản test có BCrypt
cho mật khẩu demo 123456789, dùng để kiểm thử đăng nhập qua backend thật.
Không seed mã claim hoặc MQTT credential. [CLI provision](../05-backend/door-device-handoff.md)
hiện cấp mã claim mới; credential/ACL MQTT vẫn cần tích hợp riêng. Seed nhiều
cửa không chứng minh đã kết nối firmware nhiều cửa.

## V8: cấu hình báo cháy và cửa mở lâu

Flyway `V8__door_safety_settings.sql` thêm hai cờ vào `door_configs`:
`fire_alarm_enabled` mặc định true, `door_open_alert_enabled` mặc định false.
Các cửa đã có `open_timeout_seconds` được bật cảnh báo cửa mở khi chuyển đổi.
CHECK yêu cầu timeout khi bật cảnh báo. `doors.operating_mode` tiếp tục giữ chế độ bảo mật/tự động.
Migration tăng version cấu hình để ACK phiên bản cũ không bị hiển thị là đã áp dụng các cờ mới.
Sau nâng cấp, lưu cấu hình hoặc dùng **Yêu cầu đồng bộ lại** để đưa snapshot mới vào hàng đợi.
Không sửa bootstrap V1/init.sql: database Docker bootstrap được baseline và chạy các migration tiếp theo.
Khởi động backend mới bằng `docker compose up -d --build --no-deps backend` để Flyway nâng cấp volume hiện có; không xóa volume.

Kiểm thử simulator sau khi cấu hình đã gửi và Node 2 ACTIVE:

```powershell
python scripts/device-simulator.py <private-node2.json> --temperature 55 --duration 30
python scripts/device-simulator.py <private-node2.json> --door-open-seconds 31 --duration 30
python -m unittest discover -s scripts/tests -p 'test_*simulator.py'
```

Với ngưỡng mặc định 50°C và 30 giây, bật hai cờ sẽ tạo HIGH_TEMPERATURE và DOOR_OPEN_TOO_LONG.
Tắt từng cờ rồi gửi lại snapshot: simulator không phát cảnh báo tương ứng.
Xem cảnh báo và thông báo trong App. Đây là cảm biến giả lập, không mở khóa hay bật còi thật.

## V9: lịch sử xác thực và số đo cảm biến

`V9__access_monitoring.sql` thêm `access_logs.cardholder_id` (FK giữ hồ sơ người
giữ thẻ), `access_logs.locked_until`, `control_commands.access_method` (mặc định
APP cho lệnh cũ), và bảng `door_telemetry` với số đo, trạng thái quan sát, thời
điểm nguồn/nhận cùng FK Door/Device. Hàng đợi bổ sung trạng thái EXPIRED cho
kết quả/còi đã hết hạn, không coi thông báo chưa gửi là SENT. Không xóa hoặc
ghi lại lịch sử hiện có.
Flyway áp dụng khi Backend mới khởi động; không chạy lại init.sql trên DB cũ.
Kiểm tra: `python scripts/db-check.py --expect-version 9`.
Xem [hướng dẫn truy cập/giám sát](../06-frontend/access-monitoring.md).
