---
title: Nền tảng bảo mật Backend
type: reference
version: 1.0
lang: vi
tags: [backend, security, spring-security, cors, bcrypt]
updated: 2026-10-14
---

# Nền tảng bảo mật Backend

## 1. Phạm vi đã triển khai

Backend có lớp bảo mật nền và đã bổ sung phát hành/xác minh JWT cho đăng nhập.
Chỉ API đăng nhập được public thêm; API khác yêu cầu xác thực. Chưa triển khai
kiểm tra quyền sở hữu Door. Source đăng nhập đã qua Maven verify ngày 14/10/2026;
xem [triển khai đăng nhập](account-login.md).

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
| `POST /api/v1/auth/login` | Public; validate credential và rate limit. |
| `POST /api/v1/auth/register` | Public; validate đăng ký, lưu BCrypt và kiểm tra UNIQUE username. |
| `GET /api/v1/accounts/me` | JWT hợp lệ, Account ACTIVE; chỉ trả hồ sơ của chính mình. |
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

## 7. Persistence Account và nội dung chưa triển khai

Đã có `identity/entity/Account` với trạng thái `ACTIVE/DISABLED` và
`AccountRepository` với truy vấn theo username. Đây là mapping persistence;
đã có service đăng nhập và kiểm tra trạng thái Account khi xác thực JWT.
Xem [mapping JPA và repository](entity-repository-mapping.md).

Các nội dung chưa triển khai:

- refresh token, revoke phiên và rotation nhiều khóa (JWT hiện dùng HS256,
  access token 15 phút, hết hạn đăng nhập lại);
- ownership authorization service;
- rate limit chia sẻ nhiều instance, replay protection và audit security
  (đã có limiter in-memory cho đăng nhập);
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
