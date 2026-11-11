---
title: Thiết lập và phát triển Flutter Android
type: guide
lang: vi
tags: [mobile, frontend, flutter, android]
updated: 2026-09-28
---

# Thiết lập và phát triển Flutter Android

Ứng dụng Mobile của Smart Door được phát triển bằng Flutter và chỉ hỗ trợ
Android trong phạm vi hiện tại. Source code nằm trong thư mục `mobile/`.

## Công cụ cần thiết

- Flutter SDK stable và thư mục `flutter/bin` trong biến môi trường `Path`.
- Android Studio cùng Android SDK, Android SDK Command-line Tools và NDK.
- Android Emulator hoặc thiết bị Android thật.
- Visual Studio Code với extension Flutter và Dart, hoặc IDE hỗ trợ Flutter.

Không cần cài Microsoft Visual Studio nếu chỉ phát triển Android.

## Kiểm tra môi trường

```powershell
flutter doctor -v
flutter doctor --android-licenses
flutter devices
```

Phần Android toolchain trong `flutter doctor -v` phải hợp lệ. Android Emulator
phải xuất hiện trong `flutter devices` trước khi chạy ứng dụng.

## Cài dependency và chạy ứng dụng

```powershell
cd mobile
flutter pub get
flutter run -d <device-id>
```

Khi Backend chạy trên máy phát triển, Android Emulator truy cập máy host qua
địa chỉ `10.0.2.2`; không sử dụng `localhost` từ bên trong emulator.

## Cấu trúc ban đầu

```text
mobile/
├── android/                 Cấu hình và host project Android
├── lib/                     Source code Dart/Flutter
│   ├── core/                Cấu hình, network, storage và theme dùng chung
│   ├── features/            Các tính năng nghiệp vụ
│   ├── shared/              Widget và tiện ích dùng chung
│   └── main.dart            Điểm khởi động ứng dụng
├── test/                    Automated tests
└── pubspec.yaml             Metadata và dependency của ứng dụng
```

Các thư mục `ios/`, `web/`, `windows/`, `linux/` và `macos/` không thuộc phạm
vi dự án và không được tạo hoặc commit nếu chưa có quyết định thay đổi phạm vi.

## Kiểm tra trước commit

```powershell
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
git diff --check
git status --short
```

Không commit `local.properties`, `.dart_tool/`, `build/`, cache Gradle, APK,
token hoặc thông tin bí mật. Các file này đã được cấu hình trong `.gitignore`.
