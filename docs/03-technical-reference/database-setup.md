---
title: Khởi tạo và chuyển đổi database
type: technical-reference
lang: vi
updated: 2026-10-16
---

# Khởi tạo và chuyển đổi database

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
