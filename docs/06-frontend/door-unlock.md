# Mở và đóng cửa từ dashboard

Chạm thẻ cửa mở bảng điều khiển tham khảo [mockup](mockups/dashboard.html).
Vuốt thanh đến ít nhất 95% rồi thả để gửi lệnh UNLOCK cho cửa đang chọn.
Vuốt chưa đủ không gửi; thanh bị khóa khi chờ gửi/ACK hoặc chưa rõ kết quả.
**Nhiều cài đặt hơn** mở chi tiết cửa. App đọc lệnh gần nhất khi mở bảng và
thăm dò mỗi giây khi chờ; lỗi mạng cho kiểm tra lại, không tự gửi lại POST.
Đóng bảng không hủy lệnh đã nhận. Kết quả lệnh không xác nhận trạng thái vật lý.

Nút **Đóng cửa** mở xác nhận trước khi gửi action CLOSE. Hai nút bị khóa khi
đang chờ một lệnh hoặc chưa rõ kết quả. App hiển thị đúng kết quả mở/đóng từ
ACK Node 2, không báo thành công chỉ vì POST trả 202. Hủy xác nhận không gửi lệnh.
Lịch sử CLOSE và dòng lệnh gần nhất trên dashboard được ghi rõ là đóng cửa.

Mô hình hiện tại servo kéo cánh cửa, mở 180°/đóng 90°. Node 2 tự đóng sau
10 giây giữ mở, kể cả mất mạng, và không gia hạn timer khi nhận lệnh trùng.
CLOSE từ App hủy timer và đóng ngay. Báo nhiệt hủy timer/chặn đóng; sau khi hết
điều kiện cần đóng chủ động. Thời gian này riêng với ngưỡng cảnh báo cửa mở lâu,
chưa có trường điều chỉnh trên App. Xem trạng thái thực tế qua MC-38/monitoring.

## Kiểm thử

1. Nạp firmware Node 2 mới trước, rồi build backend:
   `docker compose up -d --build backend`; Flyway áp dụng tới V11, lệnh cũ giữ
   action UNLOCK. Không reset/xóa volume. Cập nhật App sau Backend.
2. Theo [hướng dẫn thiết bị](../05-backend/person1-person4-completion.md), cấp
   credential, đồng bộ cấu hình và activate Node 2 của cửa cần kiểm thử.
3. Chạy `python scripts/device-simulator.py <credential-file> --duration 600`.
   Node 2 gửi heartbeat và mặc định ACK EXECUTED. Dùng `--unlock-result REJECTED`
   hoặc `--unlock-result silent` để test từ chối/timeout. Kết quả lưu theo ID;
   CLOSE có `--close-result REJECTED`/`silent` riêng; simulator chỉ ACK, không
   mô phỏng việc servo thực sự tự đóng sau 10 giây.
   muốn thử kết quả khác phải tạo lệnh mới. Giả lập không tác động relay.
4. Chạy `mobile/tool/run-usb.ps1`, chọn cửa và vuốt; kiểm tra kết quả theo tùy
   chọn giả lập. Thử vuốt ngắn, Node offline, cửa chưa ACTIVE, sai chủ và MQTT
   ngắt: không được báo mở khóa thành công.

Xem [REST](../../contracts/api/door-unlock.md), [MQTT](../../contracts/mqtt/topics.md).
Firmware thật chưa triển khai; cần tích hợp contract, chống trùng và kiểm tra
hạn lệnh trước khi thử phần cứng.

Kiểm thử tự động:

```powershell
python -m unittest discover -s scripts/tests -p test_unlock_simulator.py
cd mobile
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

Trong `backend/`, chạy `./mvnw.cmd test` bằng JDK 21 trở lên. Bộ kiểm thử
UnlockApiTests dùng H2 và transport giả để kiểm tra REST/lưu lệnh/ACK; kiểm thử
simulator dùng MQTT client giả, không chứng minh kết nối broker hoặc relay thật.

## Giữ cửa mở khi tắt bảo mật

Công tắc bảo mật yêu cầu xác nhận trước khi gửi: tắt sẽ mở và giữ mở; bật lại sẽ đóng và yêu cầu thẻ/PIN. Khi chọn AUTOMATIC, panel vô hiệu hóa trượt mở và nút đóng, hướng dẫn bật bảo mật để đóng. Lưu cấu hình thành công chỉ xác nhận đã xếp hàng; kiểm tra ACK từng Node và MC-38. Báo nhiệt luôn ưu tiên mở khi được bật; báo cửa mở lâu vẫn là cờ độc lập.
