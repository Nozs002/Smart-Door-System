# Kiểm tra bộ sơ đồ V13 — 16/11/2026

Phạm vi là cập nhật tài liệu theo source trong workspace. Không thay code
nghiệp vụ, migration hoặc dữ liệu MySQL trong đợt sửa sơ đồ.

| Nội dung | Kết quả |
| --- | --- |
| PlantUML Java 21, kiểm tra cú pháp | 14 source hợp lệ, thuộc 12 nhóm; có sơ đồ 6 |
| SVG | Sinh lại từ 14 source, đọc XML và kiểm tra không có hình báo lỗi |
| ERD đầy đủ | 22 bảng kể cả Flyway, 183 cột hiển thị, đủ 35 cạnh FK; đối chiếu metadata V13 |
| ERD rút gọn | Đủ 21 bảng nghiệp vụ, có device_inventory và installation_kits |
| Liên kết và anchor | 117 liên kết local/anchor hợp lệ trong index, hồ sơ, tham khảo kỹ thuật, đấu nối và biên bản này |
| Phần cứng | Đối chiếu GPIO NodeAuthApp.h, NodeControlApp.h và Node2Actuator.h |
| Nguồn riêng | Không đưa private header, credential, dữ liệu khách hàng hoặc JAR vào tài liệu |

Luồng được đối chiếu với OnboardingService, DeviceEnrollment, các màn hình
technician/door_setup, UnlockService, AccessAuthenticationService,
SettingsDeviceReadinessPolicy, Docker Compose và hợp đồng API/MQTT. Sơ đồ 6
đối chiếu thêm ControlState, Node2Actuator, HardwareAlertService,
AccessAuthenticationService, AlertService và NotificationService; phân biệt
PUBACK bên Node với transaction Backend và quyền đánh dấu trên App.
Readiness kích hoạt không đòi heartbeat còn mới; điều khiển kiểm tra ONLINE
riêng. Manifest reload không được mô tả như bằng chứng ngắt phiên MQTT cũ.

Đã xem ảnh render của các sơ đồ khối, miền, ERD slide, bàn giao, Wi-Fi,
cảnh báo/mất kết nối, trạng thái, MQTT, triển khai và đấu nối; chỉnh độ rộng chữ trong các sơ đồ
tuần tự để dễ đọc. SVG là bản xem và phóng to chính; PNG xem trước chỉ tạo
trong thư mục local bị Git bỏ qua.

Kết quả ứng dụng trước đợt này nằm ở
[tài liệu onboarding](../../05-backend/device-onboarding.md). Không chạy lại
toàn bộ test Backend/Flutter chỉ vì đổi sơ đồ. Cài SoftAP trên điện thoại,
nguồn tải, cảm biến, servo và offline không được coi đã nghiệm thu từ kết quả
render hoặc kiểm thử giả lập.

[Hồ sơ thiết kế và 12 nhóm sơ đồ](../system-design-review.md) ·
[Bảng đấu nối](../hardware-wiring.md) ·
[Khóa và CHECK V13](database-constraints-v13.md).
