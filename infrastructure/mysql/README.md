# Dữ liệu kiểm thử local

Chỉ dùng trên database development/demo. Tài khoản công khai:
**test / 123456789**, tên hiển thị **Người dùng kiểm thử**.
Mật khẩu lưu bằng BCrypt; App đăng nhập qua REST/JWT thật.

## Nạp SQL

Database mới: từ root worktree, tạo .env theo .env.example, điền cấu hình local,
rồi chạy docker compose up -d mysql phpmyadmin. MySQL tự chạy init.sql,
mockdata.sql và z_test_account.sql theo thứ tự tên khi volume rỗng.

Database development đã có schema: import
[init/z_test_account.sql](init/z_test_account.sql) bằng tab Import của phpMyAdmin,
chọn đúng database. Có thể chạy lại script: giữ dữ liệu/thao tác trên cửa đã có,
khôi phục mật khẩu và tên hiển thị tài khoản test; không xóa account khác.
Nếu username test đã tồn tại, script dùng account đó và đặt lại mật khẩu demo.
Không chạy init.sql lên database đã có dữ liệu; không xóa volume để nạp seed.

## Dữ liệu tài khoản test

| Dữ liệu | Nội dung |
| --- | --- |
| Hồ sơ | test, Người dùng kiểm thử, ACTIVE |
| Cửa chính | ACTIVE, Tầng 1 · Nhà mẫu, 2 Node ACTIVE, 1 RETIRED, 1 dự phòng PROVISIONED |
| Cửa kho | SETUP_PENDING, Tầng 1 · Kho mẫu, 2 Node PROVISIONED |
| Cửa sân | DISABLED, Sân sau · Nhà mẫu, 2 Node DISABLED |
| Lịch sử | 8 sự kiện lifecycle, có lý do mẫu |
| Khác | 1 Cardholder, 1 RFID giả, 1 access log, 1 cảnh báo nhiệt độ |

Các account demo.a/demo.b/demo.empty trong mockdata.sql được giữ để kiểm tra
phân quyền; đây không phải tài khoản đăng nhập của kịch bản này.
Không có heartbeat mẫu: thiết bị hiển thị UNKNOWN, không tự duy trì ONLINE.
Không seed mã claim/PIN/credential MQTT hoặc readiness/ACK. Kích hoạt/thay thế
vẫn có thể bị backend từ chối khi thiếu xác nhận từ firmware.
App hiện hỗ trợ hồ sơ, cửa, thiết bị và lifecycle; RFID/access log/cảnh báo
chỉ có bản ghi database, chưa có màn hình/API hoàn chỉnh.

## Chạy App

Backend phải chạy với cùng database: điền .env rồi chạy
docker compose up -d --build backend (và các dịch vụ phụ thuộc).
Lệnh build backend có chạy Maven; người dùng chạy theo quy ước repo.
phpMyAdmin: http://localhost:8081, server mysql; đăng nhập bằng MYSQL_USER và
MYSQL_PASSWORD từ .env.

Từ thư mục mobile trong worktree account-vm-integration, với điện thoại USB:

    adb reverse tcp:8080 tcp:8080
    flutter run -t lib/main.dart -d DEVICE_ID --dart-define=API_BASE_URL=http://127.0.0.1:8080

Android Emulator dùng mặc định http://10.0.2.2:8080. Đăng nhập test, xem hồ sơ,
mở Quản lý cửa, thử đổi tên/vị trí và xem lịch sử thiết bị.
Dữ liệu được lưu qua backend vào MySQL; đã bỏ chế độ preview trong App.
