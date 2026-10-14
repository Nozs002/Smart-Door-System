# Mobile App

Ứng dụng Android viết bằng Flutter cho hệ thống Smart Door, bao gồm đăng nhập,
quản lý thiết bị, điều khiển cửa, xem lịch sử và nhận cảnh báo.

Project chỉ duy trì Android. Các platform iOS, Web và Desktop không thuộc phạm
vi hiện tại.

## Giao diện đăng nhập và đăng ký

App mở màn hình đăng nhập; liên kết Đăng ký mở form tên hiển thị, username,
mật khẩu và xác nhận. Hai màn hình dùng widget Flutter native, ảnh nền local
và BackdropFilter blur 15, thẻ trắng 70%, bo góc 36 theo mockup hiện có.
Không dùng WebView, font/avatar mạng hoặc thư viện UI bổ sung.

Source thuộc `lib/features/authentication/`: `login_screen.dart`,
`register_screen.dart` và `auth_widgets.dart`. Mật khẩu được che, có nút
hiện/ẩn; form hỗ trợ loading, thông báo lỗi, chặn submit lặp và cuộn khi bàn
phím mở. Login giữ nguyên username/password và kiểm tra tối đa 72 byte UTF-8
theo contract. Registration chỉ kiểm tra đầu vào cơ bản và xác nhận khớp;
policy đầy đủ phải chốt cùng API đăng ký.

Đây là bước giao diện, **chưa gọi API hoặc lưu JWT**. Các callback `onSubmit`
cho phép adapter API nối vào sau. Khi chưa có adapter, form báo chưa khả dụng,
không chuyển dashboard hoặc thông báo đăng ký thành công giả. Adapter có thể
trả `AuthFormException` với thông báo an toàn; exception khác chỉ hiện lỗi
chung. Không log hoặc lưu mật khẩu. Mockup HTML giữ nguyên trong bước này.

Đã bổ sung widget test cho điều hướng, validation, chặn submit lặp, lỗi và
màn hình hẹp/chữ lớn/bàn phím. Dart formatter đã chạy; Flutter analyze/test/APK
chưa chạy được trong worktree vì dependency chưa tải và quyền cache SDK chưa
được cấp. Cần chạy các lệnh kiểm tra bên dưới trước khi tích hợp.

## Yêu cầu

- Flutter SDK stable.
- Android Studio cùng Android SDK, Android SDK Command-line Tools và NDK.
- Android Emulator hoặc thiết bị Android thật.

Kiểm tra môi trường:

```powershell
flutter doctor -v
flutter devices
```

## Chạy ứng dụng

```powershell
flutter pub get
flutter run -d <device-id>
```

## Kiểm tra trước commit

```powershell
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

Xem hướng dẫn chi tiết tại
[`docs/06-frontend/android-development.md`](../docs/06-frontend/android-development.md).
