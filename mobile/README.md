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
đầu vào được kiểm tra lại ở Backend. Mật khẩu mới cần ít nhất 8 ký tự Unicode
và tối đa 72 byte UTF-8.

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
Đã có màn người giữ thẻ, RFID theo cửa, PIN theo cửa, cấu hình, đồng bộ,
cảnh báo và hộp thư thông báo. Lịch sử xác thực/điều khiển cửa thuộc Người 3.

Thiết bị Android thật qua USB: chạy adb reverse tcp:8080 tcp:8080, rồi
flutter run -t lib/main.dart -d DEVICE_ID --dart-define=API_BASE_URL=http://127.0.0.1:8080.
Backend phải chạy trên máy ở cổng 8080.

### Backend khi chạy Android

Để test điện thoại Android qua USB trên Windows, bật USB debugging và chấp
nhận thông báo cấp quyền trên điện thoại. Khởi động Backend từ thư mục gốc:

```powershell
docker compose up -d --build
```

Sau đó chạy từ `mobile/`:

```powershell
.\tool\run-usb.ps1
```

Script tự tìm Android SDK, chọn thiết bị, thiết lập `adb reverse tcp:8080
tcp:8080` và chạy Flutter với API `http://127.0.0.1:8080`. Không cần IP Wi-Fi.
Nếu có nhiều thiết bị, truyền `-DeviceId <adb-serial>`. Backend phải khởi động
xong trước khi gọi API. Nếu Backend đang chạy bằng IDE, không chạy thêm
container Backend trên cùng cổng.

`flutter run` thuần không tự thiết lập USB forwarding. Có thể chạy script với
`-SetupOnly`, rồi dùng `flutter run --dart-define=API_BASE_URL=http://127.0.0.1:8080`.
Sau khi rút/cắm lại USB hoặc restart adb, chạy lại script để khôi phục forwarding.

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
hiện tại được ghi Chưa có dữ liệu. Các ô RFID, PIN và người giữ thẻ mở màn hình
quản lý qua API thật. Không sao chép số liệu, ảnh avatar, vân tay hoặc lệnh mở
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
Các mục chưa khả dụng mở thông báo; công tắc minh họa không có trong hợp đồng API
vẫn bị vô hiệu hóa. Cài đặt cửa mở màn hình ngưỡng và đồng bộ của cửa đang chọn.

## Tài khoản và thông tin truy cập — Người 1

- Hồ sơ mở **Đổi mật khẩu**. App gửi mật khẩu hiện tại và mật khẩu mới đến
  Backend, xóa phiên local sau khi Backend xác nhận rồi yêu cầu đăng nhập lại.
  Backend vô hiệu hóa các JWT cũ của tài khoản. Nhập sai mật khẩu hiện tại
  giữ nguyên phiên và hiển thị lỗi.
- **Đăng xuất** gọi API thu hồi JWT hiện tại rồi xóa secure storage. Nếu mất mạng,
  App vẫn đăng xuất local và ghi rõ chưa xác nhận thu hồi trên máy chủ.
- **Người giữ thẻ** trong dashboard hoặc hồ sơ: thêm, đổi tên, vô hiệu hóa/kích hoạt
  lại hồ sơ. Vô hiệu hóa thu hồi các RFID; kích hoạt lại không tự cấp lại thẻ.
  Đây là người được cấp RFID, khác Account dùng đăng nhập.
- Chọn cửa trước khi mở **Thẻ từ**: xem/phân trang, cấp thẻ cho người giữ thẻ
  ACTIVE, đặt ngày hết hạn tùy chọn và xác nhận thu hồi tại đúng cửa.
  UID nhận 8, 14 hoặc 20 ký tự hex; cho phép nhập dấu cách, `:` hoặc `-` và
  chuẩn hóa chữ hoa trước khi gửi.
- **Mã PIN** chỉ đọc trạng thái và phiên bản; không đọc lại PIN cũ. Nhập PIN mới
  4–8 chữ số cùng xác nhận; hai ô PIN được xóa sau khi lưu thành công.
  Cấp/thu hồi thẻ, vô hiệu hóa hồ sơ và đổi PIN cần theo dõi đồng bộ trên Node 1.

Các API dùng Bearer token của Account hiện tại, không nhận accountId nhập tay.
API trả 401 sẽ xóa phiên và đóng các màn hình cần đăng nhập. Lỗi quyền sở hữu,
trùng UID và điều kiện nghiệp vụ được hiển thị từ Backend; không giả thành công.

## Cấu hình, cảnh báo và thông báo — Người 4

