# Node 2: điều khiển, cảm biến và cảnh báo

Thay cho sketch legacy, giữ góc đã kiểm tra trên mô hình: **mở 180°, đóng 90°**.
Servo kéo trực tiếp cánh cửa. Sau lệnh mở bình thường hoàn tất, giữ mở **10 giây**
rồi tự đóng 90°, kể cả mất mạng. Có thể đóng ngay qua nút Đóng cửa trên App hoặc
`CLOSE` + Newline trên Serial. Báo nhiệt hủy timer và chặn cả hai cách đóng.
Không replay lệnh cũ khi reset/reconnect. Riêng AUTOMATIC đã lưu và được cấp quyền sẽ tiếp tục giữ mở sau reset; SECURITY không tự quay chỉ vì boot.

## Cấu hình và nạp

1. Arduino IDE: cài ESP32 core, ArduinoJson 7, ArduinoMqttClient, ESP32Servo,
   DHT sensor library của Adafruit (và Adafruit Unified Sensor).
2. Mở `node-control.ino`. Giữ các header cùng thư mục và thư mục `../shared/`
   của repository; hai node dùng `shared/DeviceProtocol.h` để ký thông điệp.
3. Copy `node_control_config.h.example` thành `node_control_config.local.h`
   cùng thư mục (đã ignore Git). Điền Wi-Fi và IP LAN laptop/cổng MQTT.
4. Điền doorId/deviceCode/username/password/credentialId/signingKey từ export
   **Node 2 mới nhất**, ví dụ thiết bị 11, cửa 5, TEST-MAIN-N2. Không dùng khóa
   Node 1, giữ signingKey dạng Base64. Không cần cấp lại khóa chỉ để đổi sketch.
5. Upload ESP32 Dev Module, đúng COM. Serial 115200 baud. Đợi MQTT, NTP,
   heartbeat và sync-ack. Nếu chưa có cấu hình, gửi lại từ App.

Thiếu cấu hình: build với mẫu an toàn nhưng không chạy phần cứng hay kết nối.
Đây là bản thử TCP trên LAN tin cậy; production cần TLS có xác minh CA/hostname.

## Dây và tín hiệu

| Thiết bị | Chân |
| --- | --- |
| SG90 signal | GPIO18, mở 180°, đóng 90° |
| MC-38 | GPIO16 và GND, INPUT_PULLUP; hiệu chỉnh thực tế: HIGH=CLOSED, LOW=OPEN |
| DHT11 module 3 chân | Data GPIO4, VCC 3V3, GND |
| Buzzer module 3 chân | Signal GPIO5, VCC 3V3, GND; HIGH=kêu |
| LED | GPIO2 qua điện trở 220 Ω, cathode GND |

Servo dùng nguồn 5V phù hợp và nối chung GND ESP32. LED thể hiện lệnh mở chốt
đã phát trong phiên chạy, không đo vị trí chốt. Telemetry lockState luôn UNKNOWN
vì SG90 không có cảm biến phản hồi vị trí; MC-38 chỉ đo cánh cửa.

## Hành vi

- `commands/unlock`: kiểm tra UUID, schema/action, hạn UTC và retained. Chỉ
  mở khi đã có giờ NTP đáng tin, cấu hình hợp lệ và bộ nhớ hoạt động.
- `commands/close`: cùng cơ chế xác thực, TTL, journal và ACK như UNLOCK,
  action CLOSE phải khớp topic. Đang báo nhiệt trả REJECTED, không đóng.
  Nếu báo nhiệt xuất hiện giữa chuyển động đóng thì mở lại và từ chối CLOSE.
- Timer monotonic giữ mở 10 giây bắt đầu sau khi lưu kết quả EXECUTED của lệnh
  mở (chờ ổn định 700 ms). ID trùng không gia hạn; lệnh mở mới hợp lệ khởi động
  khoảng giữ mở mới. Đóng bằng App/Serial hủy timer. Không dùng openTimeoutSeconds
  cho timer này và không tạo command ACK giả cho tự đóng. Nhật ký lưu thêm action,
  các bản ghi cũ thiếu action được hiểu là UNLOCK.
- Lưu commandId/kết quả trước/sau tác động servo. Trùng ID chỉ gửi lại kết quả,
  không quay lại; nhật ký giữ tối đa 32 lệnh, chỉ thay mục hết hạn. Mất điện
  giữa quay và lưu kết quả có thể để lại REJECTED dù servo đã nhận lệnh;
  không replay hoặc suy đoán trạng thái vật lý. EXECUTED sau thời gian ổn định
  700 ms chỉ xác nhận phát lệnh servo. Giữ namespace/journal tương thích sketch
  node2_heartbeat_test đã dùng; cùng Door/Node/credential mới dùng lại nhật ký.
  Khi ghi NVS, bỏ các mục đã hết hạn khỏi snapshot, giữ mục còn hiệu lực và
  mục đang chuyển động để giảm kích thước chuỗi journal. Không xóa namespace
  hoặc nhật ký cũ trước khi ghi; chỉ cho quay servo sau khi ghi và đọc kiểm tra
  thành công. Log `[NVS]` báo kích thước ghi/đọc và kết quả đối chiếu.
