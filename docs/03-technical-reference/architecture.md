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

Cập nhật 16/11/2026: Backend/Flutter có JWT, Door/Device, credentials,
cấu hình/ACK, MQTT có danh tính/ACL, RFID/PIN online, mở/đóng cửa, lịch sử,
telemetry và luồng TECHNICIAN đăng ký/bàn giao thiết bị, CUSTOMER cài Wi-Fi.
Database đến V13; API/MQTT đã được kiểm thử bằng Node giả lập với MySQL và
Mosquitto thật. Firmware hai Node đã có code tích hợp và build thành công.
Phản ứng nhiệt tại chỗ và bộ đệm alert NVS đã có; RFID/PIN offline giữa hai
Node vẫn chưa triển khai đầy đủ. Cần nghiệm thu bản onboarding trên bo thật.

Xem [hồ sơ 12 nhóm sơ đồ V13](system-design-review.md), gồm sơ đồ 6 cảnh báo
và mất kết nối theo code. Chi tiết Backend ở
[kiến trúc Backend](../05-backend/architecture.md); schema tại
[Database ERD](database-erd.md). Compose local dùng HTTP/MQTT TCP; chưa có
phương án TLS vận hành được triển khai.
