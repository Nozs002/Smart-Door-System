# Xác thực, lịch sử ra vào và giám sát cửa

Backend nhận yêu cầu RFID/PIN từ Node 1 qua MQTT đã xác thực, kiểm tra thẻ/PIN,
đếm sai riêng từng phương thức và khóa tạm theo cấu hình cửa. Xác thực đúng đặt
lại bộ đếm; hết thời gian khóa cho phép thử lại. Thử trong thời gian khóa không
tăng bộ đếm. Khi đạt ngưỡng, ghi cảnh báo/inbox và gửi lệnh còi mô phỏng đến Node 2.
Kết quả xác thực được gửi lại Node 1 qua outbox; không lưu hoặc log giá trị PIN/UID.

RFID/PIN hợp lệ tạo lệnh mở khóa dùng cùng dispatcher và cơ chế ACK như lệnh
từ App. Nếu Node 2 offline hoặc chưa có MQTT, giữ lịch sử GRANTED với lý do
CONTROL_UNAVAILABLE; không tự mở lại khi mạng phục hồi. Retry cùng eventId
không xác thực, đếm sai hoặc mở khóa lần hai. EXECUTED/FAILED/timeout có dòng
lịch sử riêng cùng correlationId. Lệnh App cũng ghi nhật ký yêu cầu và kết quả.

Dashboard có **Lịch sử ra vào** của cửa đang chọn, lọc phương thức/kết quả,
tải thêm, kéo làm mới và thử lại khi lỗi. Đây là lịch sử truy cập, tách khỏi
**Lịch sử thiết bị**. Tên người giữ thẻ lấy từ hồ sơ thuộc Account hiện tại,
kể cả khi hồ sơ đã vô hiệu hóa; không hiển thị UID. Tổng số người giữ thẻ đang
hoạt động có phạm vi Account, còn lượt truy cập gần nhất có phạm vi cửa.

Dashboard đọc telemetry và tổng quan truy cập mỗi 10 giây khi App hoạt động;
dừng khi vào nền. Node 2 gửi nhiệt độ/độ ẩm, trạng thái cánh cửa và chốt độc lập.
Mẫu quá 60 giây hoặc Node nguồn không ACTIVE/ONLINE được coi là cũ; App ẩn các
giá trị hiện tại và ghi rõ thời điểm mẫu cũ. ACK mở khóa không sửa telemetry.
Không có mẫu hoặc cảm biến tương ứng thì hiển thị chưa có dữ liệu.

## Chạy thử

1. Khởi động Docker Desktop. Build Backend: `docker compose up -d --build`.
   Flyway tự áp dụng đến V10; không xóa volume. V10 sửa ràng buộc hàng đợi để
   nhận kết quả RFID/PIN và lệnh còi. Với DB có dữ liệu quan trọng,
   sao lưu theo [hướng dẫn database](../03-technical-reference/database-setup.md).
   Kiểm tra bằng `python scripts/db-check.py --expect-version 10`.
2. Nếu đã cấp credential trước đó, chạy `python scripts/setup-mqtt.py configure`
   để bổ sung ACL cho access-request, telemetry và command-ack. Lệnh này restart
   Broker; không cấp lại/rotate credential chỉ để sửa ACL.
3. Đồng bộ/activate hai Node theo [hướng dẫn hiện có](../05-backend/person1-person4-completion.md).
4. Chạy Node 2 trước:

   ```powershell
   python scripts/device-simulator.py <node2-credential-file> --duration 600 --temperature 28.5 --humidity 65 --door-state CLOSED --lock-state LOCKED
   ```

5. Chạy Node 1 và nhập PIN cửa khi được hỏi (ký tự được ẩn):

   ```powershell
   python scripts/device-simulator.py <node1-credential-file> --duration 120 --access-method PIN --retry-access
   ```

   Dùng `--access-method RFID` để thử thẻ. `--attempts 3 --attempt-interval 2`
   gửi ba lần thử riêng để kiểm tra ngưỡng mặc định. `--retry-access` gửi trùng
   mỗi lần thử, dùng cùng ID để kiểm tra không đếm/mở trùng. Không truyền PIN/UID
   qua đối số dòng lệnh. Mở App xem lịch sử, cảnh báo và số đo; đổi cửa để kiểm
   tra không trộn dữ liệu. Dừng Node 2, chờ >60 giây để thấy dữ liệu cảm biến cũ.
6. Kiểm thử tích hợp tự tạo fixture local riêng:

   ```powershell
   python scripts/person14-smoke.py --mqtt
   ```

   Script kiểm tra cả PIN/RFID → ACK → lịch sử, yêu cầu trùng, khóa tạm/còi,
   telemetry và sai chủ cửa. Script giữ fixture và báo cáo riêng trong `.local/`,
   không thay mật khẩu Account có sẵn. Không công khai báo cáo chứa credential.

## Kiểm thử tự động

- Backend: `./mvnw.cmd test` trong `backend/` (JDK 21+).
- Flutter: `flutter analyze`, `flutter test`, `flutter build apk --debug` trong `mobile/`.
- Simulator: `python -m unittest discover -s scripts/tests` tại thư mục gốc.

[REST/MQTT chi tiết](../../contracts/api/access-monitoring.md).
Firmware thật, xác thực offline giữa hai ESP32, relay/cảm biến/còi thực tế và
push Android khi App đóng vẫn chờ cấu hình phần cứng/Firebase. Mô phỏng không
chứng minh chức năng thoát hiểm hoặc trạng thái phần cứng thực tế.

## Kết quả kiểm tra ngày 02/11/2026

- `mvnw.cmd test`: 191 test đạt, không lỗi/skip; kiểm thử tích hợp dùng H2 và
  transport giả, không phải MySQL/Mosquitto thật.
- `flutter analyze --no-pub`: không có vấn đề; `flutter test --no-pub`: 65 test đạt.
- `flutter build apk --debug --no-pub`: thành công.
- `python -m unittest discover -s scripts/tests`: 7 test đạt; các script thay đổi
  cũng đã qua `py_compile`. `git diff --check` đạt.
- Chưa chạy smoke với broker/MySQL thật và chưa xác nhận migration V9 trên MySQL:
  Docker Engine chưa hoạt động dù đã thử khởi động Docker Desktop.
- Chưa kiểm tra giao diện trên Android thật/emulator: `adb devices` không có
  thiết bị. Widget test đã kiểm tra lịch sử, bộ lọc, phân trang, dữ liệu cũ và
  dừng polling khi App vào nền.

## Kiểm chứng bổ sung ngày 03/11/2026

- V10 sửa CHECK loại thông điệp của outbox, vốn khiến MySQL từ chối kết quả
  xác thực và lệnh còi dù test H2 đạt. Đã tái hiện lỗi V9 và kiểm tra V10 giữ
  dữ liệu cũ cùng các ràng buộc khác bằng `scripts/db-fresh-check.py`.
- Đã chạy `person14-smoke.py --mqtt` thành công qua MySQL/Mosquitto thật:
  PIN/RFID → ACK thực thi → lịch sử, chống trùng, khóa tạm/còi, telemetry,
  cảnh báo/inbox và quyền sở hữu. Log: `.local/migration-v10-smoke.log`.
- Backend local đã áp dụng V10 và health UP. Đây vẫn là Node giả lập;
  kiểm thử firmware/phần cứng và thao tác Android thật là bước riêng.
