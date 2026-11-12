# Kiến trúc hệ thống

Kiến trúc mục tiêu gồm Android Mobile App viết bằng Flutter, Spring Boot Backend,
Mosquitto Broker, MySQL và một cặp ESP32 cho mỗi cửa. Demo phần cứng có một cửa;
phần mềm phục vụ nhiều Account và nhiều Door.

Theo [quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md),
Account sở hữu nhiều Door; mỗi Door đã claim có một chủ. Account đăng nhập tách
khỏi Cardholder được cấp RFID. Không có Admin nền tảng hoặc module vân tay.

Quy trình kỹ thuật provision bộ Door/Node và mã kích hoạt; khách hàng claim
trên App. Backend quản lý sở hữu, kiểm tra quyền theo cửa và lịch sử vòng đời
thiết bị. Lifecycle tách khỏi kết nối suy ra từ heartbeat từng Node. Broker và
Backend kiểm tra danh tính/ACL đúng Door/Node trước nhận sự kiện hoặc gửi lệnh.

Cập nhật 06/11/2026: Backend/Flutter đã có JWT, Door/Device, credentials,
cấu hình/ACK, MQTT có danh tính/ACL, xác thực RFID/PIN online, điều khiển cửa,
lịch sử và telemetry. Database đến V10; đã kiểm thử API/MQTT bằng Node giả lập
với MySQL/Mosquitto thật. Firmware hai Node chưa hoàn thiện; offline vẫn thuộc
phạm vi nhưng kênh hai Node/cache/thu hồi chưa được thiết kế đầy đủ.

Xem [hồ sơ sơ đồ V10](system-design-review.md) để phân biệt phần có code và
thiết kế firmware/TBD. Sơ đồ 6 tạm hoãn theo yêu cầu nhóm. Chi tiết Backend ở
[kiến trúc Backend](../05-backend/architecture.md); schema tại
[Database ERD](database-erd.md). Compose local dùng HTTP/MQTT TCP; chưa có
phương án TLS vận hành được triển khai.
