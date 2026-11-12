---
title: Tóm tắt dự án và kịch bản bảo vệ demo
type: guide
lang: vi
updated: 2026-11-11
---

# Tóm tắt dự án và kịch bản bảo vệ demo

Chuẩn bị cho buổi demo ngày 11/11/2026. Nội dung đối chiếu source hiện có trên
máy, schema V12 và [hồ sơ thiết kế](../03-technical-reference/system-design-review.md).
Kết quả đợt 06/11 bên dưới là lịch sử; kết quả sửa sau demo xem
[bàn giao trước onboarding](../05-backend/pre-onboarding-fixes.md). Firmware hai Node
đã có source; kiểm thử phần mềm không thay thế nghiệm thu trên ESP32 thật.

## 1. Lời giới thiệu trong 30–45 giây

> Nhóm em xây dựng hệ thống kiểm soát cửa thông minh sử dụng ESP32, ứng dụng
> Android, Spring Boot, MySQL và MQTT. Ngoài chức năng mở cửa bằng RFID, PIN
> hoặc từ ứng dụng, hệ thống quản lý quyền sở hữu cửa, vòng đời thiết bị,
> cấu hình, trạng thái kết nối, lịch sử truy cập và cảnh báo. Mỗi tài khoản
> quản lý các cửa của mình; một bộ cửa gồm hai Node với nhiệm vụ riêng.
> Phần mềm hỗ trợ nhiều cửa, còn phạm vi sa bàn dự kiến là một cửa.

Nếu trình diễn bằng simulator, nói thêm:

> Bản demo này chạy app Android, backend, MySQL và MQTT Broker thật; hai Node
> được mô phỏng bằng Python để kiểm tra giao tiếp và nghiệp vụ. Phản hồi từ
> Node giả lập chưa chứng minh relay hay khóa vật lý đã hoạt động.

## 2. Kiến trúc cần nhớ

```text
Android App ── REST + JWT ── Backend ── JDBC ── MySQL
                                │
                               MQTT
                                │
                         Mosquitto Broker
                           ↕           ↕
                        Node 1       Node 2
```

| Thành phần | Vai trò | Điều nên nói khi bảo vệ |
| --- | --- | --- |
| Flutter Android | Đăng nhập, quản lý cửa/thẻ/PIN, cấu hình, điều khiển và xem lịch sử | App gọi API; không truy cập trực tiếp DB hoặc Broker |
| Spring Boot / Java 21 | Xác thực, kiểm tra quyền, xử lý nghiệp vụ, giao tiếp MQTT | Backend quyết định quyền truy cập trong luồng online |
| MySQL | Lưu tài khoản, cửa, thiết bị, cấu hình và lịch sử | Dữ liệu tồn tại sau khi app/backend khởi động lại |
| Mosquitto | Chuyển thông điệp giữa backend và Node theo topic | Broker không tự quyết định thẻ/PIN có được mở cửa |
| Node 1, ngoài cửa | RC522, Keypad, LCD I2C 16×2 | Thu thập yêu cầu, gửi xác thực và hiển thị kết quả |
| Node 2, trong cửa | Servo mô hình, DHT11, MC-38, Buzzer, Exit | Thực thi lệnh và báo số đo/sự kiện theo firmware |
| Docker Compose | Chạy backend, MySQL, Mosquitto, phpMyAdmin | App chạy trên điện thoại; firmware nạp vào ESP32 |

Node thật kết nối qua router/Wi-Fi. Local hiện dùng HTTP và MQTT TCP; không
trình bày HTTPS/MQTTS như cấu hình đã được triển khai. Hai sketch tích hợp ở
`firmware/node-auth/` và `firmware/node-control/`; cần kiểm thử trên board thật.

## 3. Mô hình dữ liệu và quyền sở hữu

Nhớ ba quan hệ:

- **Account → Door:** một tài khoản có nhiều cửa. Cửa chưa claim chưa có chủ;
  sau claim có đúng một chủ. API lấy Account từ JWT và kiểm tra chủ cửa.
- **Account → Cardholder:** người giữ thẻ thuộc danh sách của một Account,
  không cần có tài khoản đăng nhập. Bảng dùng chung nhưng dữ liệu được lọc
  theo `account_id`; nhìn thấy toàn bộ bảng trong phpMyAdmin là quyền quản trị DB.
