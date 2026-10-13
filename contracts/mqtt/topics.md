# MQTT Topics

Nguồn chuẩn cho tên topic, chiều dữ liệu, QoS, retained message và quyền
publish/subscribe. Các topic cụ thể sẽ được chốt trong Sprint 1.

Contract phải tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../../docs/01-requirements/ownership-device-lifecycle-decisions.md).
Mọi topic/payload nghiệp vụ xác định cửa nguồn hoặc cửa đích và Node tương ứng.
ACL giới hạn theo danh tính thiết bị đã provision; Backend đối chiếu Door/Node,
không tin riêng ID trong payload. Node 2 chỉ nhận lệnh đúng cửa, cảnh báo chỉ
đến chủ cửa. Heartbeat không thay đổi lifecycle; thiết bị bị vô hiệu hóa/ngừng
sử dụng vẫn bị từ chối nghiệp vụ dù online. Không có message vân tay. Topic,
QoS, retained flag, schema và cơ chế cấp/thu hồi quyền còn phải đặc tả.
