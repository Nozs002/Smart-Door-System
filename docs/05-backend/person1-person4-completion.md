# Bàn giao và kiểm soát phần Người 1, Người 4

Cập nhật: 28/10/2026. Phạm vi theo thống nhất: hoàn thiện Backend và Flutter của
**Người 1, Người 4**. Luồng mở khóa, quyết định một lượt truy cập và firmware
ESP32 thuộc phần khác. Bộ giả lập bên dưới dùng kiểm thử giao tiếp cấu hình và
cảnh báo; không chứng minh khóa vật lý đã hoạt động.

## 1. Đã thêm và thay đổi gì?

| Phần việc | Hành vi trong bản này | Nơi kiểm tra |
| --- | --- | --- |
| Đăng ký, đăng nhập, hồ sơ | Giữ và tích hợp API, JWT, secure storage đã có | `identity`, Flutter `authentication` |
| Đăng xuất | Thu hồi JWT hiện tại ở Backend; App xóa phiên. Các phiên khác vẫn hoạt động | `AccountSessionService`, `AuthSession` |
| Đổi mật khẩu | Kiểm tra mật khẩu cũ, băm mật khẩu mới, tăng `auth_version`; mọi JWT cũ bị từ chối | `POST /accounts/me/password` |
| Cardholder | Danh sách/phân trang, thêm, đổi tên, vô hiệu hóa, kích hoạt lại theo Account | `CredentialsManagementService`, màn Người giữ thẻ |
| RFID | Cấp/thu hồi theo cửa; kiểm tra chủ cửa, Cardholder, UID và thời hạn; không chuyển danh tính lịch sử sang người khác | `CredentialsController`, màn Thẻ từ |
| PIN | Đặt/đổi PIN chung từng cửa; BCrypt; API chỉ trả trạng thái/phiên bản | màn Mã PIN |
| Cấu hình cửa | Đọc/sửa ngưỡng sai, thời gian khóa, nhiệt độ và thời gian mở lâu; tăng phiên bản | `DoorSettingsService`, màn Cài đặt cửa |
| Gửi cấu hình/PIN/RFID | Outbox lưu cùng transaction, gửi đúng Node qua MQTT, retry có giới hạn | `integration_outbox`, `SettingsMqttBridge` |
| Xác nhận áp dụng | ACK được xác thực, kiểm tra vai trò/phiên bản, không cho phiên bản lùi | `SignedDeviceMessageService`, `DeviceSyncAcknowledgementService` |
| Trạng thái đồng bộ | Hiển thị phiên bản mong muốn/đã áp dụng, chờ/chưa biết/đã đồng bộ; nút gửi lại | API `sync-status`, `sync/retry`, `delivery-status` |
| Cảnh báo | Nhận từ Node hợp lệ; kiểm tra vai trò/nhiệt độ; chống trùng; xem/lọc/phân trang/chi tiết | `HardwareAlertService`, Flutter `alerts` |
| Xử lý cảnh báo | Đánh dấu đã xem/giải quyết; không mở lại cảnh báo đã giải quyết; khóa hàng khi cập nhật đồng thời | `AlertService` |
| Thông báo chủ cửa | Inbox bền vững đúng Account, trạng thái đã đọc riêng; App cập nhật mỗi 30 giây khi ở foreground | `owner_notifications`, Flutter `notifications` |
| Danh tính MQTT | CLI cấp riêng từng Node; password/ACL Broker; HMAC thông điệp; chống replay | `infrastructure/mqtt`, `setup-mqtt.py` |
| Kiểm thử không có ESP32 | Giả lập hai Node nhận snapshot, ký ACK/heartbeat và phát cảnh báo thử | `device-simulator.py`, `person14-smoke.py` |

### Quy tắc đang áp dụng

- JWT mặc định **15 phút**, chưa có refresh token; hết hạn đăng nhập lại.
- Đổi mật khẩu làm hết hiệu lực mọi JWT cũ. Đăng xuất chỉ thu hồi JWT đang dùng.
  Danh sách thu hồi được dọn sau thời điểm token hết hạn. JWT phát trước khi có
  `authVersion` mới cũng cần đăng nhập lại sau nâng cấp.
- Mật khẩu theo policy đăng ký hiện có: ít nhất 8 ký tự, tối đa 72 byte UTF-8.
  PIN cửa khác mật khẩu Account, gồm **4–8 chữ số** và không đọc lại PIN cũ qua API.
