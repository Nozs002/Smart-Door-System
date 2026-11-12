# Node 1: RFID/PIN online qua Arduino IDE

Luồng: RC522/keypad → MQTT → Backend xác thực → lệnh mở đến Node 2.
Node 1 chỉ hiển thị kết quả xác thực: GRANTED không chứng minh servo đã chạy
hay cửa thực sự mở; xem kết quả lệnh và MC-38 trên App.

## Nạp chương trình

1. Cài ESP32 board core; cài ArduinoJson 7, ArduinoMqttClient, MFRC522,
   Keypad và LiquidCrystal I2C qua Library Manager.
2. Mở `node-auth.ino`, giữ các header cạnh sketch và thư mục `../shared/`
   của repository. `AuthProtocol.h` dùng helper chung `shared/DeviceProtocol.h`.
3. Copy `node_auth_config.h.example` thành **`node_auth_config.local.h`** trong
   cùng thư mục. File local đã được Git bỏ qua; chỉ điền secret trong file đó.
4. Điền Wi-Fi, IP LAN laptop, cổng MQTT 1884 và các giá trị từ export **Node 1**:
   doorId, deviceCode, username, password, credentialId, signingKey.
   Giữ signingKey dạng Base64. Không dùng khóa Node 2 hoặc chạy lại issue nếu
   export hiện tại còn hiệu lực. Ví dụ demo là cửa 5/thiết bị 10/TEST-MAIN-N1.
5. Chọn ESP32 Dev Module, đúng COM, Upload; Serial Monitor 115200 baud.
6. Claim cửa, đặt PIN 4–8 số trên App, gửi cấu hình và đồng bộ PIN/thẻ. Chờ
   MQTT, NTP, heartbeat và ACK cả CONFIGURATION/CREDENTIALS rồi kích hoạt Node 1.
   Kiểm tra `last_seen_at`, `applied_config_version`, `applied_pin_version`.

Thiếu cấu hình local: sketch vẫn biên dịch với mẫu an toàn nhưng không kết nối,
LCD báo CAU HINH THIEU. Firmware tự tạo client ID riêng, không lấy khóa Node 2.
Đây là bản thử TCP trên LAN tin cậy; ngoài local/demo cần TLS và xác minh
CA/hostname trước khi gửi RFID/PIN thật, không tắt kiểm tra chứng chỉ.

## GPIO giữ nguyên dây đã test

| Thiết bị | GPIO |
| --- | --- |
| RC522 SS/RST | 5 / 15 |
| RC522 SCK/MISO/MOSI | 18 / 19 / 23 |
| LCD SDA/SCL | 21 / 22, địa chỉ 0x27 |
| Keypad R1–R4 | 13 / 14 / 27 / 26 |
| Keypad C1–C3 | 25 / 33 / 32 |

LCD 16x2 dùng chữ không dấu. RC522 lỗi thì vẫn dùng keypad được; không nối
servo vào Node 1. Sketch không dùng shared/network/wifi_manager.cpp.

## Demo khi hàng `* 0 #` bị hỏng

Firmware có phím dự phòng, không cần đổi PIN thành 4 số hoặc bỏ số 0:

| Thao tác | Kết quả |
| --- | --- |
| Bấm ngắn 1–9 | Nhập số đó; riêng 1/2/3 nhận khi thả phím |
| Giữ `1` khoảng 0,9 giây | Xóa toàn bộ PIN đang nhập |
| Giữ `2` khoảng 0,9 giây | Nhập **một số 0** |
| Giữ `3` khoảng 0,9 giây | Gửi PIN 4–8 số |

Ví dụ PIN `1203`: bấm ngắn **1**, bấm ngắn **2**, giữ **2**, bấm ngắn **3**,
rồi giữ **3** để gửi. Thả phím sau mỗi thao tác. Giữ không lặp và không chèn
thêm 1/2/3 vào PIN. Các phím `*`, `0`, `#` vẫn dùng bình thường nếu phần cứng tốt.
LCD nhắc thao tác giữ. Phải Upload lại sketch Node 1 để có chức năng này.

Đây là cách nhập dự phòng, không sửa phần cứng. Ba phím cuối chung hàng R4,
đang nối **GPIO26** trong `rowPins`; ngắt nguồn rồi kiểm tra dây R4/jack/chân
keypad, chạy `hardware-tests/node-1/keypad_test/` để kiểm tra từng phím.

