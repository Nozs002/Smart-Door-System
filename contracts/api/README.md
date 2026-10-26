# REST API Contract

- [Cấu hình, đồng bộ và cảnh báo](settings-alerts.md)

Đặc tả REST đã triển khai cho phần Người 2 nằm tại
[door-device.openapi.yaml](door-device.openapi.yaml) (OpenAPI 3.0.3).
Bao gồm danh sách/sửa/claim Door, thông tin Node, lifecycle, replacement và lịch sử.
Contract identity đã chốt tại [Đăng nhập v1](login.md) và
[Đăng ký và hồ sơ Account v1](accounts.md). Chưa có OpenAPI tổng thể.
Contract Door/Device không bao gồm RFID/PIN hoặc điều khiển; cấu hình, trạng thái
đồng bộ và cảnh báo được mô tả trong contract riêng ở trên.

Contract phải tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../../docs/01-requirements/ownership-device-lifecycle-decisions.md):
Account khách hàng sở hữu nhiều Door; Cardholder không đăng nhập App; kiểm tra
ownership ở mọi thao tác theo cửa; provision nội bộ và claim bằng mã kích hoạt
là hai luồng riêng. Lifecycle thiết bị tách khỏi online/offline; không có API
vân tay hoặc quyền Admin nền tảng. Endpoint/DTO của Người 2 được mô tả trong
contract Door/Device trên; các module còn lại cần đặc tả riêng. API Door/Device
yêu cầu Account đã xác thực. Các contract identity cùng được nêu ở trên.
