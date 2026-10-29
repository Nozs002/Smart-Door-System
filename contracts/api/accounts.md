# Đăng ký và hồ sơ Account v1

Chốt ngày 14/10/2026. JSON thành công dùng `ApiResponse<T>`; lỗi dùng Problem
Details theo [quy ước Backend](../../docs/05-backend/api-response.md).

## Đăng ký

`POST /api/v1/auth/register` — public, `Content-Type: application/json`.

```json
{"displayName":"Khách hàng demo","username":"customer.demo","password":"<password>"}
```

- Chỉ nhận `displayName`, `username`, `password`; không cho client chọn ID,
  trạng thái, quyền, thời điểm tạo hoặc cửa sở hữu.
- Tên hiển thị và username không chỉ gồm khoảng trắng, tối đa 100 ký tự.
  Giữ nguyên giá trị, không tự trim/đổi hoa thường. Uniqueness username theo
  collation MySQL `utf8mb4_unicode_ci`, kể cả khi hai request đến đồng thời.
- Mật khẩu giữ nguyên, tối thiểu 8 Unicode code point, tối đa 72 byte UTF-8.
  Không cắt ngắn; lưu BCrypt. Không yêu cầu kiểu ký tự đặc biệt trong phiên bản này.
- Tạo Account `ACTIVE`, thời điểm tạo UTC; không tự tạo Cardholder hoặc Door.
- `201 Created`: `data` là hồ sơ Account dưới đây. Không cấp token; cần đăng nhập.
- `400 INVALID_REQUEST/VALIDATION_FAILED`: đầu vào không hợp lệ.
- `409 CONFLICT`: username đã được sử dụng. Không trả hash hay dữ liệu Account cũ.
- `500 INTERNAL_ERROR`: lỗi hệ thống an toàn. Không tự retry đăng ký ở client
  khi timeout vì request trước có thể đã được xử lý.

## Hồ sơ của chính mình

`GET /api/v1/accounts/me` — yêu cầu `Authorization: Bearer <accessToken>`.

`200 OK`, `data` chỉ gồm:

```json
{"id":1,"username":"customer.demo","displayName":"Khách hàng demo","status":"ACTIVE","createdAt":"2026-10-14T00:00:00Z"}
```

Lấy Account ID từ JWT đã xác minh. Không nhận ID từ body/query để chọn Account;
không cung cấp endpoint xem Account khác. Tài khoản mất hoặc `DISABLED`, token
sai/hết hạn trả `401 AUTHENTICATION_REQUIRED`. `createdAt` là ISO-8601 UTC.
Response đăng ký/hồ sơ đặt `Cache-Control: no-store`. Chỉnh sửa hồ sơ và refresh
chưa nằm trong phạm vi hiện tại. Đổi mật khẩu và thu hồi phiên được bổ sung bên dưới.

## Đổi mật khẩu — bổ sung 28/10/2026

`POST /api/v1/accounts/me/password`, Bearer token hợp lệ:

```json
{"currentPassword":"<mật khẩu hiện tại>","newPassword":"<mật khẩu mới>"}
```

- Kiểm tra mật khẩu hiện tại; mật khẩu mới khác mật khẩu cũ, ít nhất 8 Unicode
  code point và tối đa 72 byte UTF-8. Không trim hoặc cắt mật khẩu.
- HTTP 200: `data: {"requiresLogin":true}`. Hash BCrypt được thay trong cùng
  transaction với việc tăng `accounts.auth_version`.
- Tất cả JWT phát hành trước lần đổi mật khẩu bị từ chối tại request tiếp theo,
  kể cả JWT trên điện thoại khác. Client xóa phiên và yêu cầu đăng nhập lại.
- HTTP 400 nếu mật khẩu hiện tại sai hoặc đầu vào không hợp lệ; 401 nếu phiên
  không còn hợp lệ. Không trả mật khẩu/hash trong response hoặc log.

## Đăng xuất phía server — bổ sung 28/10/2026

`POST /api/v1/auth/logout`, Bearer token, không cần body. HTTP 200 trả
`data: {"loggedOut":true}`. Server lưu `jti` trong `revoked_access_tokens`,
không lưu bearer token. JWT đó bị từ chối ở request tiếp theo; JWT của các
phiên khác còn hợp lệ nếu Account và auth_version chưa thay đổi.

Client xóa secure storage sau khi xác nhận đăng xuất, hoặc khi server trả 401.
Nếu mất mạng, không được báo token đã được server thu hồi. Bản ghi thu hồi có
hạn đến `exp`; tác vụ dọn định kỳ xóa các bản ghi đã hết hạn.

JWT mới có claim `authVersion`. Sau triển khai V4, token cũ thiếu claim này
cần đăng nhập lại. TTL vẫn mặc định 15 phút; không bổ sung refresh token.

## Mobile

Giữ thẻ kính mờ và ảnh nền sẵn có. Đăng ký thành công quay về đăng nhập; sau
đăng nhập mở hồ sơ và tải từ API. JWT lưu bằng secure storage Android, không
lưu mật khẩu. Khôi phục token còn hạn khi mở App nhưng vẫn lấy hồ sơ từ server;
401 hoặc hết hạn xóa phiên, yêu cầu đăng nhập lại. Lỗi mạng giữ token để thử lại.
Đăng xuất gọi endpoint server bên trên và xóa phiên local. Đổi mật khẩu thành
công cũng đưa App về đăng nhập. JWT không được lưu dưới dạng plaintext.

Đăng ký public hiện chưa có limiter riêng; triển khai Internet cần bổ sung
chính sách chống lạm dụng đăng ký phù hợp. Không dùng bộ đếm sai RFID/PIN của cửa.