- **Door + Cardholder → RFIDCredential:** thẻ được cấp tại cửa cụ thể cho
  người giữ thẻ thuộc cùng Account. Thu hồi ở cửa A không tự thu hồi tại cửa B.

PIN là mã chung theo Door, khác mật khẩu đăng nhập Account. Device thuộc Door;
mỗi vai trò chỉ có tối đa một Node ACTIVE. Giữ Device cũ để truy vết nên một
cửa có thể có hơn hai bản ghi thiết bị trong lịch sử.

| Nhóm bảng | Nội dung |
| --- | --- |
| `accounts`, `cardholders`, `rfid_credentials`, `system_pins` | Tài khoản và thông tin truy cập |
| `doors`, `devices`, `door_activation_codes`, `device_lifecycle_events` | Cửa, claim và vòng đời Node |
| `door_configs`, `integration_outbox` | Cấu hình mong muốn và hàng đợi giao thông điệp |
| `control_commands`, `access_logs`, `lockout_statuses` | Lệnh, lịch sử quyết định/thực thi và khóa tạm |
| `alerts`, `owner_notifications`, `door_telemetry` | Cảnh báo, inbox và số đo |
| `revoked_access_tokens`, `device_transport_credentials`, `processed_device_messages` | Thu hồi phiên, danh tính Node và chống phát lại |

Schema V12 có **19 bảng nghiệp vụ + bảng lịch sử Flyway, 31 khóa ngoại**.
Không cần học thuộc mọi cột; cần giải thích được khóa ngoại, phạm vi sở hữu
và vì sao giữ lịch sử thay vì xóa thiết bị/thẻ đã từng được sử dụng.

## 4. Năm luồng nghiệp vụ quan trọng

### 4.1. Từ bộ cửa mới đến vận hành

1. Kỹ thuật viên dùng CLI provision Door và hai Node PROVISIONED.
2. Hệ thống cấp mã kích hoạt 8 ký tự, hạn 7 ngày; DB lưu hash SHA-256.
3. Khách hàng đăng nhập và claim bằng mã; backend gán chủ trong transaction.
4. Cấp danh tính MQTT riêng, cấu hình ACL, đồng bộ cấu hình cho hai Node;
   Node 1 nhận thêm PIN/RFID.
5. Node áp dụng và ACK; chủ cửa yêu cầu kích hoạt từng Node.

Claim không tự mở khóa. Kích hoạt cần bằng chứng danh tính/ACK đúng phiên bản;
Node 1 còn cần PIN ACTIVE. Hai tài khoản claim đồng thời không được cùng sở
hữu một cửa. Retry của tài khoản đã claim hợp lệ trả lại cửa cũ.

### 4.2. Mở cửa bằng RFID/PIN online

Người dùng → Node 1 → MQTT → backend kiểm tra đúng cửa, quyền thẻ/PIN và khóa
tạm → tạo lệnh cho Node 2 nếu đủ điều kiện → Node 2 phản hồi → lưu lịch sử.

- Sai: tăng bộ đếm riêng theo Door/phương thức; mặc định 3 lần sai khóa 60 giây,
  nhưng giá trị demo phải đọc từ cấu hình thực tế.
- Đang khóa: tiếp tục từ chối, không tăng bộ đếm thêm cho mỗi lần thử.
- Đúng: reset bộ đếm; có thể `GRANTED` nhưng `CONTROL_UNAVAILABLE` nếu Node 2
  chưa sẵn sàng. Quyền hợp lệ không bảo đảm lệnh đã thực thi.
- Gửi trùng eventId không được tăng bộ đếm hoặc mở khóa lần hai.

### 4.3. Mở cửa từ App

Vuốt mở → gửi API với JWT và requestId → backend kiểm tra chủ cửa, Door ACTIVE,
Node 2 ACTIVE/ONLINE, MQTT sẵn sàng → gửi đúng Node 2 → nhận ACK.

Lệnh có hạn 15 giây, không retained. Kết quả gồm đang chờ, đã gửi, chưa rõ,
thực thi, từ chối, thất bại hoặc hết hạn. Không tự gửi lại thao tác mở khi chưa
biết lần gửi trước đã tác động hay chưa. ACK trễ không đổi timeout thành thành công.

