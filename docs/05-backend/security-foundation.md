---
title: Nền tảng bảo mật Backend
type: reference
version: 1.1
lang: vi
tags: [backend, security, spring-security, cors, bcrypt]
updated: 2026-10-16
---

# Nền tảng bảo mật Backend

## 1. Phạm vi đã triển khai

Backend đã có lớp bảo mật nền để mọi API nghiệp vụ bị từ chối mặc định cho đến
khi cơ chế JWT được chốt và triển khai. Phần này không phát hành hoặc xác minh
JWT. Module Người 2 đã bổ sung ownership service, provider lấy Account xác thực
và giới hạn thử mã claim; chúng không thay thế JWT filter của Người 1.

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

Profile kỹ thuật `provision` tắt web application và không khởi tạo filter
chain/REST security handler. Đây là CLI có quyền truy cập môi trường/database,
không phải endpoint công khai hoặc một role Admin của Account.

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

Bean `PasswordEncoder` dùng BCrypt cho mật khẩu/PIN theo module phụ trách;
controller không tự băm dữ liệu. Mã claim ngẫu nhiên dùng SHA-256 chuẩn hóa để
lookup duy nhất, không dùng cùng cách tra cứu với mật khẩu người dùng.

Không log hoặc trả REST response chứa bí mật như mật khẩu, PIN, mã claim,
JWT hoặc khóa ký. Không lưu mật khẩu/PIN/mã claim dạng rõ trong database.
Ngoại lệ có chủ đích là CLI in mã claim gốc đúng một lần khi cấp thành công để
kỹ thuật viên bàn giao; không ghi mã này vào log ứng dụng hoặc DTO của App.
JWT được truyền qua Authorization ở module xác thực; phần Người 2 không tự
phát hành token.

Policy mã claim đã chốt: 8 ký tự chữ/số, loại `0/O/1/I/L`, trình bày
`XXXX-XXXX`, TTL 7 ngày, một lần; cấp lại chỉ cho cửa chưa claim và thu hồi mã
cũ. Claim giới hạn 5 request/phút trên Account và IP transport. Limiter hiện
trong bộ nhớ một instance có giới hạn, chưa phải giải pháp phân tán.

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

`CurrentAccountProvider.requireAccountId()` yêu cầu `Authentication` đã xác
thực, principal triển khai `AccountPrincipal` với `Long accountId()` và Account
còn ACTIVE trong database. Không tự suy ID từ username, header hoặc principal
kiểu bất kỳ. Người 1 cần cung cấp principal đúng kiểu sau khi xác minh token.

`DoorOwnershipService.requireOwner(accountId, doorId)` phục vụ module khác.
Đọc/ghi Device, Door và lịch sử phải kiểm tra Door trước; không thuộc Account
trả 404. Thao tác ghi dùng khóa Door trước Device/mã trong cùng transaction.

Heartbeat không thay thế quyền lifecycle. MQTT adapter tương lai phải xác minh
credential/ACL trước khi gọi service; thiết bị DISABLED/RETIRED không được
xử lý nghiệp vụ chỉ vì vẫn liên lạc. Chưa có readiness/ACK đáng tin cậy thì
activate/reactivate/replace bị từ chối mặc định.

## 7. Nội dung chưa triển khai

- thuật toán và khóa ký JWT;
- access/refresh token lifecycle, revoke và rotation;
- JWT authentication filter/converter;
- các use case Account/đăng nhập và chính sách phiên/token hoàn chỉnh;
- rate limit phân tán, bảo vệ replay ở MQTT và audit security tổng thể;
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

Các test mới cho principal/ownership, claim limiter và lifecycle được liệt kê
ở [bàn giao Người 2](door-device-handoff.md). Chúng cần được chạy và nghiệm thu;
không coi việc thêm test vào source là kết quả pass.

## 9. Tài liệu liên quan

- [Quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md)
- [Cấu trúc xử lý lỗi Backend](error-handling.md)
- [Cấu hình môi trường Backend](environment-configuration.md)
- [Kiến trúc Backend](architecture.md)
- [Bàn giao backend Người 2](door-device-handoff.md)
