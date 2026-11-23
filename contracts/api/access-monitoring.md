# Truy cập và giám sát cửa

GET `/api/v1/doors/{doorId}/access-logs?page=0&size=20&method=RFID&result=DENIED`
trả PageResponse trong envelope chuẩn; method/result tùy chọn. JWT phải sở hữu cửa,
sai chủ trả 404. Mỗi dòng: id, eventId, correlationId, method, result, reason,
cardholderName (nếu có), occurredAt, receivedAt. Không trả UID/PIN/hash.
GRANTED chỉ xác thực hợp lệ; EXECUTED mới là Node xác nhận thực thi lệnh.

GET `/api/v1/doors/{doorId}/monitoring` trả temperature, humidity, doorState
(OPEN/CLOSED/UNKNOWN), lockState (LOCKED/UNLOCKED/UNKNOWN), observedAt, receivedAt,
fresh (chỉ true khi mẫu <=60 giây, Node nguồn vẫn ACTIVE và ONLINE).
Chưa có mẫu trả null. App không suy trạng thái cảm biến từ ACK lệnh.

GET `/api/v1/doors/{doorId}/access-summary` trả cardholderCount và latestAccess
(dòng lịch sử gần nhất hoặc null), theo quyền sở hữu cửa.

## MQTT bổ sung

Tất cả events dùng signed envelope hiện có, QoS 1, retain=false:

- Node 1 → `events/access-request`: `{schemaVersion:1,eventId:UUID,method:"RFID"|"PIN",value:"...",occurredAt:UTC ISO}`.
  Chỉ ACTIVE, cửa ACTIVE; thời điểm tối đa 15 giây trước/5 giây sau hiện tại.
  Không lưu/log value. UUID là idempotency key toàn cục: lần đầu quyết định kết quả;
  retry cùng Node/method không đếm sai hoặc phát lệnh lần nữa. Không dùng lại ID
  cho lần quẹt/nhập mới. RFID/PIN không được gửi qua TCP ngoài môi trường local;
  triển khai thật bắt buộc TLS. Mặc định đếm sai riêng RFID/PIN, khóa theo cấu hình,
  đặt lại bộ đếm khi xác thực đúng hoặc hết thời hạn khóa. Không tự bỏ xác thực
  trong chế độ AUTOMATIC.
- Backend → Node 1 `commands/access-result`: `{schemaVersion:1,eventId,result,reason,lockedUntil,expiresAt}`.
  Kết quả xác thực không khẳng định mở cửa; không retained, outbox bền vững.
  Thông báo hết hạn được đánh dấu EXPIRED trong delivery-status, không báo SENT
  hoặc tự gửi lại sau reconnect.
- Backend → Node 2 `commands/alarm`: `{schemaVersion:1,eventId,expiresAt}` khi đạt ngưỡng sai.
  Không retained; Node chống trùng eventId, bỏ lệnh hết hạn. Giả lập chỉ ghi nhận còi.
- Node 2 → `events/telemetry`: `{schemaVersion:1,eventId:UUID,observedAt:UTC ISO,temperature:28.5,humidity:65,doorState:"CLOSED",lockState:"LOCKED"}`.
  Số đo có thể null nếu cảm biến chưa có. Nhiệt độ -40..125, độ ẩm 0..100;
  mẫu tối đa 60 giây trước/5 giây sau hiện tại. Chống trùng eventId, từ chối ID
  trùng nhưng payload khác; mẫu đến trễ không ghi đè mẫu mới hơn.

Định tuyến đúng Door/Node, lifecycle và credential vẫn được kiểm tra tại Backend.
Giả lập bổ sung thử RFID/PIN, gửi telemetry và nhận kết quả/còi; không chứng minh
relay, cảm biến, xác thực offline hoặc an toàn cháy trên phần cứng thật.
