---
title: Đăng ký và hồ sơ trên Flutter Android
type: reference
lang: vi
tags: [flutter, identity, registration, profile]
updated: 2026-10-19
---

# Đăng ký và hồ sơ trên Flutter Android

App giữ `AuthLayout`/`GlassPanel` sẵn có: ảnh nền local, blur 15, thẻ trắng 70%,
viền sáng, bo góc 36. Không sửa HTML mockup hoặc ảnh nền trong bước này.

## Luồng

- Đăng ký: tên hiển thị, username, mật khẩu, xác nhận. Validation theo
  [contract](../../contracts/api/accounts.md), không trim thông tin trước khi gửi.
  201 quay lại đăng nhập; 409 hiển thị username đã dùng. Không tự retry POST.
- Đăng nhập thực qua API hiện có, lưu token và hạn phiên bằng
  `flutter_secure_storage` (Android secure storage); không lưu mật khẩu.
- Hồ sơ lấy từ `GET /api/v1/accounts/me`, có loading, lỗi/thử lại, tải lại và
  đăng xuất local. Không tự dựng thông tin hồ sơ từ JWT.
- Khi mở App, khôi phục token còn hạn rồi lấy hồ sơ từ server. Lỗi mạng giữ
  phiên để retry; 401 hoặc hết hạn xóa phiên và trở về đăng nhập.
- Đăng xuất chỉ xóa token local; chưa có server revoke/refresh. Màn hình đổi
  mật khẩu và sửa hồ sơ chưa triển khai.

## Cấu hình API

URL tập trung tại `core/config/api_config.dart`, thay qua `--dart-define`.
Debug mặc định `http://10.0.2.2:8080` cho Android Emulator. Ví dụ:

```powershell
cd mobile
flutter pub get
flutter run -d <emulator-id> --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Thiết bị thật qua USB:

```powershell
adb reverse tcp:8080 tcp:8080
flutter run -d <device-id> --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

Debug chỉ cho HTTP ở 10.0.2.2/localhost/127.0.0.1; release yêu cầu HTTPS.
Release phải truyền URL backend thật; default release là domain placeholder
không hoạt động `https://api.example.invalid`. Không tắt kiểm tra chứng chỉ.
Android INTERNET được khai báo; backup bị tắt để tránh sao lưu token sang máy
khác. Dependency secure storage được giữ ở dòng 10.x cho SDK Android hiện có.

HTTP client dùng dart:io, không theo redirect, timeout 15 giây, giới hạn body
64 KiB và parse dữ liệu tại biên. Chỉ hiển thị lỗi an toàn theo status; không
đưa chi tiết server hoặc request chứa secret vào giao diện/log.

## Xác minh

```powershell
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --debug
```

Test HTTP bằng local server, session bằng fake secure storage và widget qua
đăng ký → đăng nhập → hồ sơ → đăng xuất; có validation, lỗi mạng và 401.
Fake storage chỉ nằm trong test, App thật không fallback sang lưu plaintext.
Cần chạy trên Android để kiểm tra Keystore, bàn phím, màn hình hẹp, cỡ chữ lớn
và kết nối backend thật.

## Kết quả kiểm tra ngày 19/10/2026

- `flutter analyze`: không có issue.
- `flutter test`: 14 test đạt, gồm test giao diện hẹp/cỡ chữ lớn/bàn phím đã có.
- `flutter build apk --debug`: thành công; APK tại
  `mobile/build/app/outputs/flutter-apk/app-debug.apk` (file build được ignore).
- Đã kiểm tra HTTP với Spring Boot/MySQL thật: `GET /actuator/health` trả `UP`,
  sau đó đăng ký tài khoản mới (`201`), đăng nhập (`200`) và lấy hồ sơ với
  Bearer token (`200`). JWT và mật khẩu không được in vào log kiểm thử.
- Chưa kiểm tra trực quan trên thiết bị/emulator; vẫn cần kiểm tra Keystore,
  bàn phím, màn hình hẹp và cỡ chữ lớn trên Android thật.
