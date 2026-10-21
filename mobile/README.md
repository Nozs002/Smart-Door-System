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

Form gọi REST API thật qua AuthSession, lưu JWT trong Android secure storage.
Đăng nhập thành công mở dashboard theo mockup. Avatar góc trái mở hồ sơ;
nút **Quản lý cửa** mở danh sách cửa của tài khoản.
Đã bỏ entrypoint và repository preview. Dữ liệu mẫu được nạp vào MySQL.
Xem [dữ liệu test local](../infrastructure/mysql/README.md).

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

### Tích hợp đăng nhập và dữ liệu test

Chỉ chạy entrypoint lib/main.dart. AuthSession cung cấp Bearer token cho
RestDoorRepository; hết hạn token yêu cầu đăng nhập lại. API cửa dùng cùng
API_BASE_URL với API đăng nhập/hồ sơ. Widget test vẫn dùng repository giả
để kiểm thử độc lập, không phải chế độ chạy App.

Tài khoản local: **test / 123456789**. Nạp
[SQL và hướng dẫn](../infrastructure/mysql/README.md) trước khi đăng nhập.
Dữ liệu gồm ba cửa, thiết bị ở nhiều trạng thái và lịch sử lifecycle.
Các màn RFID/log/cảnh báo chưa có trong App; các bản ghi đó chỉ xem trong DB.

Thiết bị Android thật qua USB: chạy adb reverse tcp:8080 tcp:8080, rồi
flutter run -t lib/main.dart -d DEVICE_ID --dart-define=API_BASE_URL=http://127.0.0.1:8080.
Backend phải chạy trên máy ở cổng 8080.

### Backend khi chạy Android

Mặc định API là `http://10.0.2.2:8080` dành cho Android Emulator. Nếu chạy trên
điện thoại thật qua USB, dùng ADB reverse như hướng dẫn bên trên:

```powershell
adb reverse tcp:8080 tcp:8080
flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080
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
test/api_client_test.dart        Bearer, body claim, envelope, lỗi và phân trang
test/door_models_test.dart       Mã kích hoạt, UTC, trạng thái và phân trang
test/widget_test.dart            Các luồng màn hình với repository giả
```

```powershell
flutter analyze --no-pub
flutter test --no-pub
```

## Dashboard theo mockup

Tham chiếu: docs/06-frontend/mockups/dashboard.html, profile.html và style.css.
Flutter dùng widget native, ảnh nền local và ảnh khóa local; không tải avatar/font mạng.
Theme sáng/tối và thẻ bo góc dùng chung cho dashboard, hồ sơ và các trang quản lý cửa.

Dashboard tải tất cả trang cửa thuộc tài khoản, chọn cửa và đọc thiết bị của cửa
qua REST. Mỗi Node hiển thị lifecycle tách khỏi kết nối. Có tải lại, lỗi/thử lại,
trạng thái chưa có cửa và liên kết hồ sơ, quản lý cửa, chi tiết, lịch sử thiết bị.
Đăng xuất/hết phiên xóa các route cần xác thực và trở về đăng nhập.

Dữ liệu nhiệt độ/độ ẩm/trạng thái khóa vật lý/cấu hình chưa có trong contract
hiện tại được ghi Chưa có dữ liệu. RFID, PIN, người truy cập chưa có API được
ghi Chưa khả dụng. Không sao chép số liệu, ảnh avatar, vân tay hoặc lệnh mở
khóa mô phỏng của HTML vào App thật.

Source: lib/features/dashboard/dashboard_screen.dart và lib/core/theme/app_theme.dart.

Thẻ tổng quan cửa dùng một khối cao tối thiểu 240 px, bo góc 28 px, ảnh khóa
bên phải với mask chuyển trong suốt. Tên/lệnh khóa ở trên, chỉ số ở đáy thẻ.
Chưa có API sensor/lệnh khóa nên chỉ số hiển thị — và lệnh ghi Chưa có dữ liệu;
không suy trạng thái khóa từ lifecycle. Thẻ tự tăng chiều cao khi chữ lớn/tên dài.

Phần còn lại của dashboard bám bố cục dashboard.html: giới hạn chiều rộng 430 px,
lề 16 px, nút tròn hồ sơ/thông báo/theme; bộ chọn cửa kính mờ, thanh thiết bị
bo tròn, nhóm truy cập lưới 2×2 và bảng cấu hình có các công tắc chưa khả dụng.
Bộ chọn và bảng cấu hình dùng blur 15, nền trắng 70% (tối: đen 50%).
Thanh thiết bị dùng số lượng/trạng thái thật; danh sách Node nằm trong mục
Trạng thái thiết bị có thể mở rộng. Ô Lịch sử thiết bị mở lịch sử cửa đang chọn.
Ô Người giữ thẻ thay phần quản lý người dùng cũ; không triển khai vân tay.
Các mục chưa khả dụng mở thông báo; công tắc cấu hình bị vô hiệu hóa, không
gửi lệnh hoặc giả lập lưu. Cài đặt cửa mở thông tin/các thiết bị của cửa hiện tại.
