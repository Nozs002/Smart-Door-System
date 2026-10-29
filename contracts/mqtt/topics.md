# MQTT Topics

Contract v1 cho cấu hình, đồng bộ credential và cảnh báo. Mọi topic đều chứa cả
`doorId` và `deviceCode`; Broker ACL phải ràng buộc credential thiết bị với đúng
hai giá trị này. Backend luôn đối chiếu lại danh tính đã xác thực với bảng
`devices`, lifecycle và `node_type`; không tin ID trong payload. Cảnh báo nghiệp
vụ yêu cầu `ACTIVE`. Kênh setup cấu hình/ACK chấp nhận `PROVISIONED`, `ACTIVE`,
`DISABLED` sau claim và luôn từ chối `RETIRED`.

## Topics

| Topic | Chiều | QoS | Retained | Nội dung |
| --- | --- | ---: | --- | --- |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/commands/configuration` | Backend → Node 1/2 | 1 | Có | Snapshot cấu hình và `version` |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/commands/credentials` | Backend → Node 1 | 1 | Có | Snapshot PIN verifier/RFID theo `version` |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/events/sync-ack` | Node → Backend | 1 | Không | Xác nhận phiên bản đã áp dụng |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/events/alert` | Node → Backend | 1 | Không | Cảnh báo sai nhiều lần/nhiệt độ/cửa mở lâu |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/events/heartbeat` | Node → Backend | 1 | Không | Phiên bản firmware; thời điểm nhận ở Backend dùng suy ra kết nối |

Không dùng wildcard publish và không broadcast lệnh/cảnh báo cho nhiều cửa.
Retained command là desired state mới nhất; ACK và alert là event nên không
retained. Consumer phải xử lý QoS 1 idempotent bằng `eventId`.

## Payload cấu hình

```json
{
  "schemaVersion": 1,
  "version": 7,
  "maxFailedAttempts": 3,
  "lockoutSeconds": 60,
  "temperatureThreshold": 50.0,
  "openTimeoutSeconds": 30
}
```

Node chỉ thay state khi `version` lớn hơn phiên bản đã có, ghi bền vững trước khi
gửi ACK. Nếu nhận lại cùng version, Node gửi lại ACK để phục hồi ACK đã mất.
Node 1 dùng ngưỡng sai/thời gian khóa; Node 2 dùng ngưỡng nhiệt độ và
thời gian cửa mở. Cả hai vẫn lưu toàn snapshot để có cùng phiên bản.

## Payload đồng bộ PIN/RFID

```json
{
  "schemaVersion": 1,
  "version": 12,
  "reason": "RFID_REVOKED",
  "pinVerifier": "$2a$10$...",
  "cards": [
    {"credentialId": 8, "cardholderId": 3, "uid": "AABBCCDD", "expiresAt": null}
  ]
}
```

`reason`: `PIN_CHANGED`, `RFID_CHANGED`, `RFID_REVOKED` hoặc `FULL_REFRESH`.
Transport đọc `CredentialSnapshotService` trong transaction đã khóa Door, lấy
version/snapshot mới nhất. Chỉ đưa thẻ còn hiệu lực của Cardholder `ACTIVE` đúng
chủ cửa; snapshot đầy đủ thay danh sách cũ để thu hồi có hiệu lực. `pinVerifier`
là BCrypt riêng của PIN cửa, có thể null nếu chưa cấp PIN. Không gửi PIN rõ, hash
đăng nhập Account hoặc secret MQTT trong payload/log. Firmware phải hỗ trợ xác
minh BCrypt và lưu snapshot an toàn trước khi có thể báo ACK áp dụng.

## Payload ACK

```json
{
  "schemaVersion": 1,
  "eventId": "01JAZ...",
  "kind": "CONFIGURATION",
  "version": 7,
  "appliedAt": "2026-10-27T02:30:00Z"
}
```

`kind` là `CONFIGURATION` hoặc `CREDENTIALS`. `CREDENTIALS` chỉ hợp lệ từ
Node 1. Backend từ chối phiên bản tương lai và không cho ACK cũ làm lùi phiên
bản đã áp dụng. Heartbeat không được sửa các cột ACK.
ACK ở kênh setup không tự chuyển lifecycle sang `ACTIVE`. Một ACK version hợp lệ
nhỏ hơn desired version chỉ cập nhật đơn điệu, không làm cấu hình thành `SYNCED`.

## Payload cảnh báo

```json
{
  "schemaVersion": 1,
  "eventId": "01JBA...",
  "type": "HIGH_TEMPERATURE",
  "observedTemperature": 55.5,
  "occurredAt": "2026-10-27T02:31:00Z"
}
```

`type`: `TOO_MANY_FAILURES`, `HIGH_TEMPERATURE`, `DOOR_OPEN_TOO_LONG`.
`observedTemperature` bắt buộc riêng với `HIGH_TEMPERATURE`; sự kiện này chỉ
hợp lệ từ Node 2 và phải vượt ngưỡng cấu hình hiện tại. Cảnh báo xác thực sai chỉ
hợp lệ từ Node 1; cảnh báo cửa mở lâu chỉ từ Node 2. Backend chuẩn hóa `eventId`,
nhiệt độ và thời gian trước khi chống trùng. Retry cùng payload trả cảnh báo cũ
trước khi đánh giá lại ngưỡng hiện tại. Cùng `eventId` nhưng khác Door/Node/loại/
nhiệt độ/thời điểm trả lỗi xung đột. Backend ghi một thông báo inbox duy nhất
đến `owner_account_id` của cửa nguồn cùng transaction với cảnh báo.

## Tích hợp Backend

- Module credentials gọi `DoorSettingsService.requestCredentialsSync(...)` trong
  cùng transaction đổi PIN/thêm/thu hồi RFID hoặc vô hiệu hóa Cardholder.
- ACK gọi `DeviceSyncAcknowledgementService`; alert gọi
  `HardwareAlertService` với `AuthenticatedDeviceIdentity` lấy từ credential/ACL,
  không dựng danh tính từ payload.
- `DeviceCommandPublisher` ghi outbox bền vững cùng transaction DB; dispatcher
  gọi `OutboxTransport`. Không có transport thì giữ `PENDING`, lỗi được retry có
  giới hạn. `SENT` là kết quả transport, `SYNCED` cần ACK của Node. Khi crash sau
  gửi nhưng trước ghi `SENT`, có thể gửi lại; Node phải idempotent theo version.
- `OwnerAlertNotifier` ghi `owner_notifications` cùng transaction cảnh báo, App
  đọc qua `/api/v1/notifications`. Chưa cấu hình FCM/push nền; không coi Spring
  application event là bằng chứng giao lệnh/thông báo.

## Envelope xác thực của event thiết bị

Các ví dụ ACK/alert phía trên là **payload bên trong**. Khi publish `events/*`,
Node gửi JSON envelope sau, không publish payload nghiệp vụ trực tiếp:

```json
{
  "credentialId": "uuid-do-cli-cap",
  "sentAt": 1793150000,
  "nonce": "uuid-ngau-nhien-cho-lan-gui",
  "payload": "Base64(UTF-8 JSON payload)",
  "signature": "64-ky-tu-hex-HMAC-SHA256"
}
```

`sentAt` là Unix seconds UTC. `nonce` gồm 8–100 ký tự chữ/số/`_`/`-`; tạo mới
cho lần gửi mới. Dùng signing key Base64 trong private device export (giải mã
thành bytes) để HMAC UTF-8 của chuỗi sau, ngăn cách bằng LF, không có LF cuối:

```text
<MQTT topic đầy đủ>
<credentialId>
<sentAt dưới dạng số thập phân>
<nonce>
<payload Base64 nguyên văn>
```

`signature` dùng hex SHA-256. Backend so sánh constant-time, đối chiếu
credential hiện tại với Door/Device trong topic và DB, từ chối danh tính đã
rotate/thu hồi/RETIRED. ID trong payload không xác định danh tính.
Giới hạn toàn bộ thông điệp là 65.536 bytes. Heartbeat payload:

```json
{"firmwareVersion":"node-auth-1.0"}
```

Không chấp nhận thời điểm tương lai quá 60 giây. Heartbeat chỉ được cũ tối đa
60 giây, tránh heartbeat chờ lâu làm thiết bị thành ONLINE giả. ACK/alert ký
trong vòng 24 giờ được nhận để phục hồi event đang chờ tại Broker khi Backend
tạm ngừng; receipt nonce được giữ 48 giờ, lâu hơn cửa sổ chấp nhận replay.
Event quá cửa sổ phải được Node gửi lại trong envelope mới với **eventId nghiệp
vụ giữ nguyên**; không tạo eventId mới cho cùng cảnh báo. `occurredAt` là thời
điểm sự kiện tại nguồn, khác `sentAt` của lần gửi. Phản ứng nhiệt độ tại chỗ
không chờ Backend hoặc thông báo App.

Backend xử lý event ngoài callback thread của Paho; chỉ ACK MQTT sau khi
transaction nghiệp vụ commit hoặc đã quyết định từ chối thông điệp không hợp
lệ. Lỗi lưu trữ không được ghi receipt/ACK thành công. Broker session và
persistence Backend được giữ qua reconnect; business eventId/version vẫn phải
idempotent. Tham khảo [API manual ACK của Paho](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttAsyncClient.html#setManualAcks-boolean-).

## Provision credential, ACL và cổng local

- Backend dùng user riêng: read `events/+`, write `commands/+` cho các cửa.
- Mỗi Node có username/password riêng, chỉ read `commands/+` và write đúng ba
  topic event của chính Door/deviceCode. Node 2 không được ACK credentials;
  kiểm tra vai trò được thực hiện thêm ở Backend.
- `python scripts/setup-mqtt.py init` tạo master key còn thiếu và chuẩn bị
  Broker; `issue <deviceId>` xuất private credential; `configure` cập nhật
  password/ACL rồi restart Broker để áp dụng danh tính hiện tại.
- Broker tắt anonymous; active password/ACL ở volume Linux có owner mosquitto,
  mode 0600, tránh quyền bind mount Windows. Secret/private export nằm trong
  `.env`/`.local`, không lên Git. Database chỉ lưu credential ID và metadata.
- Docker host mặc định **1884** (`MQTT_HOST_PORT`), Broker nội bộ vẫn **1883**.
  Trên máy có Mosquitto Windows ở 1883, kết nối localhost:1883 sẽ đi nhầm Broker.
  Backend chạy từ IDE thay vì Docker cần đặt `MQTT_PORT=1884`, host localhost.
- TCP chỉ dùng local; production bật `MQTT_TLS=true`, Broker TLS và certificate
  tin cậy. Không vô hiệu hóa kiểm tra certificate/hostname để thử cho qua.
