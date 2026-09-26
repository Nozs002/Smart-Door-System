## Thông tin chung của dự án

- **Tên dự án**: Hệ thống Cửa Thông minh (Smart Access Control IoT).
- **Mục tiêu**: Xây dựng nguyên mẫu (sa bàn) hệ thống kiểm soát ra vào bảo mật, tích hợp công nghệ IoT để quản lý, giám sát và điều khiển từ xa qua điện thoại.

## Kiến trúc & Tech Stack

- **Thiết bị IoT (Phần cứng)**: Sử dụng vi điều khiển ESP32 (lập trình C/C++ qua PlatformIO/Arduino).
  - **Ngoại vi**: Module RFID (RC522), Bàn phím số (Keypad), Cảm biến siêu âm (JSN-SR04T), Cảm biến nhiệt độ (DHT11), Màn hình OLED, Còi báo động (Buzzer), Động cơ (Servo/Solenoid khóa chốt).
- **Giao thức Mạng**: Kết nối Wi-Fi, giao tiếp thời gian thực qua MQTT (Mosquitto Broker) bằng định dạng JSON. Gửi tín hiệu "Heartbeat" định kỳ để báo trạng thái online/offline.
- **Backend & Cơ sở dữ liệu**: RESTful API viết bằng Spring Boot (Java). Cơ sở dữ liệu quan hệ MySQL. API bảo mật bằng JWT và mật khẩu băm Bcrypt.
- **Frontend (Mobile App)**: Sử dụng Flutter (chuyển từ Web sang Mobile App để tối ưu trải nghiệm người dùng, điều khiển từ xa và nhận thông báo đẩy).

## Môi trường triển khai & Docker

### Thành phần bắt buộc dùng Docker

- **MySQL Database**: Sử dụng image MySQL chính thức.
- **Mosquitto (MQTT Broker)**: Sử dụng image `eclipse-mosquitto`.
- **Backend API**: Đóng gói mã nguồn Backend thành Docker image. Trong dự án hiện tại, Backend sử dụng Spring Boot; Node.js có thể là phương án thay thế nếu nhóm thay đổi công nghệ. Backend phải chạy cùng MySQL và Mosquitto trong môi trường Docker, ưu tiên điều phối bằng Docker Compose.

### Thành phần không dùng Docker

- **Firmware ESP32**: Viết bằng C/C++ qua PlatformIO/Arduino và nạp trực tiếp vào vi điều khiển bằng cáp USB. Firmware không được đóng gói hoặc chạy bằng Docker.
- **Mobile App**: Ứng dụng hiện tại sử dụng Flutter và chạy trên máy ảo điện thoại (Emulator) hoặc thiết bị thật. Nếu nhóm chuyển sang React Native thì ứng dụng vẫn chạy theo cách này, không chạy trong Docker.

## Tính năng cốt lõi (In Scope)

- **Xác thực tại chỗ**: Mở cửa bằng thẻ từ RFID và mật khẩu (Mã PIN).
- **Điều khiển từ xa**: Nút "Mở cửa khẩn cấp" và xem trạng thái đóng/mở trực tiếp trên Mobile App.
- **Đánh thức thông minh (Wake-up)**: Cảm biến siêu âm phát hiện người đến gần (40-60cm) kích hoạt hệ thống/bật sáng màn hình để tiết kiệm điện.
- **Bảo mật & Cảnh báo**:
  - Nhập sai mật khẩu/thẻ quá 3 lần: Khóa bàn phím tạm thời (10-60s), bật còi hú, gửi cảnh báo lên App.
  - Cửa mở quá lâu (ví dụ: 30s) hoặc có lực cạy phá: Bật còi hú.
- **Báo cháy & An toàn**: Cảm biến nhiệt độ phát hiện > 50°C sẽ hú còi và tự động rút chốt mở cửa để thoát hiểm.
- **Quản lý vòng đời (Lifecycle)**: Backend theo dõi trạng thái thiết bị, quản lý cấp quyền (Admin/User), cấp phát mã thẻ và lưu trữ 100% lịch sử ra vào (Access Logs).
- **Cơ chế Offline**: Rớt mạng Wi-Fi, hệ thống cửa vẫn hoạt động bình thường với thẻ RFID và Keypad. Tự động kết nối lại khi có mạng.

## Giới hạn hệ thống (Out of Scope)

- **Không làm AI/Camera**: Không nhận diện khuôn mặt (Face ID), không chuông cửa màn hình (Video Doorbell). Tránh quá tải vi điều khiển và đảm bảo tiến độ.
- **Không lắp ráp thực tế**: Chỉ thi công trên mô hình sa bàn, không đục phá/lắp lên cửa gỗ hoặc kính thật.
- **Không tối ưu pin sâu**: Dùng nguồn Adapter cắm điện trực tiếp (có UPS/Pin sạc dự phòng duy trì 1-2h khi mất điện). Không làm tính năng chạy bằng pin tiểu AA tối ưu năng lượng như khóa thương mại.

