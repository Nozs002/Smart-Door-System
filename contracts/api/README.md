# REST API Contract

Đặc tả REST đã triển khai cho phần Người 2 nằm tại
[door-device.openapi.yaml](door-device.openapi.yaml) (OpenAPI 3.0.3).
Bao gồm danh sách/sửa/claim Door, thông tin Node, lifecycle, replacement và lịch sử.
Đây không phải contract đầy đủ cho tài khoản, RFID/PIN, điều khiển hoặc cảnh báo.

Contract phải tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../../docs/01-requirements/ownership-device-lifecycle-decisions.md):
Account khách hàng sở hữu nhiều Door; Cardholder không đăng nhập App; kiểm tra
ownership ở mọi thao tác theo cửa; provision nội bộ và claim bằng mã kích hoạt
là hai luồng riêng. Lifecycle thiết bị tách khỏi online/offline; không có API
vân tay hoặc quyền Admin nền tảng. Endpoint/DTO của Người 2 được mô tả trong
contract trên; các module còn lại cần đặc tả riêng. API Người 2 yêu cầu Account
đã xác thực; việc có security scheme trong contract không có nghĩa luồng JWT
của Người 1 đã được triển khai.
