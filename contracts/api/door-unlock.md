# Mở khóa từ App

Các API yêu cầu JWT Account ACTIVE sở hữu cửa; sai chủ trả 404. Dùng envelope
ApiResponse và Problem Details hiện có.

| Method | Path | Kết quả |
| --- | --- | --- |
| POST | `/api/v1/doors/{doorId}/commands` | `{requestId: UUID}` → 202 CommandView, action luôn UNLOCK |
| GET | `/api/v1/doors/{doorId}/commands/latest` | 200 CommandView hoặc data null |
| GET | `/api/v1/doors/{doorId}/commands/{id}` | 200 CommandView tại đúng cửa |

CommandView: `id`, `doorId`, `action`, `status`, `createdAt`, `expiresAt`.
Thời gian UTC ISO-8601. Status: PENDING, SENT, UNKNOWN, EXECUTED, REJECTED,
FAILED, TIMED_OUT. SENT là Broker nhận; EXECUTED là Node xác nhận thực thi,
không phải cảm biến xác nhận vị trí khóa/cánh cửa.

Cửa phải ACTIVE, có đúng một Node 2 ACTIVE/ONLINE theo heartbeat và MQTT đang
kết nối. Một cửa chỉ có một lệnh chờ trong thời hạn; sai điều kiện trả 409.
Cùng requestId trả lại lệnh cũ. Hết hạn sau 15 giây. Không tự retry POST khi lỗi
mạng: tra cứu ID đã gửi hoặc lệnh gần nhất trước khi quyết định gửi lệnh mới.

Migration V7 tạo control_commands. Dispatcher chạy mỗi giây, publish không
retained. Gửi lỗi không rõ kết quả thành UNKNOWN, chờ ACK hoặc timeout, không
tự phát lại. QoS 1/crash vẫn có thể gửi trùng: Node phải chống trùng bền vững
theo commandId, kiểm tra expiresAt và không thực thi lại lệnh cũ.
