# Settings and Alerts API

Tất cả endpoint yêu cầu Bearer token. Backend lấy Account từ token và trả `404`
cho cửa/cảnh báo thuộc tài khoản khác để không lộ sự tồn tại tài nguyên.

| Method | Path | Mô tả |
| --- | --- | --- |
| `GET` | `/api/v1/doors/{doorId}/configuration` | Cấu hình và trạng thái ACK từng thiết bị |
| `PUT` | `/api/v1/doors/{doorId}/configuration` | Lưu, tăng version và phát cấu hình |
| `GET` | `/api/v1/doors/{doorId}/sync-status` | Đọc lại trạng thái đồng bộ |
| `POST` | `/api/v1/doors/{doorId}/credentials-sync?reason=RFID_REVOKED` | Điểm tích hợp cho thay đổi PIN/RFID |
| `GET` | `/api/v1/alerts?doorId=&status=&page=&size=` | Danh sách cảnh báo của chủ cửa |
| `GET` | `/api/v1/alerts/{alertId}` | Chi tiết cảnh báo |
| `PATCH` | `/api/v1/alerts/{alertId}/status` | `ACKNOWLEDGED` hoặc `RESOLVED` |

Giới hạn cấu hình: `maxFailedAttempts` 1–10, `lockoutSeconds` 10–3600,
`temperatureThreshold` 30–80°C, `openTimeoutSeconds` rỗng hoặc 5–3600.

Trạng thái cảnh báo chỉ đi từ `ACTIVE` sang `ACKNOWLEDGED` hoặc `RESOLVED`, và
từ `ACKNOWLEDGED` sang `RESOLVED`. Cảnh báo đã giải quyết không được mở lại.
