---
title: Đăng ký và hồ sơ Account
type: reference
lang: vi
tags: [identity, registration, profile, mobile]
updated: 2026-10-19
---

# Đăng ký và hồ sơ Account

Đã bổ sung source Backend và tích hợp Flutter theo
[contract Account v1](../../contracts/api/accounts.md). Thay đổi mới chưa chạy
Maven; bằng chứng 25 test đăng nhập trước đó không xác minh tính năng mới.

Đã biên dịch riêng source và test Java mới bằng `javac --release 21` với
dependency có sẵn từ classpath lần verify trước: thành công. Đây chỉ là kiểm
tra biên dịch, không phải kết quả chạy test hoặc Maven verify.

Ngày 19/10/2026, API đã được chạy với Spring Boot/MySQL local: health check
trả `UP`; một tài khoản kiểm thử được đăng ký (`201`), đăng nhập (`200`) và
lấy hồ sơ qua Bearer JWT (`200`). Kiểm tra này không thay thế `mvnw verify`.

## Backend

- `POST /api/v1/auth/register`: `RegisterRequest` validate tên/username và mật
  khẩu tối thiểu 8 Unicode code point, tối đa 72 byte UTF-8. `AccountService`
  băm BCrypt, đặt ACTIVE và lưu Account. Không tự tạo Door/Cardholder hoặc JWT.
- `existsByUsername` hỗ trợ phản hồi sớm; UNIQUE `uk_accounts_username` là lớp
  bảo vệ cuối cùng khi đăng ký đồng thời. `saveAndFlush` buộc kiểm tra constraint
  trong transaction. Chỉ lỗi constraint username được đổi thành `409 CONFLICT`;
  lỗi persistence khác không bị báo nhầm là username trùng.
- `GET /api/v1/accounts/me`: principal từ JWT đã xác minh; service đọc Account
  và kiểm tra ACTIVE, trả DTO gồm id/username/displayName/status/createdAt.
  Timestamp chuyển từ LocalDateTime UTC sang Instant; không trả passwordHash.
- Không thay schema hoặc migration. Cần MySQL schema v2 hiện hành; không xóa
  volume để thử tính năng. Contract đăng ký public chưa định nghĩa rate limit
  riêng; cần chốt chống lạm dụng khi triển khai Internet.

## Kiểm thử

`AccountApiTests` dùng filter chain thật và repository H2 để kiểm tra đăng ký,
hash BCrypt, trùng username, validation, đăng nhập và hồ sơ của chính mình,
token thiếu hoặc Account DISABLED. `AccountServiceTests` kiểm tra mapping lỗi
UNIQUE khi request đồng thời và không che lỗi persistence khác.

Người dùng chạy:

```powershell
cd backend
.\mvnw.cmd verify
```

H2 không chứng minh collation MySQL. Cần kiểm tra username khác hoa/thường/dấu
và hai request đồng thời trên MySQL schema v2: một Account được tạo, request
còn lại trả 409; tài khoản gốc không bị sửa.

## Gọi thử

1. Gửi JSON displayName/username/password tới API register, nhận 201.
2. Gọi [đăng nhập](account-login.md), lấy accessToken.
3. Gọi API me với Bearer token, nhận hồ sơ của chính Account.
4. Thử thiếu token, token sai/hết hạn, username trùng và mật khẩu vượt 72 byte.

Xem [tích hợp Flutter](../06-frontend/account-registration-profile.md) cho cấu
hình URL, lưu token và luồng giao diện. Đổi mật khẩu/chỉnh sửa hồ sơ chưa thuộc
phạm vi lần này.
