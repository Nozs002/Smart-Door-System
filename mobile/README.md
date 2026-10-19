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

Đã nối đăng ký, đăng nhập và hồ sơ với Backend. JWT lưu bằng secure storage;
hết hạn yêu cầu đăng nhập lại. Mặc định debug dùng `http://10.0.2.2:8080`.
Xem [hướng dẫn tích hợp Account](../docs/06-frontend/account-registration-profile.md)
để đổi API URL, chạy trên thiết bị thật và kiểm tra luồng.

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

## Quản lý cửa và vòng đời thiết bị — Người 2

Đã có các màn hình danh sách cửa (phân trang), thêm cửa bằng mã kích hoạt,
đổi tên/vị trí, chi tiết cửa với trạng thái riêng từng Node, chi tiết thiết bị,
kích hoạt/vô hiệu hóa/kích hoạt lại/thay thế/ngừng sử dụng và lịch sử lifecycle
(phân trang, lọc theo thiết bị). Trạng thái kết nối và trạng thái vòng đời được
hiển thị riêng. Khi dùng API thật, thay đổi chỉ được hiển thị thành công sau
phản hồi Backend; lỗi điều kiện kích hoạt được hiển thị ngay tại hộp thoại,
không tự bỏ qua.
Kích hoạt/kích hoạt lại chỉ cần xác nhận, không có ô lý do và vẫn ghi lịch sử.
Vô hiệu hóa cho phép nhập lý do tùy chọn; thay thế/ngừng sử dụng bắt buộc lý do.

### Thử giao diện trên Android khi chưa có đăng nhập

Có entrypoint preview riêng dùng `PreviewApp` và `PreviewDoorRepository` trong
`lib/preview`. Dữ liệu nằm trong bộ nhớ điện thoại, không gọi API, không cần
Docker/MySQL/JWT và không tạo hay sửa dữ liệu Backend. Preview chỉ chạy ở chế
độ debug; entrypoint từ chối chạy ở profile/release.

Trong thư mục `mobile`, dừng phiên `flutter run` hiện tại bằng `q`, rồi chạy:

```powershell
flutter devices
flutter run --debug -t lib/main_preview.dart -d DEVICE_ID
```

Thay `DEVICE_ID` bằng ID Android thật từ `flutter devices`. Không cần truyền
`API_BASE_URL` cho preview. Nếu thiếu dependency, chạy `flutter pub get` trước.
Màn hình có banner dữ liệu giả, nút `Đặt lại` và hai công tắc mặc định tắt:

| Điều khiển | Ý nghĩa mô phỏng |
| --- | --- |
| `Sẵn sàng` | Giả lập đã xác minh danh tính Node và đã áp dụng cấu hình/PIN |
| `Thu hồi` | Giả lập quyền Broker của Node cũ đã được thu hồi khi thay Node |
| `Đặt lại` | Xóa thay đổi trong preview, nạp lại dữ liệu mẫu và tắt hai công tắc |

Các công tắc không gửi MQTT, không nhận ACK và không thay đổi policy của
Backend thật. Dữ liệu cũng được nạp lại khi khởi động lại App/hot restart.

Luồng thử bằng tay:

1. Danh sách ban đầu có hai cửa đã claim. Mở cửa ID `1` để xem Node `11`
   `ACTIVE/ONLINE`, Node `12` `ACTIVE/OFFLINE` và Node `13`
   `PROVISIONED/UNKNOWN` chờ thay Node 1. Đây là các trạng thái mẫu cố định,
   không phải heartbeat nhận từ phần cứng.
2. Thêm cửa bằng mã `K7MP-4Q2D`: có cửa thứ ba; nhập lại cùng mã trả lại cùng
   cửa, không tạo trùng. Mã khác bị báo không hợp lệ (lỗi mô phỏng `400`).
   Thử sửa tên/vị trí cửa rồi quay lại danh sách kiểm tra.
3. Cửa ID `2` ban đầu `SETUP_PENDING`, hai Node `PROVISIONED`. Thử kích hoạt
   khi `Sẵn sàng` tắt để xem lỗi, sau đó bật công tắc và kích hoạt lại.
4. Thử vô hiệu hóa Node với lý do tùy chọn, sau đó kích hoạt lại chỉ bằng xác
   nhận và kiểm tra lịch sử lifecycle. Thử ngừng sử dụng trên một Node không
   cần cho bước thay thế bên dưới và nhập lý do bắt buộc.
5. Ở cửa ID `1`, thay Node `11` bằng Node `13`. Thao tác yêu cầu cả `Sẵn sàng`
   và `Thu hồi`; thử thiếu một điều kiện để xem lỗi rồi bật đủ để hoàn tất.
   Kiểm tra Node cũ `RETIRED`, Node mới `ACTIVE` và lịch sử được cập nhật.
