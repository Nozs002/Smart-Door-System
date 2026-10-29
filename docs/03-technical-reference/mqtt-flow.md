# MQTT Flow

Mô tả luồng xác thực, điều khiển cửa, heartbeat và cảnh báo qua MQTT.

Luồng mục tiêu tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md):

1. Provision cấp danh tính và quyền Broker cho từng Node của bộ cửa.
2. Node gửi sự kiện/heartbeat; Broker kiểm tra ACL, Backend đối chiếu danh tính
   với Door/Node được đăng ký, không tin riêng Door ID trong payload.
3. Backend xác thực RFID/PIN đúng cửa và gửi lệnh tới Node 2 của cửa đích.
   Lệnh từ App phải kiểm tra Account sở hữu Door trước khi publish.
4. Log/cảnh báo gắn với cửa nguồn; thông báo chỉ gửi đúng chủ sở hữu.
5. Heartbeat cập nhật kết nối từng Node; không ghi đè lifecycle. Node bị vô
   hiệu hóa/ngừng sử dụng không được xử lý nghiệp vụ dù còn kết nối.

Topic, payload và chính sách QoS/retained chưa được chốt; xem [MQTT contract](../../contracts/mqtt/topics.md).