## Hành vi

- Keypad nhận PIN 4–8 số, `#` gửi, `*` xóa, sau 20 giây không nhập tự xóa.
  Không tự gửi ở số thứ tư; số thứ chín bị từ chối. PIN hiển thị bằng dấu sao;
  không log PIN/UID/envelope/khóa, không lưu PIN rõ vào NVS.
- Một lượt xác thực có UUID và hạn 15 giây. Chỉ nhận kết quả đúng eventId,
  còn hạn, schema/result hợp lệ và không retained. Không tự retry/replay
  yêu cầu sau reconnect, kể cả khi không chắc Broker đã nhận.
- Heartbeat, access-request và sync-ack dùng signed envelope HMAC-SHA256,
  QoS 1, retain=false. NTP phải có thật và chưa quá hai giờ kể từ lần sync.
- Lưu nguyên snapshot cấu hình vào NVS trước ACK, không chấp nhận phiên bản
  lùi hoặc cùng version khác dữ liệu. NVS gắn với Door/deviceCode/credentialId.
- Backend quyết định bộ đếm sai và khóa tạm riêng PIN/RFID. Node lưu thời điểm
  lockedUntil nhận từ Backend để khóa đúng phương thức, giữ qua reset.
- Không có delay dài cho LCD/đọc phím/thẻ, không có vòng chờ reconnect vô hạn.
  Kết nối/gửi MQTT vẫn có timeout hữu hạn 2 giây. AUTOMATIC dừng yêu cầu xác thực và hiển thị chế độ giữ mở.
- Xác thực thành công hiển thị XAC THUC DUNG; Da yeu cau mo khi Backend trả
  QUEUED, Kiem tra Node 2 khi không tạo được lệnh điều khiển.

**Xác thực mở cửa hiện vẫn online; offline còn trong phạm vi phát triển.**
Node subscribe `commands/credentials`, kiểm tra schema, UID/trùng thẻ/expiry,
verifier BCrypt `$2a$`/`$2b$`/`$2y$` cost 4–12 (Backend dùng 10), rồi thay toàn
bộ snapshot bằng một blob NVS và đọc lại xác minh trước ACK CREDENTIALS.
Snapshot tối đa 16 KiB; Backend từ chối gửi vượt giới hạn, không cắt danh sách.
Phiên bản lùi hoặc cùng phiên bản khác dữ liệu không được ACK. Snapshot rỗng
xóa quyền cũ; một thẻ bị thu hồi không còn trong snapshot mới.
NVS gắn với Door/deviceCode/credentialId, nên đổi danh tính không dùng ACK cũ.
BCrypt Openwall được vendor trong `src/bcrypt/`; self-test vector độc lập chạy
khi boot. Lỗi NVS/BCrypt chặn ACK. Có hàm kiểm tra verifier nhưng luồng online
vẫn gửi yêu cầu đến Backend để quyết định quyền/khóa tạm; chưa gửi lệnh trực
tiếp đến Node 2 khi mất mạng. Không dùng ACK giả hoặc sửa DB để kích hoạt.
NVS bản demo chưa mã hóa flash; triển khai thật cần NVS encryption/secure boot
và TLS. ACK chứng minh lưu áp dụng snapshot, không chứng minh chống đọc flash.

## Kiểm thử trên mô hình

1. Bắt đầu với bộ cửa mới PROVISIONED, claim và cấp credential riêng từng Node.
   Chạy `setup-mqtt.py configure`, nạp đúng firmware, đặt PIN/cấp thẻ và gửi
   đồng bộ trên App. Đợi ACK đúng phiên bản rồi kích hoạt hai Node; cửa ACTIVE.
2. Kiểm tra Node 1 heartbeat và ACK cấu hình trong DB; reset rồi gửi lại cùng
   snapshot phải ACK mà không ghi NVS thêm. `applied_pin_version` phải bằng
   `door_configs.credentials_version`; thu hồi thẻ rồi đồng bộ lại.
3. Quẹt thẻ hợp lệ hoặc nhập PIN đúng và bấm # → LCD báo xác thực đúng, Node 2 mở 180°;
   đối chiếu access_logs GRANTED và control_commands EXECUTED trên App/DB.
