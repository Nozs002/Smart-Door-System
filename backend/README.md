# Backend

Spring Boot REST API, authentication, device lifecycle, access logs, alerts và
MQTT integration. Source đã có nền tảng cấu hình, response/error và security.

Khung tám module nghiệp vụ đặt dưới `src/main/java/com/smartdoor/backend/`:
`identity`, `credentials`, `door`, `device`, `access`, `control`, `settings`,
`alert`. Mỗi module có `controller/service/entity/repository/dto`; các module
nhận MQTT có thêm `mqtt`. Đã có 12 JPA entity/repository theo schema v2;
service/controller đã có cho Account, cửa/thiết bị, credential, cấu hình/cảnh báo,
mở khóa, xác thực RFID/PIN, khóa tạm, access log và telemetry. Xem
[truy cập/giám sát](../contracts/api/access-monitoring.md) và
[mapping JPA và repository](../docs/05-backend/entity-repository-mapping.md).

Xem [cấu trúc module và cách làm song song](../docs/05-backend/module-structure.md)
và [kiến trúc Backend](../docs/05-backend/architecture.md) trước khi thêm chức năng.

## Web kỹ thuật viên

Tại thư mục gốc, chạy hoặc cập nhật backend bằng lệnh sau.

```powershell
docker compose up -d --build backend
```

Mở [web kỹ thuật viên](http://localhost:8080/technician/) và đăng nhập bằng
tài khoản kỹ thuật viên đã cấp cho app. Web dùng cùng API và cơ sở dữ liệu.

- **Kho ESP32** để đăng ký bo, tìm theo mã và mở **Chuẩn bị nạp**. Bạn có thể
  sao chép cấu hình để thay nội dung file `enrollment_config.local.h` hiện có
  hoặc tải file `.h`. Lưu file cạnh sketch trong `firmware/node-auth/` hoặc
  `firmware/node-control/` tùy loại bo. Cấu hình chứa thông tin riêng của bo.
- **Tạo bộ cửa** để chọn hoặc nhập mã hai bo, sau đó gửi mã bàn giao cho chủ cửa.
- **Thay Node** để nhập mã ủy quyền và mã bo mới. Nạp firmware cho bo trước khi
  gắn vào cửa đã bàn giao. Chủ cửa xác nhận hoàn tất trên app Android.
