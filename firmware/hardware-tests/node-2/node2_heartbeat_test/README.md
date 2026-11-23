# Node 2: heartbeat, đồng bộ cấu hình và thử mở chốt

Sketch Arduino dùng ArduinoJson 7, ArduinoMqttClient, ESP32Servo và ESP32 Arduino core
(WiFi, Preferences, mbedTLS). Sao chép `heartbeat_config.h.example` thành
`heartbeat_config.local.h` rồi điền Wi-Fi và credential Node 2. File local được
Git bỏ qua; không điền secret vào file mẫu.

Đây là thử nghiệm giao thức: ACK xác nhận snapshot hợp lệ đã lưu vào NVS và
nạp vào bộ nhớ. Sketch điều khiển SG90 và đọc MC-38, chưa đọc DHT11 hay thực thi cảnh báo;
không coi trạng thái đồng bộ là bằng chứng phần cứng sẵn sàng vận hành.

## Kiểm tra trên ESP32

1. Upload sketch, mở Serial Monitor 115200 baud.
2. Trong App gửi lại cấu hình đúng cửa. Chờ log lưu cấu hình và gửi `sync-ack`.
3. Kiểm tra `devices.applied_config_version` của đúng thiết bị bằng phiên bản
   mong muốn trong `door_configs.version`. Node 1 có thể vẫn chưa đồng bộ.
4. Reset ESP32: log phải báo khôi phục cấu hình; snapshot retained cùng phiên
   bản được ACK lại mà không ghi NVS thêm.
5. Thay đổi cấu hình trên App: phiên bản mới phải được lưu và ACK.

Snapshot cũ hoặc sai kiểu/giới hạn không được ACK. Cùng phiên bản nhưng khác
nội dung bị từ chối. Snapshot lưu gắn với Door/deviceCode/credentialId, không
tái sử dụng sau thay danh tính. Không ghi lại khóa ký trong snapshot NVS.

## Thử mở từ App

1. Cài **ArduinoMqttClient** của Arduino qua Library Manager. Sketch không còn
   dùng PubSubClient vì cần cờ retained và publish QoS 1.
2. Servo SG90: Signal GPIO18, nguồn 5V phù hợp, chung GND với ESP32.
   Góc người dùng đã kiểm tra: mở 180°, đóng 90°.
3. Upload cả thư mục sketch (giữ `Node2Unlock.h` cạnh file `.ino`). Reset không
   tự điều khiển servo. Đợi NTP, heartbeat và đồng bộ cấu hình.
4. Trên App chọn đúng cửa ACTIVE, Node 2 ACTIVE/ONLINE rồi bấm mở cửa.
5. Serial phải báo mở 180°, lưu `EXECUTED`, gửi `command-ack`. App phải nhận
   kết quả `EXECUTED` trước hạn 15 giây. Đây là xác nhận phát lệnh servo,
   không phải cảm biến xác nhận cửa/chốt thực sự đã mở.
6. Để đóng thử, chọn Newline trong Serial Monitor, nhập `CLOSE`. Servo về 90°.
   Chưa tự đóng sau một khoảng thời gian; chưa gửi telemetry về trạng thái này.

Mỗi event gửi QoS 1, retain=false theo [MQTT contract](../../../../contracts/mqtt/topics.md).
Broker ACK không đồng nghĩa Backend đã chấp nhận nghiệp vụ; luôn kiểm tra App/DB.
ACK được thử lại khi gửi lỗi và còn hạn. Khi mất mạng hoặc reset, không phát
lại thao tác servo. Có thể gửi lại kết quả đã lưu khi nhận commandId trùng.

Lệnh retained, sai JSON/UUID/schema/action, hết hạn, sai UTC hoặc khi chưa có
cấu hình/NTP hợp lệ không được quay servo. Giờ được coi hợp lệ trong tối đa
hai giờ từ lần NTP thành công gần nhất. Timestamp hỗ trợ phần giây thập phân.
Journal NVS giữ tối đa 32 commandId/kết quả; chỉ tái sử dụng mục đã hết hạn.
Nếu bộ nhớ lỗi/đầy, không thực hiện lệnh mới. Journal khác danh tính hoặc hỏng
không được tự xóa; cần kiểm tra và xử lý khi provision lại thiết bị.

Lưu trạng thái bảo thủ REJECTED trước khi quay, rồi lưu EXECUTED sau 700 ms.
Nếu mất điện giữa hai lần lưu, chốt có thể đã di chuyển nhưng kết quả vẫn là
REJECTED/không chắc chắn: không replay để cố đạt trạng thái. Thời gian chờ
700 ms là thời gian ổn định dùng cho thử nghiệm, không đo vị trí servo.

## Các ca cần thử trên mô hình

- Lệnh mới còn hạn: quay 180° và ACK thành công.
- Gửi lại cùng commandId trước/sau reset: chỉ ACK, không quay lại.
- Lệnh retained/hết hạn/sai action hoặc chưa có NTP: không quay.
- Ngắt mạng ngay sau nhận lệnh: hoàn tất lưu kết quả tại chỗ, không quay lại
  khi kết nối phục hồi; ACK chỉ có ý nghĩa nếu còn trong hạn backend.
- Đóng thử bằng `CLOSE`: quay 90°; heartbeat và cấu hình vẫn hoạt động.

## MC-38 và telemetry

- Một dây MC-38 vào GPIO16, dây còn lại GND. Dùng INPUT_PULLUP;
  nam châm gần = LOW/CLOSED, tách ra = HIGH/OPEN, đã đối chiếu test thực tế.
- Lọc rung 50 ms. Gửi mẫu có chữ ký QoS 1, retain=false sau thay đổi trạng thái,
  sau reconnect và định kỳ 20 giây; giới hạn thử gửi tối đa một lần mỗi giây.
- Mỗi lần thử gửi lấy mẫu hiện tại với UUID mới và observedAt UTC mili giây.
  Không gửi lại các trạng thái cũ trong thời gian mất mạng.
- temperature/humidity là null vì chưa tích hợp DHT11. lockState luôn UNKNOWN:
  MC-38 không đo vị trí chốt; lệnh servo/ACK không phải phản hồi cảm biến khóa.
- Mở trang giám sát cửa trong App; đưa/tách nam châm và chờ chu kỳ tải của App.
  Có thể kiểm tra bảng `door_telemetry` hoặc API GET `/api/v1/doors/5/monitoring`
  bằng tài khoản chủ cửa. Mẫu chỉ fresh khi Node 2 ACTIVE/ONLINE và còn mới.
- Nếu chưa thấy dữ liệu, xem Serial `telemetry: ...` và log backend. Khi mất
  mạng quá 60 giây, dữ liệu không được hiển thị như trạng thái mới.