**Cài đặt cửa** cho phép sửa số lần sai tối đa, thời gian khóa, ngưỡng nhiệt độ và
thời gian cửa mở quá lâu. **Lưu và gửi đến thiết bị** chỉ xác nhận đã lưu/đưa vào
hàng đợi. Trạng thái cấu hình và PIN/RFID của từng Node, phiên bản cần áp dụng
và phiên bản đã nhận hiển thị riêng. **Yêu cầu đồng bộ lại** gửi lại phiên bản
hiện tại, không tăng phiên bản. Kéo để tải ACK mới nhất. `PENDING`/`UNKNOWN`
không được hiển thị là đã áp dụng.

Nút chuông mở hộp thư thuộc Account. App đọc số chưa xem khi mở dashboard,
sau đó cập nhật mỗi 30 giây khi ứng dụng hoạt động; dừng polling khi vào nền.
Thông báo lưu ở Backend và có phân trang/lọc chưa đọc. Đây là thông báo trong
App; chưa có thông báo hệ điều hành khi App đóng. Mở thông báo đánh dấu `readAt`
và mở chi tiết cảnh báo, không tự đổi trạng thái xử lý cảnh báo.

**Tất cả cảnh báo** có phân trang và lọc Mới/Đã xem/Đã giải quyết. Các cảnh báo
tải qua mục cửa còn được giới hạn theo cửa đó. Chi tiết tải lại trạng thái mới,
hiển thị cửa, loại, mức độ, thời điểm và nhiệt độ/thiết bị nguồn nếu có.
Chủ cửa thao tác **Đánh dấu đã xem** hoặc **Đánh dấu đã giải quyết**; trạng thái
đã giải quyết không được mở lại. ACK cấu hình và sự kiện phần cứng vẫn phải
được Backend nhận từ danh tính thiết bị hợp lệ; App không tự tạo ACK.

### Quy trình thử trên Android

1. Khởi động Backend/MySQL và áp dụng migration theo tài liệu Backend; chạy
   `./tool/run-usb.ps1` trong `mobile/`, hoặc dùng emulator với API mặc định.
2. Đăng ký/đăng nhập, claim một cửa bằng mã provision. Có thể dùng dữ liệu test
   local theo hướng dẫn MySQL khi cần cửa có sẵn.
3. Tạo người giữ thẻ, chọn cửa, cấp UID thử như `A1:B2:C3:D4`; kiểm tra danh sách
   và thu hồi thẻ. Đổi PIN rồi mở Cài đặt cửa để xem phiên bản PIN/RFID tăng.
4. Sửa ngưỡng, lưu và kiểm tra Node còn Đang chờ khi chưa có ACK; thử đồng bộ lại
   và xác nhận phiên bản không tăng. Chỉ sau ACK hợp lệ mới hiện Đã áp dụng.
5. Gửi sự kiện cảnh báo qua bộ kiểm thử tích hợp Backend/thiết bị. Làm mới hộp thư,
   mở thông báo, kiểm tra trạng thái cảnh báo vẫn Mới; sau đó đánh dấu đã xem và
   đã giải quyết bằng nút tương ứng. Không tạo cảnh báo giả trong App.
6. Đổi mật khẩu trong hồ sơ, kiểm tra trở lại đăng nhập, mật khẩu cũ bị từ chối
   và mật khẩu mới đăng nhập được. Đăng xuất để kiểm tra thu hồi JWT hiện tại.

### Source và kiểm thử bổ sung

```text
lib/features/credentials/                  Cardholder, RFID theo cửa, PIN an toàn
lib/features/settings/                     Cấu hình và trạng thái đồng bộ/retry
lib/features/alerts/                       Danh sách/lọc/chi tiết/xử lý cảnh báo
lib/features/notifications/                Hộp thư và đánh dấu đã đọc
lib/features/authentication/password_change_screen.dart
test/person1_person4_api_test.dart          HTTP thật trên loopback: hợp đồng API
test/credentials_widget_test.dart          Form người giữ thẻ, RFID, PIN, mật khẩu
test/settings_alerts_widget_test.dart      Retry/phiên bản, phân trang, read và resolve
test/auth_session_test.dart                Thu hồi phiên, xóa token và đổi mật khẩu
```

Widget test dùng repository giả trong `test/` để kiểm tra độc lập. App chỉ chạy
`lib/main.dart`, gọi REST thật và lưu JWT bằng Android secure storage; không có
entrypoint preview hay tài khoản/mã cửa hardcode trong các màn mới.