## Phân công vai trò (Vertical Slicing)

Bốn thành viên chia việc theo cụm tính năng từ dưới lên trên:

- **Tuấn (Kiến trúc & Vòng đời)**:
  - Viết firmware kết nối Wi-Fi/MQTT tự động Reconnect.
  - Viết Backend API quản lý vòng đời thiết bị (Onboarding mạch mới, kiểm tra trạng thái Online/Offline).
- **Sơn (Xác thực & Bảo mật)**:
  - Lập trình ESP32 để đọc thẻ RFID, Keypad.
  - Viết Backend API xử lý đăng nhập (JWT) và logic kiểm tra tính hợp lệ của mã thẻ bằng MySQL.
- **Minh (Điều khiển & Cảnh báo - Kỹ sư phần cứng)**:
  - Đi dây, làm sa bàn.
  - Lập trình điều khiển Relay/Khóa, cảm biến nhiệt độ và cảm biến siêu âm.
  - Viết Backend API lưu Log lịch sử và Rule-engine đếm số lần sai mật khẩu.
- **Hà (Mobile App UI/UX)**:
  - Thiết lập màn hình OLED hiển thị thông báo.
  - Lập trình toàn bộ Mobile App (Flutter) để gọi API, làm giao diện đăng nhập, điều khiển cửa và xem lịch sử.

## Lộ trình triển khai

- **Sprint 1 (Tuần 1-2)**: Chốt Database ERD, JSON Contract MQTT. Hàn mạch, ESP32 kết nối Wi-Fi gửi dữ liệu giả lên MQTT. Khởi tạo Base Project (Backend & Mobile).
- **Sprint 2 (Tuần 3-4)**: ESP32 đọc thẻ RFID. Backend hoàn thành API Đăng nhập và CRUD Thiết bị. App hoàn thành màn hình đăng nhập.
- **Sprint 3 (Tuần 5-6) - MVP**: Ghép nối hoàn chỉnh luồng nghiệp vụ (Quẹt thẻ $\rightarrow$ Gửi MQTT $\rightarrow$ Backend kiểm tra $\rightarrow$ Mở khóa $\rightarrow$ App cập nhật lịch sử).
- **Sprint 4 (Tuần 7-8)**: Hoàn thiện các luồng cảnh báo (sai mật khẩu, báo cháy) và xử lý mượt mà kịch bản rớt mạng (Offline mode).
- **Sprint 5 (Tuần 9-10)**: Đóng băng code (Code Freeze). Làm đẹp sa bàn, test lỗi, viết báo cáo và quay video Demo.

## Cấu trúc Hardware Nodes

### Node 1: Trạm Tương tác & Xác thực

Đóng vai trò giao tiếp trực tiếp với người dùng muốn đi vào. Lắp đặt ở mặt ngoài cửa, tuyệt đối không kết nối dây trực tiếp vào chốt khóa cửa để đảm bảo an toàn nếu bị đập phá.

- **Linh kiện kết nối**:
  - Đầu đọc thẻ từ RFID (RC522)
  - Bàn phím số (Keypad)
  - Màn hình OLED
  - Cảm biến khoảng cách siêu âm (JSN-SR04T)
- **Nhiệm vụ chính**:
  - Dùng cảm biến siêu âm quét liên tục. Khi có người đến gần (40-60cm), ESP32 "thức dậy", bật sáng màn hình và bàn phím.
  - Thu thập mã thẻ RFID hoặc dãy mật khẩu.
  - Đóng gói dữ liệu thành JSON và đẩy lên topic MQTT (Ví dụ: `door/node1/auth`) về Backend kiểm tra.

### Node 2: Trạm Điều khiển & Cảnh báo an ninh

Lắp đặt an toàn bên trong nhà, làm nhiệm vụ trực tiếp điều khiển đóng/mở cánh cửa.

- **Linh kiện kết nối**:
  - Mạch Relay (cấp điện đóng/mở Khóa chốt từ Solenoid)
  - Cảm biến nhiệt độ, độ ẩm (DHT11)
  - Còi báo động (Buzzer)
  - Nút nhấn mở cửa cơ học (Exit button)
- **Nhiệm vụ chính**:
  - **Lắng nghe lệnh**: Subscribe topic MQTT từ Backend. Khi Backend kiểm tra mã thẻ từ Node 1 hợp lệ, Backend gửi lệnh "Mở khóa", Node 2 kích hoạt Relay rút chốt cửa.
  - **Báo cháy tự động**: Cảm biến DHT11 đo nhiệt độ phòng liên tục. Nếu vượt 50°C, Node 2 tự động hú còi Buzzer, kích hoạt Relay mở cửa thoát hiểm, bắn cảnh báo lên MQTT để App nhận thông báo đẩy.
  - **Báo động xâm nhập**: Nhận lệnh từ Backend để hú còi khi Node 1 bị nhập sai mật khẩu quá 3 lần.