### 4.4. Cấu hình và đồng bộ

App lưu → backend ghi `door_configs` và outbox → gửi MQTT → Node áp dụng →
Node gửi ACK → backend cập nhật phiên bản đã áp dụng → app hiển thị đồng bộ.

**Ba việc khác nhau:** lưu DB, Broker nhận thông điệp, Node xác nhận áp dụng.
`SENT` trong outbox chưa phải đã đồng bộ. So sánh `door_configs.version` với
`devices.applied_config_version`. Không tự sửa cột ACK trong phpMyAdmin để demo
luồng nhận xác nhận; phải chạy Node thật hoặc giả lập.

### 4.5. Giám sát và cảnh báo

Node 2 gửi số đo/sự kiện → backend xác thực nguồn và kiểm tra cấu hình → lưu
cảnh báo + inbox đúng chủ cửa → app đọc, hiển thị, cho đánh dấu/xử lý theo quy tắc.
App hiện thăm dò khi foreground; chưa có push nền khi đóng app.

Mất heartbeat quá ngưỡng mặc định 90 giây → OFFLINE; chưa từng nhận → UNKNOWN.
Telemetry quá 60 giây hoặc nguồn không còn ACTIVE/ONLINE được coi là cũ.
Không nhận số đo thì không được tự suy ra trạng thái vật lý từ ACK mở khóa.

## 5. Các khái niệm dễ bị hỏi nhầm

| Cặp khái niệm | Cách phân biệt |
| --- | --- |
| Account / Cardholder | Chủ tài khoản đăng nhập / hồ sơ người được cấp thẻ |
| Provision / Claim | Kỹ thuật tạo bộ cửa / khách hàng nhận quyền sở hữu bằng mã |
| ACTIVE / ONLINE | Được phép vận hành / còn nhận heartbeat gần đây |
| OFFLINE / mở cửa offline | Trạng thái mất liên lạc / chức năng xác thực tại cửa không cần backend |
| GRANTED / EXECUTED | Đã quyết định cấp quyền / Node báo đã thực thi |
| ACK / cửa mở vật lý | Phản hồi giao thức / kết quả đo bằng phần cứng |
| SENT / SYNCED | Broker nhận thông điệp / Node xác nhận phiên bản đã áp dụng |
| Lịch sử truy cập / lifecycle | Các lần xác thực, điều khiển / các lần kích hoạt, vô hiệu hóa, retire Node |
| Docker / phpMyAdmin | Môi trường chạy dịch vụ / giao diện quản trị MySQL |
| V12 / MySQL 8.4 | Phiên bản migration dự án / phiên bản phần mềm database |

## 6. Kịch bản demo chính trong 8–10 phút

Chuẩn bị sẵn một Account A có cửa đã claim, hai Node có credential/ACL và
đã đồng bộ/kích hoạt, một Account B độc lập, một Cardholder và dữ liệu truy cập
thử. Giữ bộ cửa/mã chưa claim riêng nếu muốn trình diễn thêm cửa.

| Thời gian | Thao tác | Điều cần giải thích |
| --- | --- | --- |
| 0–1 phút | Mở sơ đồ 1, giới thiệu kiến trúc | Vai trò App, Backend, DB, Broker, hai Node; nói rõ dùng giả lập nếu có |
| 1–2 phút | Đăng nhập A, xem cửa, đổi tên/vị trí rồi tải lại | Dữ liệu lưu bền vững và thuộc chủ cửa |
| 2–3 phút | Xem hai Node và lịch sử thiết bị | Lifecycle khác kết nối; không cần thay/retire bộ đang dùng cho demo |
| 3–4 phút | Thêm Cardholder, cấp thẻ hoặc đổi PIN | Quyền theo cửa; Cardholder khác Account |
| 4–5 phút | Đổi cấu hình, quan sát chờ rồi đồng bộ | Node phải áp dụng và gửi ACK; có thể ACK nhanh nên trạng thái chờ thoáng qua |
| 5–7 phút | Vuốt mở cửa, sau đó gửi PIN đúng bằng Node 1 giả lập | Luồng App và RFID/PIN cùng định tuyến đúng Node 2; xem lịch sử tương ứng |
| 7–8 phút | Gửi số lần PIN sai đạt ngưỡng hoặc cảnh báo nhiệt độ giả lập | Khóa tạm, cảnh báo và inbox; không coi mô phỏng là còi/khóa thật |
| 8–9 phút | Đăng nhập B, xem danh sách cửa | B không được truy cập cửa của A; API kiểm tra quyền phía backend |
| 9–10 phút | Kết luận, chỉ ra giới hạn và bước tiếp theo | Offline, onboarding trực quan, nghiệm thu phần cứng và push nền |