- Heartbeat, command-ack, sync-ack, telemetry và alert đều HMAC-SHA256, Base64,
  UUID nonce, QoS 1, retain=false. Chỉ công bố event khi NTP đã sync và chưa
  quá hai giờ; lệnh remote cũng cần điều kiện này. Broker PUBACK không chứng
  minh Backend đã chấp nhận nghiệp vụ.
- Lưu toàn snapshot cấu hình vào một bản ghi NVS trước ACK; không lùi version
  hoặc ACK cùng version khác nội dung. Khôi phục sau reset. Lỗi lưu mới vẫn
  giữ cấu hình RAM cũ cho xử lý tại chỗ, nhưng chặn lệnh remote mới.
- MC-38 lọc rung 50 ms; DHT11 đọc mỗi 2 giây. Telemetry sau thay đổi cửa,
  reconnect và định kỳ 20 giây; số đo lỗi/out-of-range là null. Không replay
  các trạng thái cửa cũ sau mất mạng.
- `commands/alarm`: còi 5 giây, từ chối retained/hết hạn/sai schema/UUID;
  lưu đủ eventId trước khi kêu để không kêu lại do QoS 1/reset. Không ACK
  command-ack cho alarm vì contract chưa định nghĩa ACK đó.
- Cửa mở quá timeout khi doorOpenAlertEnabled bật: còi cho tới khi cửa đóng
  hoặc tắt tính năng; tạo một alert cho mỗi lần điều kiện được kích hoạt.
- Nhiệt độ vượt ngưỡng khi fireAlarmEnabled bật: mở chốt 180° và còi ngay tại
  chỗ, kể cả mất Wi-Fi/broker. Khi chưa có snapshot, mặc định báo nhiệt bật,
  ngưỡng 50°C; cảnh báo cửa mở tắt. Không phụ thuộc operatingMode.
- Cảnh báo nhiệt đang bật mà DHT lỗi thì giữ phản ứng cho đến khi có số đo
  bình thường hoặc tắt cờ. Khi điều kiện hết, còi nhiệt dừng, **không tự đóng**
  chốt. CLOSE chỉ được dùng khi điều kiện nhiệt không còn.
- Chưa hỗ trợ xác thực RFID/PIN offline giữa hai ESP32, nút thoát chưa được đấu.

## Alert khi mất mạng

Outbox NVS có giới hạn 8 mục, giữ eventId/payload/occurredAt nguyên vẹn qua
retry/reset. Trong khi một loại alert còn chờ, quan sát cùng loại được gộp vào
event đang chờ. Mỗi episode trong RAM chỉ tạo một event. Sau reset, nếu điều
kiện vẫn còn mà event trước đã được Broker nhận, có thể tạo một quan sát mới.
Nếu outbox lỗi/đầy, phản ứng tại chỗ vẫn chạy và ghi lỗi Serial.

Khi chưa có giờ UTC đáng tin, vẫn phản ứng tại chỗ; chỉ tạo alert từ quan sát
còn tồn tại sau đồng bộ giờ, không bịa timestamp lịch sử cho điều kiện đã hết.
Mỗi lần gửi lại dùng envelope/nonce mới nhưng giữ nguyên eventId và payload.
Xóa khỏi outbox sau Broker PUBACK. Backend vẫn có thể từ chối vì Node chưa ACTIVE,
cấu hình hiện tại đã tắt hoặc ngưỡng đã đổi; kiểm tra App/log, không coi Serial
`sent` là cảnh báo đã ghi DB. Không tự xóa alert ACTIVE trên Backend khi hết còi.

## Kiểm tra trên hai board

1. Chờ Node 2 ONLINE và applied_config_version bằng bản mong muốn.
2. Mở từ App/thẻ/PIN → servo 180°, control_commands EXECUTED; giữ mở 10 giây
   rồi servo 90°. Thử nút Đóng cửa + xác nhận để đóng trước hạn; CLOSE Serial
   vẫn hoạt động. Tự đóng phải còn chạy khi ngắt Wi-Fi sau khi lệnh mở hoàn tất.
3. Đưa/tách nam châm → CLOSED/OPEN trên Serial, door_telemetry và trang giám sát.
4. DHT11 có số đo nhiệt/ẩm; tháo Data khi đã ngắt nguồn rồi bật lại → số đo lỗi
   phải thành null. Không phát lệnh mở mới do một số đo NaN.
5. Bật cảnh báo cửa mở với timeout 5–10 giây để thử. Mở cánh cửa qua MC-38:
   đủ timeout mới kêu và tạo DOOR_OPEN_TOO_LONG; đóng cửa thì hết điều kiện còi.
