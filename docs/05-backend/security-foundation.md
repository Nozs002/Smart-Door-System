---
title: Nền tảng bảo mật Backend
type: reference
version: 1.0
lang: vi
tags: [backend, security, spring-security, cors, bcrypt]
updated: 2026-10-13
---

# Nền tảng bảo mật Backend

## 1. Phạm vi đã triển khai

Backend đã có lớp bảo mật nền để mọi API nghiệp vụ bị từ chối mặc định cho đến
khi cơ chế JWT được chốt và triển khai. Phần này không phát hành hoặc xác minh
JWT và chưa triển khai kiểm tra quyền sở hữu Door.

Các thành phần đã cấu hình:

- session stateless;
- tắt form login, HTTP Basic, logout server-side và request cache;
- tắt CSRF cho REST API stateless dùng bearer token trong tương lai;
- bật CORS theo allowlist từ `AppProperties`;
- chỉ public `OPTIONS` và các health probe tối thiểu;
- mọi endpoint khác yêu cầu authentication;
- bật method security để use case có thể bổ sung kiểm tra quyền;
- cung cấp BCrypt `PasswordEncoder`;
- chuẩn hóa lỗi Security `401/403` qua cùng Problem Details của API.

## 2. Luồng request

```text
Request
  -> CorrelationIdFilter
  -> Spring Security filter chain
       -> public health/OPTIONS: cho phép
       -> chưa xác thực: RestAuthenticationEntryPoint
       -> không đủ quyền: RestAccessDeniedHandler
       -> hợp lệ: controller/use case
  -> GlobalExceptionHandler khi có lỗi 401/403
  -> Problem Details có correlationId
```

Hai security handler ủy quyền exception cho `HandlerExceptionResolver`. Cách
này giữ một nơi duy nhất tạo schema lỗi, không tự serialize JSON riêng trong
security filter.

## 3. Chính sách endpoint hiện tại

| Endpoint/request | Chính sách |
| --- | --- |
| `OPTIONS /**` | Public để xử lý CORS preflight. |
| `/actuator/health` | Public, không hiển thị chi tiết. |
| `/actuator/health/liveness` | Public. |
| `/actuator/health/readiness` | Public. |
| `/actuator/info` | Yêu cầu authentication dù được Actuator expose. |
| Mọi endpoint khác | Yêu cầu authentication. |

Khi bổ sung đăng ký/đăng nhập, chỉ endpoint được contract phê duyệt mới được
thêm vào allowlist.

## 4. CORS

Danh sách origin được đọc từ `app.cors.allowed-origins`. Các method được phép:
`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.

Header request được phép gồm `Authorization`, `Content-Type`, `Accept` và
`X-Correlation-ID`. Response expose `X-Correlation-ID`. Production không được
cấu hình wildcard vì credential được cho phép.

## 5. Mật khẩu, PIN và mã kích hoạt

Bean `PasswordEncoder` dùng BCrypt. Application service phù hợp phải dùng bean
này; controller không tự băm dữ liệu.

Không log, trả response hoặc lưu dạng rõ mật khẩu Account, PIN cửa, activation
code, JWT hoặc khóa ký. Activation code cũng cần lưu hash, nhưng policy độ dài,
TTL và số lần thử vẫn phải được chốt riêng.

## 6. Ownership theo tài liệu mới

Authentication chỉ xác định Account hiện tại. Authorization nghiệp vụ phải
kiểm tra thêm:

- Account có sở hữu Door qua `owner_account_id` hay không;
- Cardholder có thuộc cùng Account với Door hay không;
- RFIDCredential có đúng Door và Cardholder hay không;
- thiết bị có đúng Door, node role và lifecycle state hay không;
- claim Door có nguyên tử và chống hai Account claim đồng thời hay không.

Không nhận Account ID trong request body làm nguồn quyền. Account phải lấy từ
security context sau khi JWT được xác minh.

## 7. Nội dung chưa triển khai

- thuật toán và khóa ký JWT;
- access/refresh token lifecycle, revoke và rotation;
- JWT authentication filter/converter;
- Account repository và trạng thái Account;
- ownership authorization service;
- rate limit, replay protection và audit security;
- MQTT identity/ACL;
- TLS termination và production secret store.

## 8. Kiểm thử

Đã bổ sung test cho BCrypt, CORS allowlist, việc ủy quyền lỗi `401/403` vào
global handler và Problem Details `401` có correlation ID.

Theo quy ước hiện tại, agent không chạy Maven. Người dùng cần xác minh bằng:

```powershell
cd backend
.\mvnw.cmd test
```

## 9. Tài liệu liên quan

- [Quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md)
- [Cấu trúc xử lý lỗi Backend](error-handling.md)
- [Cấu hình môi trường Backend](environment-configuration.md)
- [Kiến trúc Backend](architecture.md)
