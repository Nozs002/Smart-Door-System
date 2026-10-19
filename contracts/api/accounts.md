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
Response đăng ký/hồ sơ đặt `Cache-Control: no-store`. Không cung cấp chỉnh sửa
hồ sơ, đổi mật khẩu, refresh hoặc thu hồi phiên trong contract này.

## Mobile

Giữ thẻ kính mờ và ảnh nền sẵn có. Đăng ký thành công quay về đăng nhập; sau
đăng nhập mở hồ sơ và tải từ API. JWT lưu bằng secure storage Android, không
lưu mật khẩu. Khôi phục token còn hạn khi mở App nhưng vẫn lấy hồ sơ từ server;
401 hoặc hết hạn xóa phiên, yêu cầu đăng nhập lại. Lỗi mạng giữ token để thử lại.
Đăng xuất trong App chỉ xóa token local; token đã phát hành vẫn có hiệu lực tới
khi hết hạn hoặc Account bị vô hiệu hóa, theo contract đăng nhập hiện tại.

Đăng ký public hiện chưa có limiter riêng; triển khai Internet cần bổ sung
chính sách chống lạm dụng đăng ký phù hợp. Không dùng bộ đếm sai RFID/PIN của cửa.