- UID RFID chuẩn hóa chữ hoa, bỏ dấu cách/`:`/`-`, nhận 4/7/10 byte
  (8/14/20 ký tự hex). Cùng UID được dùng tại nhiều cửa; mỗi cửa có một liên kết
  UID với Cardholder để giữ danh tính lịch sử. Cấp lại sau thu hồi/hết hạn phải
  là chính Cardholder đó.
- **Vô hiệu hóa Cardholder thu hồi các RFID cùng transaction.** Kích hoạt lại
  hồ sơ không tự cấp lại thẻ; chủ cửa chủ động cấp lại. Mỗi cửa liên quan có
  snapshot mới gửi đến Node 1.
- Ngưỡng provision mặc định: sai 3 lần, khóa 60 giây, nhiệt độ 50°C. Đây là
  giá trị cấu hình; phần Người 3/firmware phải thực thi bộ đếm và phản ứng tại chỗ.
- Lưu DB, Broker nhận lệnh và Node áp dụng là ba kết quả khác nhau.
  `SENT` trong outbox nghĩa Broker xác nhận nhận thông điệp QoS 1; `SYNCED` cần
  ACK phiên bản từ Node đã xác thực. ACK không chứng minh trạng thái vật lý cửa.
- Cho phép gửi cấu hình và nhận ACK khi `PROVISIONED`/`DISABLED` sau claim,
  tránh vòng lặp “phải ACTIVE mới ACK nhưng phải ACK mới được ACTIVE”.
  `RETIRED` luôn bị từ chối. Cảnh báo nghiệp vụ vẫn yêu cầu Node `ACTIVE`.
- Gửi lại không tăng phiên bản. Transport đọc lại snapshot mới nhất dưới khóa
  Door, tránh lần retry cũ ghi đè retained message mới.
- Inbox `readAt` độc lập với trạng thái xử lý cảnh báo. Mở thông báo không tự
  giải quyết cảnh báo.

## 2. Các nhóm source cần đọc

1. [`contracts/api/credentials.md`](../../contracts/api/credentials.md),
   [`accounts.md`](../../contracts/api/accounts.md),
   [`login.md`](../../contracts/api/login.md): hợp đồng Người 1.
2. [`contracts/api/settings-alerts.md`](../../contracts/api/settings-alerts.md),
   [`contracts/mqtt/topics.md`](../../contracts/mqtt/topics.md): hợp đồng Người 4
   và định dạng thông điệp thiết bị.
3. `backend/.../identity/service/AccountSessionService.java` và
   `credentials/service/`: phiên, thẻ, PIN và snapshot nội bộ.
4. `backend/.../settings/service/`, `alert/notification/`,
   `infrastructure/outbox/`, `infrastructure/mqtt/`: cấu hình, ACK, cảnh báo,
   inbox và việc giao thông điệp.
5. [`mobile/README.md`](../../mobile/README.md): màn hình và cách chạy Android.
   Các màn mới ở `mobile/lib/features/credentials`, `settings`, `alerts`,
   `notifications`, `authentication/password_change_screen.dart`.
6. `backend/src/test/java/.../credentials`, `identity/AccountSessionApiTests`,
   `settings/SettingsAlertTransactionTests`, `infrastructure/`: kiểm tra phân
   quyền, rollback, đồng thời, retry và chữ ký thiết bị.

Các thay đổi kèm theo: thêm Paho MQTT trong `backend/pom.xml`; profile kỹ thuật
`application-device-security.yml`; thêm biến môi trường MQTT và volume riêng
cho persistence MQTT Backend; Broker tắt anonymous, dùng password file và ACL.
`SecurityConfiguration` cùng các handler bỏ phần Web khi chạy CLI kỹ thuật.
`.gitignore` bỏ qua `.local/` chứa backup và secret test.

## 3. Database được thay đổi như thế nào?

Không sửa lại migration V1/V2/V3 đã phát hành. Dùng các migration tiến lên:

| Migration | Thay đổi |
| --- | --- |
| V3 đã được nhánh cấu hình thêm trước đó | `door_configs.credentials_version`; `alerts.event_id` unique cho event mới |
| V4 `account_credentials` | Thêm `accounts.auth_version`; bảng `revoked_access_tokens` cho logout |
| V5 `reliable_settings_alerts` | Bảng `integration_outbox`, `owner_notifications`; unique `(alerts.id,door_id)` phục vụ FK ghép |
| V6 `device_message_identity` | Bảng `device_transport_credentials`, `processed_device_messages` |