6. Thử cảnh báo sai thẻ/PIN từ Node 1 tới ngưỡng → còi Node 2 kêu 5 giây.
   Gửi lại cùng alarm ID không được kéo dài/kêu lại; alarm không quay servo.
7. Kiểm tra phản ứng nhiệt bằng điều kiện thử nghiệm phù hợp cảm biến, không
   dùng lửa. Ngắt mạng: điều kiện nhiệt vẫn mở chốt/kêu; thử CLOSE phải bị chặn.
   Khi nhiệt bình thường thì hết còi, chốt vẫn mở cho tới CLOSE.
8. Thử lệnh unlock trùng, hết hạn, retained, reset hoặc reconnect: không replay.
   Thử cả CLOSE với các điều kiện đó. ID trùng không kéo dài timer giữ mở.
   Theo dõi điều kiện tại chỗ vẫn chạy khi Backend/broker không khả dụng.

Đây là firmware mô hình, chưa xác minh trên phần cứng với bản tích hợp này.
Không suy diễn DHT11 thành hệ thống báo cháy được chứng nhận.

## Kiểm tra chính sách không tác động GPIO

Bỏ comment NODE_CONTROL_SELF_TEST trong sketch, Upload và mở Serial 115200.
Chế độ này chỉ kiểm tra ngưỡng nhiệt/cửa, switch, NaN, millis rollover, UUID,
timestamp; không điều khiển chân, ghi NVS hay kết nối Wi-Fi. Mong đợi failed: 0.
Sau đó comment lại và nạp firmware thật. Các ca lệnh/NVS/reset vẫn cần board.

Biên dịch từ root repo:

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 --build-path .local/arduino-build/node2 firmware/node-control
```

Contract: [MQTT](../../contracts/mqtt/topics.md),
[truy cập/giám sát](../../contracts/api/access-monitoring.md),
[cấu hình/cảnh báo](../../contracts/api/settings-alerts.md).

## Xác minh bản này

Đã biên dịch bản đầy đủ với cấu hình giả trên ESP32 core 3.3.11, ArduinoJson
7.4.3, ArduinoMqttClient 0.1.8, ESP32Servo 3.2.1, DHT sensor library 1.4.7
và Adafruit Unified Sensor 1.1.15: khoảng 75% flash, 15% RAM tĩnh.
Node 1 cũng đã build lại thành công sau khi tách helper chung. Chưa chạy
self-test hoặc kiểm thử bản Node 2 tích hợp này trên phần cứng thật.

Khi cập nhật tính năng đóng cửa, cần nạp Node 2 mới, cập nhật Backend có migration
V11 rồi App mới. API/MC-38 xác nhận trạng thái cánh cửa; commandedLockState chỉ
là lệnh remote gần nhất và có thể cũ sau tự đóng. Mô hình chưa có cảm biến vật
cản, nên thử chuyển động khi lối đi trống. Không dùng bản thử làm cơ cấu cửa thật.

Bản có CLOSE/tự đóng gửi firmwareVersion `node-control-online-1.1` qua heartbeat.
Đã kiểm tra build firmware, 17 test Backend, 68 test Flutter, 8 test simulator,
Flutter analyze/build APK và DDL V11 trên bảng TEMP MySQL. Các ca tự đóng,
đóng từ App và ưu tiên báo nhiệt vẫn cần chạy lại trên mô hình sau khi nạp.

## Tắt/bật chế độ bảo mật

- Tắt trên App (`AUTOMATIC`): mở 180° và giữ mở, hủy timer 10 giây; không nhận CLOSE từ App/Serial.
- Bật lại (`SECURITY`): đóng 90°; các lần mở bằng RFID/PIN/App tiếp theo tự đóng sau 10 giây.
- Báo nhiệt ưu tiên mở; khi hết báo nhiệt không tự đóng lại. Cảnh báo cửa mở lâu là công tắc riêng, vẫn kêu nếu bật và vượt timeout.
- Snapshot có `controlEnabled=true` mới cho phép tác động mode/remote/Serial. Backend mới cấp cờ khi Door và Node 2 ACTIVE; setup chỉ lưu và ACK. Snapshot cũ thiếu cờ không cấp quyền. Cờ và cấu hình được lưu NVS để dùng khi mất mạng; thay đổi quyền server cần giao snapshot mới.
- Nạp lại cả Node 1 và Node 2, rebuild Backend/App rồi đồng bộ lại cấu hình. Tắt bảo mật, chờ ACK Node 2, kiểm tra giữ mở hơn 10 giây; bật lại, kiểm tra servo đóng và thẻ/PIN hoạt động. Thử Hủy ở hộp xác nhận: cửa không đổi mode.
- ACK sau phát lệnh servo 700 ms; đối chiếu MC-38 để biết cửa thực tế, không suy đoán từ ACK.