Nếu chỉ có 5 phút: giới thiệu → đăng nhập/cửa → cấu hình/ACK → mở khóa/lịch sử
→ giới hạn. Chọn ít thao tác đã tập chạy ổn thay vì trình diễn tất cả màn hình.

Kịch bản bổ sung: dừng Node và chờ quá 90 giây để thấy OFFLINE; chạy Node 2
với phản hồi `silent` để xem timeout sau 15 giây. Không có Node giả lập/thật
thì chỉ demo phần lưu dữ liệu, không kỳ vọng mở khóa hoặc đồng bộ thành công.

## 7. Chuẩn bị terminal và lệnh chạy

Chạy từ thư mục gốc repository. Dùng các credential của **cùng cửa demo**;
không nhầm Node 1/Node 2 hoặc bộ fixture khác. Các chuỗi `NODE1_FILE`,
`NODE2_FILE` dưới đây là chỗ thay bằng đường dẫn file riêng đã cấp, không phải
tên file có sẵn. Không mở file chứa key/password khi đang chia sẻ màn hình.

### Trước buổi demo

Nếu có code mới, build và chạy thử từ trước. Docker Desktop phải đang chạy:

```powershell
docker compose up -d --build
docker compose ps
curl.exe --fail http://localhost:8080/actuator/health
```

Cần thấy health `UP`. phpMyAdmin ở `http://localhost:8081`; backend API ở 8080.
MySQL nội bộ container dùng 3306; MQTT host mặc định 1884, nội bộ 1883.
Không cần build lại nếu đang dùng đúng bản đã kiểm chứng và code không đổi.

### Terminal App Android

Cắm USB, bật gỡ lỗi USB và xác nhận quyền trên điện thoại:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ".\mobile\tool\run-usb.ps1"
```

Giữ dây USB và terminal. App truy cập backend PC qua adb reverse cổng 8080.

### Hai terminal giữ Node online và nhận ACK

Môi trường Python giả lập phải được cài theo [scripts/README](../../scripts/README.md).
Chạy mỗi dòng ở một terminal riêng, giữ đến hết demo:

```powershell
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE2_FILE" --duration 1800 --temperature 28.5 --humidity 65 --door-state CLOSED --lock-state LOCKED
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE1_FILE" --duration 1800
```

Node 2 mặc định phản hồi `EXECUTED`. Dữ liệu cảm biến ở đây là giá trị giả lập;
ACK mở khóa không tự đổi `doorState=CLOSED` thành OPEN.

### Thử PIN, cảnh báo và timeout

Dừng phiên simulator của Node tương ứng bằng Ctrl+C trước khi chạy biến thể
khác; tránh hai client cùng danh tính làm ngắt kết nối lẫn nhau.

```powershell
# Node 1: nhập PIN khi được hỏi; gửi trùng để chứng minh chống xử lý lặp.
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE1_FILE" --duration 120 --access-method PIN --retry-access

# Node 1: nhập PIN sai; dùng số lần bằng ngưỡng thực tế của cửa.
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE1_FILE" --duration 120 --access-method PIN --attempts 3 --attempt-interval 2

# Node 2: cảnh báo nhiệt độ khi đã ACTIVE và cấu hình cho phép.
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE2_FILE" --duration 120 --temperature 80