Sau V6 có **17 bảng nghiệp vụ + `flyway_schema_history`**. V4–V6 không xóa bảng,
cột hoặc dữ liệu nghiệp vụ cũ. Inbox có FK ghép đến đúng chủ/cửa/cảnh báo;
outbox có FK ghép đến đúng Node/cửa. Mật khẩu Account và PIN vẫn chỉ lưu hash.
V6 không lưu password MQTT hay signing key rõ; khóa từng Node được suy ra từ
`DEVICE_MASTER_KEY` và credential ID. Phải giữ bản sao riêng của master key;
mất/đổi khóa này cần cấp lại danh tính thiết bị.

Chạy từ root repo khi Docker Desktop đang mở:

```powershell
python scripts/db-check.py --backup
docker compose up -d --build
python scripts/db-check.py --expect-version 6
```

Backup nằm trong `.local/backups/`, không lên Git. Script chỉ đọc khi kiểm tra;
`--backup` ghi thêm file dump, không xóa hay reset volume. Các mục `NOTE`
về seed demo có UID giả/Node chưa ACK không phải bằng chứng thiết bị đã sẵn sàng.
Không dùng `docker compose down -v` để cập nhật database.

## 4. Chạy lần đầu sau khi pull bản này

Mở Docker Desktop. Tại root repo, giữ `.env` riêng của máy:

```powershell
python scripts/setup-mqtt.py init
docker compose up -d --build
docker compose ps
curl.exe http://localhost:8080/actuator/health
python scripts/db-check.py --expect-version 6
```

`init` tạo master key còn thiếu, giữ nguyên secret/ACL đã có và chuẩn bị
password file cho Broker. Không đưa `.env`, `.local` hoặc file cấp khóa Node
lên Git. Local dùng MQTT TCP; môi trường production bật TLS và cấu hình Broker
TLS tương ứng. Backend từ chối MQTT không TLS khi profile `prod` hoạt động.
Nếu cần nhiều instance Backend, mỗi instance có MQTT client ID/persistence
riêng (`MQTT_CLIENT_ID`, `MQTT_PERSISTENCE_DIR`).

MySQL trong Docker luôn cổng 3306; kết nối từ Windows dùng `MYSQL_HOST_PORT`
(máy hiện tại là **3307**). phpMyAdmin: <http://localhost:8081>.
Backend health: <http://localhost:8080/actuator/health>.
MQTT từ Windows/thiết bị vào Docker mặc định cổng **1884** (`MQTT_HOST_PORT`);
Backend trong Docker vẫn dùng `mosquitto:1883`. Máy hiện tại đã có Mosquitto
Windows ở 1883 nên không dùng `localhost:1883` để thử Broker Docker.
Nếu đổi host port, truyền `--port` cho simulator hoặc `--mqtt-port` cho smoke test.

## 5. Test một lệnh, không cần ESP32

### API và database

```powershell
python scripts/person14-smoke.py
```

Script tạo Account và bộ cửa mới tên `E2E-Person14-...`, không đụng mật khẩu
Account đã có. Kiểm tra đăng ký/đăng nhập, claim, cách ly chủ cửa, Cardholder,
cấp/thu hồi/cấp lại RFID, PIN, phiên bản cấu hình, logout và đổi mật khẩu.
Thông tin đăng nhập test cuối cùng nằm trong file `report.local.json` ở thư
mục `.local/smoke/<lần-chạy>/`; token không được ghi vào báo cáo.

### Thêm MQTT, ACK và cảnh báo

```powershell
python -m venv .local/tools
.\.local\tools\Scripts\python.exe -m pip install -r scripts/simulator-requirements.txt
.\.local\tools\Scripts\python.exe scripts/person14-smoke.py --mqtt
```

Script cấp credential cho hai Node mới, cập nhật ACL và restart Broker để áp
dụng danh tính hiện tại. Hai client giả lập nhận snapshot, ACK khi còn
PROVISIONED, sau đó kiểm tra activate, heartbeat, cập nhật nhiều phiên bản,
cảnh báo nhiệt độ, chống trùng, sai vai trò Node và inbox đúng chủ cửa.
Kết thúc script thì client giả lập dừng; Backend/MySQL vẫn chạy. Fixture được
giữ để mở trên App. Chạy nhiều lần sẽ có nhiều fixture; không dùng script này
trên production.