6. Bấm `Đặt lại` để quay về dữ liệu ban đầu và thử lại.

Preview giúp kiểm tra form, điều hướng, hiển thị trạng thái và xử lý lỗi.
Nó không chứng minh API, database, ownership, JWT hoặc bảo mật Backend đã đạt.
Muốn quay lại App gọi API thật, dừng preview rồi chạy:

```powershell
flutter run --debug -t lib/main.dart -d DEVICE_ID
```

### Kết nối với đăng nhập của Người 1

Entrypoint bình thường `lib/main.dart` không triển khai đăng nhập và không chứa
token/tài khoản demo. Khi chưa ghép đăng nhập, App bình thường hiển thị yêu cầu
đăng nhập. Đó là trạng thái dự kiến, không phải lỗi Backend. Entrypoint preview
ở trên dùng repository giả riêng, không cấp token hay bỏ qua xác thực của API.
Các widget test cũng dùng `DoorRepository` giả để kiểm thử độc lập.

Người 1 hiện thực `AccessTokenProvider` trong
`lib/core/access_token_provider.dart`, trả về access token hợp lệ (hoặc `null`
khi đã đăng xuất). Việc refresh token, lưu token và điều hướng đăng nhập thuộc
feature tài khoản. Khi ghép App, truyền provider cùng callback vào:

```dart
SmartDoorApp(
  tokenProvider: accountSession,
  onSignInRequested: openLogin,
)
```

`accountSession` và `openLogin` là thành phần do Người 1 cung cấp. Nếu App dùng
Navigator hoặc shell riêng, có thể mở trực tiếp `DoorListScreen` và truyền
`RestDoorRepository(ApiClient(tokenProvider: accountSession))`; shell sở hữu
client cần gọi `close()` khi không còn sử dụng. Provider được đọc lại ở mỗi
request. Không truyền Account ID từ form để giả lập danh tính người đăng nhập.

### Backend khi chạy Android

Mặc định API là `http://10.0.2.2:8080` dành cho Android Emulator. Nếu chạy trên
điện thoại thật, dùng IP LAN của máy chạy Backend:

```powershell
flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080
```

Chỉ debug manifest cho phép HTTP để thử Backend local. Bản release cần địa chỉ
HTTPS. Không đưa token vào `dart-define` hoặc source. Client gửi Bearer token,
đọc envelope `data` của API và giữ nguyên thông báo/mã lỗi ProblemDetail để App
hiển thị điều kiện còn thiếu. Không tự retry các lệnh thay đổi lifecycle khi
timeout; kiểm tra lại trạng thái/lịch sử trước khi người dùng thao tác tiếp.

Backend kiểm tra quyền sở hữu và điều kiện đồng bộ/broker thật. Giai đoạn chưa
có MQTT/ACK, API kích hoạt/thay thế có thể từ chối do chưa sẵn sàng; màn hình
không tự giả định thiết bị đã được xác minh. Node thay thế chỉ chọn từ thiết bị
cùng vai trò, thuộc cửa và đang `PROVISIONED`, đã được kỹ thuật viên đăng ký.
Backend thay thế thành công sẽ ngừng sử dụng Node cũ và kích hoạt Node mới
trong cùng transaction.

### Source và kiểm thử

```text
lib/core/                       Token provider, REST client và lỗi API
lib/features/doors/data/         DTO, repository và REST endpoints
lib/features/doors/presentation/ Danh sách/claim/chi tiết/lifecycle/lịch sử
lib/main_preview.dart           Entrypoint preview chỉ dành cho debug
lib/preview/                    App preview và repository dữ liệu trong bộ nhớ
test/api_client_test.dart        Bearer, body claim, envelope, lỗi và phân trang
test/door_models_test.dart       Mã kích hoạt, UTC, trạng thái và phân trang
test/widget_test.dart            Các luồng màn hình với repository giả
test/preview_app_test.dart       Preview, nhận cửa mẫu, reset và nút Back Android
test/preview_door_repository_test.dart  Lifecycle/dữ liệu giả, retry và lịch sử
```

```powershell
flutter analyze --no-pub
flutter test --no-pub
```

Lượt kiểm tra sau khi thêm preview: Flutter analyze không phát hiện vấn đề;
toàn bộ 31 test đạt, gồm 15 test có sẵn và 16 test mới cho preview. Chưa xác minh
APK preview trên điện thoại thật trong phiên này. Dữ liệu giả không chứng minh
backend/MySQL/JWT/MQTT đã được tích hợp.
