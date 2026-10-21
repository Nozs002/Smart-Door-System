# Contract đăng nhập v1

Đã chốt ngày 14/10/2026 cho bước triển khai đăng nhập Backend.

- `POST /api/v1/auth/login`, public, JSON gồm `username` và `password`.
- Username không rỗng, tối đa 100 ký tự; không trim/đổi hoa thường, tra cứu theo
  collation database hiện tại (`utf8mb4_unicode_ci` trên MySQL).
- Mật khẩu không rỗng, giữ nguyên, tối đa 72 byte UTF-8; không tự cắt ngắn.
- HTTP 200: `ApiResponse` với `data` gồm `accessToken`, `tokenType` (`Bearer`),
  `expiresIn` (giây) và `account` gồm `id`, `username`, `displayName`.
- HTTP 400: `INVALID_REQUEST` hoặc `VALIDATION_FAILED`; HTTP 401:
  `INVALID_CREDENTIALS` chung cho sai mật khẩu, không tồn tại hoặc DISABLED.
- HTTP 429: `LOGIN_RATE_LIMITED`; HTTP 500: lỗi hệ thống chung.
- Lỗi dùng Problem Details theo tài liệu Backend; response login không cache.
- JWT HS256, secret UTF-8 từ cấu hình môi trường, tối thiểu 32 byte; claims
  `sub` là Account ID, `iss`, `aud`, `iat`, `exp`, `jti`. Không chứa password/PII.
- Access token mặc định 15 phút, không refresh; hết hạn cần đăng nhập lại.
  Xác minh chữ ký, issuer, audience, exp/nbf với clock skew 0; mỗi request
  xác thực phải kiểm tra Account còn ACTIVE. Token không cấp quyền sở hữu cửa.
- Nhiều access token được phép đồng thời. Logout/đổi mật khẩu chưa triển khai;
  chưa có thu hồi token theo phiên. Thay khóa ký làm mất hiệu lực token cũ;
  chưa hỗ trợ rotation nhiều khóa.
- Rate limit tính mọi lần thử hợp lệ về cấu trúc theo username và IP kết nối
  trực tiếp, không tin X-Forwarded-For. Mặc định 5 lần/username và 30 lần/IP
  trong 60 giây; cấu hình qua `app.login-rate-limit.*`.
- Bộ đếm in-memory, giới hạn 10.000 khóa; khi đầy từ chối khóa mới bằng 429.
  Username được gom về chữ thường/bỏ dấu cho bộ đếm (không thay đổi lookup).
  Collation MySQL có thể có alias khác; IP limit là lớp bổ sung. Restart xóa
  bộ đếm; nhiều instance chưa chia sẻ bộ đếm. Production nhiều instance cần
  shared limiter/gateway; proxy làm các request có thể cùng IP.

Đăng ký, refresh, logout và đổi mật khẩu nằm ngoài contract lần này.