4. Thẻ chưa cấp/PIN sai → DENIED, không mở. Sai đủ ngưỡng → khóa phương thức
   đó; khi PIN khóa vẫn có thể thử RFID đúng. Reset không xóa khóa tạm đã lưu.
5. Thử PIN 4/6/8 số, * xóa, nhập 1–3 số rồi # không gửi, nhập dở quá 20 giây
   tự xóa; nhập đủ số không tự gửi, # gửi đúng một yêu cầu. Giữ thẻ không được tạo vòng
   yêu cầu liên tục; nhấc ra rồi đưa lại tạo lượt mới.
6. Mất mạng/broker/backend → không mở offline, không phát lại yêu cầu khi có
   mạng. Nếu đã gửi nhưng timeout, kiểm tra App/lịch sử trước khi thử lượt mới:
   backend có thể đã nhận và mở cửa dù Node 1 chưa nhận kết quả.
7. Kết quả sai eventId, quá hạn hoặc retained không đổi LCD thành thành công.

Biên dịch từ root repo, không upload board:

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 --build-path .local/arduino-build/node1 firmware/node-auth
```

Contract: [MQTT](../../contracts/mqtt/topics.md),
[truy cập/giám sát](../../contracts/api/access-monitoring.md).

## Kiểm tra giao thức không cần ngoại vi

Trong `node-auth.ino`, bỏ comment dòng `#define NODE_AUTH_PROTOCOL_SELF_TEST`,
Upload vào ESP32 và mở Serial 115200. Chế độ này chỉ chạy các kiểm tra UUID,
UTC, cấu hình và kết quả đúng/sai lượt, retained, hết hạn, signing key sai;
không kết nối mạng hay điều khiển chân ngoại vi. Kết quả mong đợi là failed: 0.
Sau đó comment lại dòng này và Upload lại firmware online. Đây là các ca chạy
trên ESP32; biên dịch thành công không có nghĩa đã chạy/passed các ca trên board.

Test logic keypad và BCrypt trên máy, không cần board:

```powershell
docker build -f firmware/tests/native/Dockerfile -t smartdoor-input-tests firmware
docker run --rm smartdoor-input-tests
```

Chạy từ root repository. Test kiểm tra nhập `1203` không cần hàng cuối,
nhấn/giữ/thả không lặp, xóa, PIN ngắn/dài, không tràn buffer và vector BCrypt
đối chiếu Spring Security. Không thay thế test GPIO, NVS hoặc kết nối ESP32.

## Xác minh bản này

Cập nhật 12/11/2026: build cấu hình giả đầy đủ và build chế độ self-test đạt
với core 3.3.11; bản đầy đủ dùng 77% flash, 16% RAM tĩnh. Test C++ native
phím dự phòng và vector BCrypt đạt. Chưa upload/chạy bộ self-test hoặc thử
NVS/luồng bộ cửa mới trên ESP32 thật. Số liệu bên dưới là build trước khi sửa.

Đã biên dịch ESP32 Dev Module với core 3.3.11, ArduinoJson 7.4.3,
ArduinoMqttClient 0.1.8, MFRC522 1.4.12, Keypad 3.1.1 và LiquidCrystal I2C 1.1.2.
Đã build cả cấu hình mẫu để trống lẫn cấu hình giả có đủ trường để kiểm tra
nhánh kết nối mạng. Bản đầy đủ dùng khoảng 76% flash, 15% RAM tĩnh.
Thư viện LCD có cảnh báo metadata chỉ khai báo AVR; build ESP32 vẫn thành công.
Chưa chạy protocol self-test hoặc luồng phần cứng Node 1 trên board thật.

## Chế độ giữ mở

`AUTOMATIC` là tắt bảo mật và giữ cửa mở tại Node 2. Node 1 xóa PIN đang nhập/yêu cầu đang chờ khi đổi mode, dừng gửi RFID/PIN và hiển thị `BAO MAT DA TAT / Che do giu mo`. Bật SECURITY lại sẽ tiếp tục xác thực online theo Backend, giữ nguyên lockout chưa hết hạn. LCD biểu thị chế độ cấu hình, không khẳng định cửa vật lý đã mở.