# Node 2: vẫn online nhưng không phản hồi lệnh mở để kiểm tra timeout.
.\.local\tools\Scripts\python.exe scripts/device-simulator.py "NODE2_FILE" --duration 120 --unlock-result silent
```

PIN thử phải là PIN hiện tại của cửa. Tập phần PIN đúng trước phần khóa tạm;
nếu vừa khóa do nhập sai, phải chờ hết thời gian khóa để thử lại. Cảnh báo
nhiệt độ cần bật cờ và ngưỡng thấp hơn giá trị thử; gửi đúng Node 2 của cửa.

Nếu chưa có bộ dữ liệu và credential phù hợp, chuẩn bị theo
[bàn giao tích hợp](../05-backend/person1-person4-completion.md). Smoke test
`person14-smoke.py --mqtt` có thể tạo bộ test riêng, nhưng chỉ chạy lúc chuẩn
bị: nó tạo tài khoản/cửa, đổi mật khẩu của tài khoản test đó, cập nhật ACL và
restart Broker. Dùng thông tin cuối trong báo cáo `.local/` một cách riêng tư.

## 8. Nếu cần chứng minh dữ liệu trong DB

Trong phpMyAdmin chọn database ứng dụng. Chỉ xem các cột cần thiết:

```sql
SELECT id, name, owner_account_id, status FROM doors;

SELECT door_id, node_type, lifecycle_status, last_heartbeat_at,
       applied_config_version, applied_pin_version
FROM devices;

SELECT door_id, version, credentials_version, max_failed_attempts,
       lockout_seconds, temperature_threshold
FROM door_configs;

