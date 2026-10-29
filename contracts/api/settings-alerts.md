# Settings and Alerts API

Tất cả endpoint yêu cầu Bearer token. Backend lấy Account từ token và trả `404`
cho cửa/cảnh báo thuộc tài khoản khác để không lộ sự tồn tại tài nguyên.

| Method | Path | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/doors/{doorId}/configuration` | Cấu hình và trạng thái ACK từng thiết bị |
| `PUT` | `/api/v1/doors/{doorId}/configuration` | Lưu, tăng version và xếp hàng gửi cấu hình trong cùng transaction |
| `GET` | `/api/v1/doors/{doorId}/sync-status` | Đọc lại trạng thái đồng bộ |
| `POST` | `/api/v1/doors/{doorId}/credentials-sync?reason=RFID_REVOKED` | Điểm tích hợp cho thay đổi PIN/RFID |
| `POST` | `/api/v1/doors/{doorId}/sync/retry` | Gửi lại cấu hình/PIN/RFID hiện tại, không tăng version; không có body |
| `GET` | `/api/v1/doors/{doorId}/delivery-status?page=&size=` | Lịch sử hàng đợi gửi cấu hình/credential, không trả payload bí mật |
| `GET` | `/api/v1/alerts?doorId=&status=&page=&size=` | Danh sách cảnh báo của chủ cửa |
| `GET` | `/api/v1/alerts/{alertId}` | Chi tiết cảnh báo |
| `PATCH` | `/api/v1/alerts/{alertId}/status` | `ACKNOWLEDGED` hoặc `RESOLVED` |
| `GET` | `/api/v1/notifications?unread=&page=&size=` | Hộp thông báo của Account trong App |
| `POST` | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc, không có body |

Giới hạn cấu hình: `maxFailedAttempts` 1–10, `lockoutSeconds` 10–3600,
`temperatureThreshold` 30–80°C, `openTimeoutSeconds` rỗng hoặc 5–3600.

Trạng thái cảnh báo chỉ đi từ `ACTIVE` sang `ACKNOWLEDGED` hoặc `RESOLVED`, và
từ `ACKNOWLEDGED` sang `RESOLVED`. Cảnh báo đã giải quyết không được mở lại.
Gửi lại cùng trạng thái trả kết quả hiện tại, không đổi thời điểm giải quyết.
`ACKNOWLEDGED` là xác nhận đã xem cảnh báo; `notifications.readAt` chỉ là đã đọc
thông báo. Hai thao tác này độc lập. Chưa có cơ chế tự giải quyết theo cảm biến.

## Cấu hình và đồng bộ

`configuration`, `sync-status` và `sync/retry` trả cùng cấu trúc `DoorConfigView`:
`doorId`, `maxFailedAttempts`, `lockoutSeconds`, `temperatureThreshold`,
`openTimeoutSeconds`, `version`, `credentialsVersion`, `updatedAt`, `devices`.
Mỗi thiết bị có `configState`, `credentialsState`, các version đã ACK và lifecycle.
`SYNCED` yêu cầu phiên bản đã ACK bằng phiên bản mong muốn; thiếu ACK là `UNKNOWN`,
khác phiên bản là `PENDING`. Credential không áp dụng cho Node 2.

Cấu hình được gửi cho thiết bị `PROVISIONED`, `ACTIVE`, `DISABLED`; bỏ qua
`RETIRED`. ACK hợp lệ được chấp nhận ở ba trạng thái này sau khi cửa đã claim,
để thiết bị hoàn tất setup trước khi kích hoạt. ACK không tự kích hoạt thiết bị,
không sửa heartbeat, không chứng minh trạng thái vật lý của khóa.

`DeviceCommandPublisher` ghi `integration_outbox` cùng transaction thay đổi DB.
Tiến trình gửi lấy tối đa 10 bản ghi/lượt; lease 2 phút, mặc định tối đa 8 lần thử,
có thời gian chờ tăng dần. Khi tiến trình chết, lease hết hạn cho phép thử lại.
Không có transport thì bản ghi giữ `PENDING`.

| Trạng thái gửi | Ý nghĩa |
| --- | --- |
| `PENDING` | Chờ transport/lần thử tiếp theo |
| `PROCESSING` | Đã được một worker nhận, chưa xác nhận gửi thành công |
| `SENT` | Transport đã gửi thành công; vẫn chờ ACK áp dụng từ Node |
| `FAILED` | Hết số lần tự thử; chủ cửa có thể thử lại |

`POST sync/retry` reset công việc `PENDING`/`FAILED` và xếp hàng snapshot hiện tại;
không can thiệp worker còn lease và không tăng version. Transport đọc snapshot
mới nhất dưới khóa Door khi gửi, tránh gửi retained state cũ sau state mới.
Mỗi mục `delivery-status` gồm `id`, `kind`, `deviceCode`, `status`, `attempts`,
`nextAttemptAt`, `lastError`, `createdAt`, `sentAt`. Không trả `payload` hoặc secret.
Phân trang theo mẫu `items`, `page`, `size`, `totalElements`, `totalPages`;
`page >= 0`, `1 <= size <= 100`.

## Cảnh báo và hộp thông báo

Cảnh báo phần cứng yêu cầu danh tính thiết bị đã xác thực và lifecycle `ACTIVE`.
`eventId` được trim, nhiệt độ chuẩn hóa hai chữ số thập phân và thời gian chuẩn
hóa mili giây. Gửi lại cùng sự kiện trả bản ghi cũ, không đánh giá lại ngưỡng
nhiệt độ mới và không tạo thêm thông báo. Dùng lại `eventId` cho Door, Node, loại,
nhiệt độ hoặc thời điểm khác bị từ chối `409`.

Cảnh báo mới và thông báo cho chủ cửa được ghi cùng transaction. Thông báo là
hộp thư trong App, chưa phải push nền Android/FCM. App có thể đọc lại khi mở
màn hình hoặc refresh. Một cảnh báo tạo tối đa một thông báo cho chủ cửa.

```json
{
  "id": 51,
  "alertId": 32,
  "doorId": 4,
  "title": "Cảnh báo nhiệt độ",
  "body": "Cửa trước: Cảnh báo nhiệt độ (55.50°C).",
  "createdAt": "2026-10-28T08:00:00.000",
  "readAt": null
}
```

Danh sách nằm trong `data.items` của envelope API chuẩn. `unread=true` chỉ trả
bản ghi chưa đọc. Đánh dấu đã đọc lặp lại giữ nguyên `readAt`; truy cập thông báo
của Account khác trả `404`. Các thời điểm không có offset là UTC theo quy ước DB.
