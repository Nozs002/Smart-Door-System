# MQTT Topics

Contract v1 cho cấu hình, đồng bộ credential và cảnh báo. Mọi topic đều chứa cả
`doorId` và `deviceCode`; Broker ACL phải ràng buộc credential thiết bị với đúng
hai giá trị này. Backend luôn đối chiếu lại danh tính đã xác thực với bảng
`devices`, trạng thái lifecycle `ACTIVE` và `node_type`; không tin ID trong
payload.

## Topics

| Topic | Chiều | QoS | Retained | Nội dung |
| --- | --- | ---: | --- | --- |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/commands/configuration` | Backend → Node 1/2 | 1 | Có | Snapshot cấu hình và `version` |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/commands/credentials` | Backend → Node 1 | 1 | Có | Yêu cầu làm mới PIN/RFID theo `version` |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/events/sync-ack` | Node → Backend | 1 | Không | Xác nhận phiên bản đã áp dụng |
| `smart-door/v1/doors/{doorId}/devices/{deviceCode}/events/alert` | Node → Backend | 1 | Không | Cảnh báo sai nhiều lần/nhiệt độ/cửa mở lâu |

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

Node chỉ áp dụng khi `version` lớn hơn phiên bản đã có, ghi bền vững trước khi
gửi ACK. Node 1 dùng ngưỡng sai/thời gian khóa; Node 2 dùng ngưỡng nhiệt độ và
thời gian cửa mở. Cả hai vẫn lưu toàn snapshot để có cùng phiên bản.

## Payload đồng bộ PIN/RFID

```json
{
  "schemaVersion": 1,
  "version": 12,
  "reason": "RFID_REVOKED"
}
```

`reason`: `PIN_CHANGED`, `RFID_CHANGED`, `RFID_REVOKED` hoặc `FULL_REFRESH`.
Thông điệp này chỉ báo phiên bản cần làm mới; adapter của module credentials
chịu trách nhiệm đóng gói snapshot phù hợp firmware. Không gửi PIN rõ, hash dùng
cho đăng nhập App hay secret MQTT trong payload/log.

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
hợp lệ từ Node 1. Backend lưu một lần theo `eventId` và đẩy thông báo duy nhất
đến `owner_account_id` của cửa nguồn.

## Tích hợp Backend

- MQTT adapter gọi `DoorSettingsService.requestCredentialsSync(...)` sau khi
  module người 1 đổi PIN/thêm hoặc thu hồi RFID thành công.
- ACK gọi `DeviceSyncAcknowledgementService`; alert gọi
  `HardwareAlertService` với `AuthenticatedDeviceIdentity` lấy từ credential/ACL,
  không dựng danh tính từ payload.
- `DeviceCommandPublisher` và `OwnerAlertNotifier` là outbound ports. Bean mặc
  định phát Spring application event; adapter hạ tầng lắng nghe event hoặc thay
  bean để publish MQTT và gửi push đúng tài khoản.
