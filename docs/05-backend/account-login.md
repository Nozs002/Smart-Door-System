# Triển khai đăng nhập Backend

Updated: 2026-10-14. Backend đăng nhập đã qua Maven `verify` trong worktree này.

Contract: [Đăng nhập v1](../../contracts/api/login.md).

`identity/controller/LoginController` nhận DTO đã validate; limiter kiểm tra
username/IP trước khi service đọc Account. `LoginService` dùng BCrypt, thực
hiện so khớp dummy hash cho username không tồn tại và chỉ cấp JWT cho ACTIVE.
Response chỉ chứa DTO an toàn; token không được cache hoặc ghi log.

`JwtConfiguration` cung cấp Nimbus encoder/decoder HS256. Security resource
server xác minh chữ ký, issuer, audience, thời gian và bắt buộc sub/iat/exp;
`AccountJwtAuthenticationConverter` kiểm tra Account ACTIVE ở mỗi request.
Principal name là Account ID; không lấy quyền từ body hay token claim scope.
Ownership service chưa triển khai: module nghiệp vụ vẫn phải kiểm tra chủ cửa.

JWT dùng `app.jwt.*`/`JWT_*` hiện có; TTL mặc định 15 phút, không refresh/revoke.
Secret cần ngẫu nhiên và đủ mạnh, tối thiểu 32 byte UTF-8, cấp qua môi trường.
Limiter cấu hình bằng `app.login-rate-limit.username-attempts` (5),
`ip-attempts` (30), `window` (60s), `max-keys` (10000). Cửa sổ cố định bắt đầu
từ lần thử đầu, tính cả lần thành công. IP là `request.getRemoteAddr()`.
Limiter có giới hạn bộ nhớ nhưng chưa chia sẻ giữa instance; xem contract
cho giới hạn proxy, alias username và restart. Audit persistence chưa triển khai.

Test mới: `identity/LoginTests` kiểm tra credential, expiry/signature/issuer/
audience, vô hiệu hóa Account, rate limit và giới hạn byte mật khẩu.
`identity/LoginControllerTests` kiểm tra response, validation UTF-8 và lỗi 429.
`identity/LoginSecurityTests` dùng filter chain thật để kiểm tra route public,
Bearer token và Account bị vô hiệu hóa. Các test mới cần chạy cùng toàn bộ test
nền; cần kiểm chứng MySQL trước khi tích hợp.

Lệnh kiểm tra:

```powershell
cd backend
.\mvnw.cmd verify
```

Maven sẽ cần tải dependency resource server mới. Kiểm chứng trên MySQL v2
để xác nhận collation username và Hibernate validate; H2 không thay thế bước này.

## Bằng chứng xác minh ngày 14/10/2026

`mvnw.cmd verify`: BUILD SUCCESS, 25 test, 0 failure, 0 error, 0 skipped.
Trong đó 11 test thuộc đăng nhập: 6 service/JWT/limiter, 3 controller và 2
security với filter chain thật. JAR executable đã được tạo. Chạy bằng Java
25.0.2, biên dịch với release 21; chưa xác minh runtime bằng JDK 21 hoặc
đăng nhập trên MySQL thật. Không thay đổi mockup hoặc Flutter trong bước này.

## Gọi API

Khởi động Backend profile local với database schema v2 đã khởi tạo. Dùng
Account ACTIVE có password_hash BCrypt hợp lệ; có thể tạo Account qua
[API đăng ký mới](account-registration-profile.md). Không đưa thông tin thật
vào ví dụ hoặc Git. Bằng chứng verify đăng nhập ở trên có trước thay đổi đăng ký.

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"<username>","password":"<password>"}
```

Lấy `data.accessToken` từ response 200, gửi các request được bảo vệ với
`Authorization: Bearer <accessToken>`. Response lỗi 401 do token không hợp lệ
hoặc hết hạn dùng `AUTHENTICATION_REQUIRED`; sai credential khi login dùng
`INVALID_CREDENTIALS`; 429 dùng `LOGIN_RATE_LIMITED`. Hết hạn yêu cầu đăng nhập
lại. Không gửi Authorization header chứa token cũ khi gọi endpoint login.
