# Mở và đóng cửa từ App

Các API yêu cầu JWT Account ACTIVE sở hữu cửa; sai chủ trả 404. Dùng envelope
ApiResponse và Problem Details hiện có.

| Method | Path | Kết quả |
| --- | --- | --- |
| POST | `/api/v1/doors/{doorId}/commands` | `{requestId: UUID, action?: "UNLOCK" | "CLOSE"}` → 202 CommandView; thiếu action mặc định UNLOCK |
| GET | `/api/v1/doors/{doorId}/commands/latest` | 200 CommandView hoặc data null |
| GET | `/api/v1/doors/{doorId}/commands/{id}` | 200 CommandView tại đúng cửa |

CommandView: `id`, `doorId`, `action`, `status`, `createdAt`, `expiresAt`.
Thời gian UTC ISO-8601. Status: PENDING, SENT, UNKNOWN, EXECUTED, REJECTED,
FAILED, TIMED_OUT. SENT là Broker nhận; EXECUTED là Node xác nhận thực thi,
không phải cảm biến xác nhận vị trí khóa/cánh cửa.

Cửa phải ACTIVE, có đúng một Node 2 ACTIVE/ONLINE theo heartbeat và MQTT đang
kết nối. Một cửa chỉ có một lệnh chờ trong thời hạn; sai điều kiện trả 409.
Cùng requestId và action trả lại lệnh cũ; đổi action với cùng ID trả 409.
Hết hạn sau 15 giây. Không tự retry POST khi lỗi
mạng: tra cứu ID đã gửi hoặc lệnh gần nhất trước khi quyết định gửi lệnh mới.

Migration V7 tạo control_commands. Dispatcher chạy mỗi giây, publish không
retained. Gửi lỗi không rõ kết quả thành UNKNOWN, chờ ACK hoặc timeout, không
tự phát lại. QoS 1/crash vẫn có thể gửi trùng: Node phải chống trùng bền vững
theo commandId, kiểm tra expiresAt và không thực thi lại lệnh cũ.

Migration V11 thêm `control_commands.action`, giữ lệnh cũ là UNLOCK. CLOSE có
cùng kiểm tra chủ cửa, ACTIVE/ONLINE, hạn lệnh, ACK và chống trùng như UNLOCK.
ACK EXECUTED của CLOSE cập nhật commandedLockState=LOCKED; đây vẫn chỉ là
trạng thái lệnh, không phải cảm biến. Lịch sử CLOSE dùng reason CLOSE_<status>.

Mô hình servo kéo trực tiếp cánh cửa: mở 180°, đóng 90°. Firmware giữ mở 10 giây
sau khi hoàn tất lệnh UNLOCK rồi tự đóng tại chỗ, kể cả mất mạng. CLOSE từ App
đóng ngay và hủy timer. Báo nhiệt hủy timer, chặn CLOSE và giữ mở; sau hết báo
nhiệt phải đóng chủ động. Không dùng openTimeoutSeconds làm thời gian tự đóng.
Tự đóng không tạo command REST giả; xem trạng thái cánh cửa qua monitoring/MC-38,
không suy diễn từ lệnh gần nhất. Reset hủy timer và không tự quay servo khi boot.
