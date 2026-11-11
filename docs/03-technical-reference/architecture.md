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

Các nội dung này là kiến trúc mục tiêu, chưa xác nhận đã triển khai. Chi tiết
Backend ở [kiến trúc Backend](../05-backend/architecture.md); schema hiện tại và
chênh lệch ở [Database ERD](database-erd.md).