## 6. Test giao diện trên Android

Chạy `mobile/tool/run-usb.ps1` từ `mobile/` theo
[`mobile/README.md`](../../mobile/README.md). Mở báo cáo smoke gần nhất để lấy
username/password test (không gửi file chứa password cho cả nhóm).

1. Đăng nhập, chọn cửa `E2E-Person14-...`. Từ dashboard hoặc chi tiết cửa mở
   Người giữ thẻ, Thẻ từ, PIN và Cài đặt cửa.
2. Thêm người giữ thẻ, cấp UID như `A1:B2:C3:D4`, đổi PIN. Kiểm tra danh sách
   đúng cửa, thông báo lỗi nhập và trạng thái đồng bộ.
3. Muốn thấy ACK cập nhật khi thao tác App, chạy hai cửa sổ terminal với hai
   file credential được liệt kê trong báo cáo:

```powershell
.\.local\tools\Scripts\python.exe scripts/device-simulator.py <file-node1.local.json> --duration 600
.\.local\tools\Scripts\python.exe scripts/device-simulator.py <file-node2.local.json> --duration 600
```

4. Lưu cấu hình/đổi PIN rồi tải lại Cài đặt cửa: desired version tăng, applied
   version theo sau ACK; gửi lại không làm desired version tăng.
5. Dùng Node 2 đã ACTIVE phát một cảnh báo thử:

```powershell
.\.local\tools\Scripts\python.exe scripts/device-simulator.py <file-node2.local.json> --temperature 80 --duration 30
```

6. Mở chuông thông báo. Kiểm tra chi tiết cảnh báo, đánh dấu đã đọc hộp thư,
   đã xem cảnh báo và giải quyết là các thao tác riêng. Thử lọc/phân trang.
7. Vô hiệu hóa Cardholder: RFID bị thu hồi và đồng bộ lại. Kích hoạt lại
   Cardholder không tự trả thẻ thành ACTIVE.
8. Đổi mật khẩu: App quay về đăng nhập, mật khẩu cũ bị từ chối. Đăng xuất rồi
   mở lại App để kiểm tra phiên không được khôi phục.

### 6.1. Kiểm tra lại điều hướng đăng nhập và PIN (29/10/2026)

- Đăng nhập sai → mở Đăng ký → tạo tài khoản thành công hoặc quay lại:
  màn đăng nhập phải xóa lỗi lần trước và thông báo hết phiên cũ, xóa mật khẩu
  đã nhập. Một lần đăng nhập sai tiếp theo vẫn phải hiện lỗi mới.
- Đổi PIN: chỉ khi API lưu thành công mới quay về màn trước và hiện thông báo
  đã lưu. PIN sai định dạng, xác nhận không khớp hoặc API thất bại thì giữ form
  để sửa/thử lại. Mở lại form không hiển thị PIN vừa lưu.
- Sau khi cập nhật source, tại terminal `flutter run` bấm **R** (hot restart),
  rồi thử lại hai luồng trên.

Kiểm tra ngày 29/10/2026: `flutter test --no-pub` **57 bài PASS**;
`flutter analyze --no-pub` không phát hiện vấn đề. Widget test kiểm tra cả
quay lại sau đăng ký, hủy đăng ký, lỗi mới sau đó, PIN xác nhận sai, API từ chối
và quay về màn trước khi lưu PIN thành công.

### 6.2. UID RFID đang nhập từ đâu?

Hiện App hỗ trợ nhập/dán UID, chưa có chức năng đọc thẻ bằng NFC hay lấy UID
tự động từ Node 1. UID test như `11223344` chỉ dùng thử API/giao diện; khi cấp
thẻ thật phải dùng UID đọc từ chính thẻ đó theo cùng thứ tự byte với đầu đọc.

Luồng đề xuất cho phần cứng: chủ cửa chọn cửa/người giữ thẻ → mở phiên thêm
thẻ có thời hạn → quẹt thẻ tại RC522 của Node 1 → Node gửi UID kèm mã phiên →
App hiển thị UID → chủ cửa xác nhận cấp. Cần bổ sung firmware, hợp đồng MQTT,
API và màn chờ đọc; đây chưa phải chức năng đã hoàn thành. Chế độ thêm thẻ
phải tách khỏi yêu cầu mở cửa và chỉ trả UID cho chủ cửa đã mở phiên đó.

