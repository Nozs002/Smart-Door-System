# REST API Contract

Đặt đặc tả `openapi.yaml` tại đây sau khi chốt API contract.

Contract phải tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../../docs/01-requirements/ownership-device-lifecycle-decisions.md):
Account khách hàng sở hữu nhiều Door; Cardholder không đăng nhập App; kiểm tra
ownership ở mọi thao tác theo cửa; provision nội bộ và claim bằng mã kích hoạt
là hai luồng riêng. Lifecycle thiết bị tách khỏi online/offline; không có API
vân tay hoặc quyền Admin nền tảng. Endpoint, DTO và error code chưa được chốt.
