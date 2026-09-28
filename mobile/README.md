# Mobile App

Ứng dụng Android viết bằng Flutter cho hệ thống Smart Door, bao gồm đăng nhập,
quản lý thiết bị, điều khiển cửa, xem lịch sử và nhận cảnh báo.

Project chỉ duy trì Android. Các platform iOS, Web và Desktop không thuộc phạm
vi hiện tại.

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