Một phương án khác là đọc bằng NFC của điện thoại nếu máy và thẻ tương thích.
Android cung cấp [Tag.getId()](https://developer.android.com/reference/android/nfc/Tag#getId()),
nhưng vẫn cần tích hợp vào App và kiểm tra UID ổn định, khớp với đầu đọc thực tế.

### 6.3. Kích hoạt bộ cửa mới sau khi claim

Backend hiện kiểm tra các điều kiện sau:

- Account đang đăng nhập là chủ cửa; cửa không ở `UNCLAIMED`/`DISABLED`.
- Node đang `PROVISIONED` khi kích hoạt lần đầu hoặc `DISABLED` khi kích hoạt lại;
  chưa có Node khác `ACTIVE` cùng vai trò trong cửa.
- Node có credential chưa bị thu hồi và Backend đã xác minh danh tính qua thông
  điệp ký hợp lệ; đã ACK đúng phiên bản cấu hình hiện tại của cửa.
- Riêng Node 1: cửa có PIN `ACTIVE` và Node đã ACK phiên bản PIN/RFID hiện tại.
  Không bắt buộc phải cấp sẵn một thẻ RFID nếu chỉ thử PIN.

Với cửa vừa provision, quy trình local như sau:

1. Claim trên App rồi lưu PIN của cửa. Lấy `id` của hai Node từ bảng `devices`
   trong phpMyAdmin, đối chiếu `door_id`, `device_code`, `node_type`.
2. Với Node mới chưa được cấp credential, chạy các lệnh sau ở root repository.
   Thay `ID_NODE_1`/`ID_NODE_2` bằng ID thực; mỗi lệnh `issue` in đường dẫn một
   file `.local.json` riêng để giữ lại:

   ```powershell
   python scripts/setup-mqtt.py issue ID_NODE_1
   python scripts/setup-mqtt.py issue ID_NODE_2
   python scripts/setup-mqtt.py configure
   ```

   `issue` trên Node đã có credential sẽ rotate key và xóa bằng chứng ACK cũ.
   Khi chỉ chạy lại bộ test đã chuẩn bị, dùng file credential hiện có.
   `configure` cập nhật ACL và khởi động lại Broker, các client sẽ kết nối lại.
3. Mở hai terminal riêng; mỗi terminal chạy một Node với file do `issue` trả về:

   ```powershell
   .\.local\tools\Scripts\python.exe scripts/device-simulator.py DUONG_DAN_FILE_NODE.local.json --duration 600
   ```

4. Trên App mở Cài đặt cửa, yêu cầu đồng bộ lại nếu cần, tải lại để kiểm tra
   cấu hình hai Node và PIN/RFID Node 1 đã được áp dụng. ACK thiết lập được phép
   nhận khi Node còn `PROVISIONED`, nên không cần kích hoạt trước bước này.
5. Vào chi tiết từng Node → **Kích hoạt thiết bị** → xác nhận. Khi cả hai Node
   sẵn sàng và `ACTIVE`, cửa có thể chuyển sang `ACTIVE`.

`ACTIVE` là trạng thái vòng đời, `ONLINE/OFFLINE/UNKNOWN` là trạng thái kết nối
riêng. Không sửa trực tiếp DB thành `ACTIVE` để thay thế quy trình xác minh.

## 7. Giới hạn phải bàn giao rõ

- Firmware ESP32 cần hiện thực chính xác contract MQTT, xác minh BCrypt PIN,
  kiểm tra thời hạn thẻ, lưu snapshot, ACK, ký HMAC, đồng hồ và phản ứng nhiệt
  độ tại chỗ. Bộ giả lập kiểm tra giao thức, không thay firmware hoặc thử tải khóa.
- Thông báo hiện là **inbox trong App**, polling foreground 30 giây. Chưa có
  FCM/thông báo hệ điều hành khi App đã đóng; muốn thêm cần dự án Firebase và
  cấu hình push riêng.
- Bộ đếm sai/khóa tạm thực tế, mở cửa từ App, MQTT điều khiển/timeout/log lượt
  truy cập vẫn thuộc **Người 3**. Chỉ cung cấp helper kiểm tra credentials và
  cấu hình để họ tích hợp; không báo các chức năng đó đã hoàn thành.
- Thay Node tự động còn cần quy trình xác minh thu hồi Broker credential cũ
  của module Người 2. `canReplace` tiếp tục từ chối khi chưa có bằng chứng đó;
  ghi `RETIRED` trong DB không tự xóa session Broker. `setup-mqtt.py configure`
  xuất lại user hiện tại và restart Broker để áp dụng ACL, không tự chứng nhận
  hoàn thành toàn bộ workflow thay thiết bị.
- Các trạng thái ACTIVE/UID giả của seed demo không phải danh tính firmware
  đã xác thực. Luồng smoke dùng provision/claim thật và credential mới.

## 8. Theo dõi và kiểm tra sau khi sửa tiếp

```powershell
cd backend
.\mvnw.cmd verify
cd ..\mobile
flutter analyze --no-pub
flutter test --no-pub
```

Sau khi API/DB/MQTT đổi, chạy thêm `person14-smoke.py --mqtt` và audit DB.
Sau khi chỉ sửa widget, chạy test liên quan; vẫn cần kiểm tra thủ công trên
Android thật trước khi ghi mục “đã kiểm thử thiết bị thật”.

Kết quả xác minh của lần triển khai này được cập nhật sau lượt chạy cuối tại
cuối tài liệu. Không dùng báo cáo test cũ của nhánh khác để xác nhận code mới.

## 9. Kết quả đã chạy ngày 28/10/2026

| Kiểm tra | Kết quả |
| --- | --- |
| Backend `mvnw.cmd verify` trên source mới | **160 tests PASS**, 0 failure/error/skip, đóng gói JAR thành công |
| Flutter `analyze --no-pub` | Không còn issue |
| Flutter toàn bộ widget/HTTP tests | **55 tests PASS**; 5 test credentials chạy lại sau khi làm rõ nội dung thu hồi thẻ cũng PASS |
| Android `flutter build apk --debug --no-pub` | **Build thành công**: `mobile/build/app/outputs/flutter-apk/app-debug.apk` |
| MySQL đang có dữ liệu | Đã backup, nâng cấp V2 → V6, Hibernate validate thành công; 27 FK và 23 truy vấn bất biến PASS |
| MySQL mới hoàn toàn | `db-fresh-check.py` PASS V1 → V6 và Hibernate validate; 17 bảng nghiệp vụ + Flyway, 27 FK |
| API + hai client MQTT qua Docker Broker | **Smoke PASS**: setup ACK, activate, latest snapshot, cảnh báo, chống trùng, inbox đúng chủ, read/resolve, logout và đổi mật khẩu |
| Xác minh lại bản triển khai cuối | Health `UP`; Node 1 config/credentials `SYNCED`, Node 2 config `SYNCED`; cảnh báo thử mới đã vào DB/inbox |

Log tại `backend/target/person1-person4-verify.log`,
`mobile/test-person1-person4.log`, `mobile/build-person1-person4.log`,
`.local/person14-smoke.log`. Đây là file local được ignore.

Bộ test đã thành công nằm ở `.local/smoke/20261028T013536Z-7e7c32/`:

- `report.local.json`: username/password **sau đổi mật khẩu**, PIN thử, UID,
  Door ID **9** và đường dẫn file credential của Node **20/21**.
- Hai Node được giả lập khi kiểm thử; khi script dừng chúng sẽ dần OFFLINE.
  Muốn thử sửa cấu hình/PIN trực tiếp trên App hãy chạy lại simulator bằng
  hai file credential của lần này.
- Door 9 có cảnh báo đã giải quyết và một cảnh báo ACTIVE mới, một thông báo
  chưa đọc để kiểm tra giao diện. Đây là dữ liệu test, không phải cảnh báo
  từ cảm biến thật.
- Schema mới được giữ riêng tên `p14_schema_check_20261028_014245` để đối chiếu;
  ứng dụng vẫn dùng database ở `MYSQL_DATABASE`, không chuyển sang schema test.

Audit vẫn ghi chú 8 Node ACTIVE của fixture cũ chưa có ACK và 5 UID demo dạng
placeholder không phải UID hex thật. Không tự sửa/xóa lịch sử seed của nhóm;
không dùng các fixture đó để kết luận firmware hoặc đồng bộ đã được xác thực.
Bộ smoke mới tạo danh tính, PIN/RFID và ACK đúng quy trình, tách khỏi seed cũ.

Chưa thực hiện thử cảm biến/khóa trên ESP32 hay kiểm tra thao tác bằng tay trên
điện thoại Android thật trong lượt triển khai này. APK build, widget tests và
client MQTT giả lập không thay thế hai bước kiểm tra phần cứng đó.