SELECT door_id, kind, status, attempts, sent_at
FROM integration_outbox ORDER BY id DESC LIMIT 10;
```

So sánh đúng `door_id`; `applied_pin_version` của Node 1 đối chiếu với
`door_configs.credentials_version`. Không trình chiếu `.env`, private export,
hash mật khẩu/PIN hoặc payload chứa snapshot credentials. Không sửa ACK hay
trạng thái lệnh bằng tay để tạo bằng chứng thành công.

## 9. Câu hỏi thường gặp và trả lời ngắn

**Vì sao dùng hai ESP32?** Phân chia tương tác ngoài cửa (thẻ/phím/màn hình)
và điều khiển bên trong (khóa/cảm biến/còi/Exit), giúp trách nhiệm rõ ràng.
Sự phân chia này không tự bảo đảm an toàn; vẫn cần danh tính, ACL và firmware đúng.

**Vì sao App dùng REST còn Node dùng MQTT?** REST phù hợp thao tác yêu cầu–phản
hồi của App. MQTT dùng publish/subscribe để Node và backend trao đổi sự kiện,
lệnh theo topic qua Broker mà không cần App kết nối trực tiếp đến Node.

**Biết cửa của ai bằng cách nào?** JWT xác định Account; backend đối chiếu
`doors.owner_account_id`. Không tin Account ID do client tự gửi hoặc chỉ ẩn
nút trên giao diện. Tài khoản khác không được đọc/ghi/điều khiển cửa đó.

**Kích hoạt bằng mã có phải chỉ là đăng nhập không?** Không. Mã chứng minh quyền
claim bộ cửa đã provision; JWT chứng minh phiên Account. Mã dùng một lần, có
hạn, lưu hash; mã thiết bị công khai không thay cho mã claim.

**Vì sao thiết bị ONLINE nhưng không mở được?** Online chỉ chứng minh nhận
heartbeat gần đây. Thiết bị/cửa phải đủ lifecycle, quyền và điều kiện vận hành;
thiết bị DISABLED có thể còn kết nối nhưng không được xử lý nghiệp vụ.

**Vì sao lưu cấu hình rồi vẫn chờ?** Lưu thành công ở MySQL chưa chứng minh
Node đã nhận và áp dụng. Backend đợi ACK đúng phiên bản, đúng danh tính Node.

**QoS 1 có bảo đảm không mở hai lần không?** QoS 1 có thể giao trùng. Backend
dùng eventId/requestId; contract firmware yêu cầu dedupe commandId bền vững.
Node 2 đã có xử lý chống lặp bền vững; vẫn cần thử reboot/retry trên board thật.

**Timeout nghĩa là khóa chắc chắn chưa mở?** Không. Có thể lệnh đã tới nhưng
ACK bị mất. Hệ thống không báo thành công khi thiếu bằng chứng và không tự
gửi lại lệnh mở sau kết quả mơ hồ; trạng thái vật lý cần cảm biến độc lập.

**Bảo mật thế nào?** Mật khẩu/PIN dùng BCrypt, API dùng JWT và ownership;
MQTT có credential riêng, ACL, HMAC/nonce để kiểm tra nguồn và phát lại. JWT
có chữ ký không đồng nghĩa dữ liệu được mã hóa. Bản local chưa triển khai TLS.

**Đăng xuất/đổi mật khẩu xử lý phiên cũ ra sao?** Đăng xuất thu hồi JWT hiện tại;
đổi mật khẩu tăng `auth_version` để token cũ không còn hợp lệ. Chưa có refresh
token; token hết hạn yêu cầu đăng nhập lại.

**V10 sửa gì?** Code mới cần ghi ACCESS_RESULT và ALARM nhưng CHECK cũ chỉ nhận
CONFIGURATION/CREDENTIALS. V10 mở rộng đúng ràng buộc đó để transaction xác
thực không bị MySQL từ chối; giữ dữ liệu cũ. Kiểm thử H2 không thay được kiểm
tra migration và CHECK trên MySQL thật.

**Mất mạng còn dùng được không?** Nhóm vẫn đặt offline trong phạm vi, nhưng
source hiện tại mới có luồng xác thực online và cache snapshot PIN/thẻ. Chưa có
kênh mở offline giữa hai Node, chính sách quyền offline và đối soát đã nghiệm thu.
Không trình bày chức năng offline đã hoàn thành chỉ vì app có nhãn OFFLINE.

**Phần nào chưa xong?** Onboarding kỹ thuật viên/QR/Wi-Fi, offline, push nền,
TLS và nghiệm thu phần cứng. Đã có công cụ thu hồi Broker trước thay Node và
firmware tích hợp; không lấy simulator làm bằng chứng servo/cảm biến hoạt động thật.

## 10. Điểm nhấn khi trình bày phần Door/Device

Nếu bạn trình bày phần quản lý cửa và vòng đời thiết bị, tập trung vào:

1. Provision trước, claim sau; không để khách hàng tự nhận cửa bằng device_code.
2. Ownership được dùng chung cho các module, không chỉ kiểm tra ở màn danh sách.
3. Lifecycle và trạng thái kết nối độc lập; lịch sử thiết bị không bị xóa.
4. Kích hoạt có kiểm tra bằng chứng cấu hình/danh tính, tránh báo ACTIVE giả.
5. Claim đồng thời, retry, giới hạn một Node ACTIVE cho mỗi vai trò.
6. Thay Node cần `setup-mqtt.py revoke <old-id>`: thu hồi khóa, restart Broker,
   kiểm tra khóa cũ bị từ chối rồi ghi bằng chứng V12; sau đó mới thay trên App.

Khi trình bày đóng góp cá nhân, phân biệt phần tự triển khai, phần tích hợp
và phần lấy từ thành viên khác; không nhận toàn bộ module là phần mình viết.

## 11. Bằng chứng và tài liệu nên mở sẵn

- [Hồ sơ sơ đồ](../03-technical-reference/system-design-review.md): ưu tiên
  sơ đồ 1, 3, 5, 7 bản slide, 8 và 9 khi thuyết trình; bản ERD đầy đủ để hỏi sâu.
- [Đặc tả database](../03-technical-reference/database-erd.md) và
  [hướng dẫn migration](../03-technical-reference/database-setup.md).
- [Luồng truy cập/giám sát](../06-frontend/access-monitoring.md),
  [mở cửa từ App](../06-frontend/door-unlock.md),
  [MQTT contract](../../contracts/mqtt/topics.md).
- Đợt kiểm tra 06/11/2026: **182 test backend, 65 test Flutter, 7 test Python
  đạt**; Flutter analyze đạt; migration V9 → V10 và smoke API/MQTT thật với
  Node giả lập đạt. Đây là bằng chứng phần mềm, không phải nghiệm thu phần cứng.

Trước khi ra trình bày: chạy thử toàn bộ kịch bản một lần, sạc điện thoại,
kiểm tra USB authorization, giữ các terminal Node hoạt động và chuẩn bị
ảnh/video kết quả lần chạy thử để đối chiếu nếu môi trường demo gặp lỗi.
