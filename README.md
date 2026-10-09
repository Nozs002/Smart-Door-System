# Smart Door System

Monorepo cho hệ thống kiểm soát cửa thông minh, tập trung vào quản lý vòng đời
thiết bị và vận hành nhiều cửa. Hệ thống cho phép đăng ký cửa, onboarding thiết
bị mới, gán thiết bị vào từng cửa và theo dõi trạng thái thiết bị trong suốt quá
trình sử dụng.

Nguyên mẫu phần cứng hiện tại chỉ triển khai một cửa với hai ESP32. Backend,
Mobile App và mô hình dữ liệu vẫn được thiết kế theo hướng nhiều cửa để có thể
đăng ký và đưa một cửa mới vào vận hành mà không phải thay đổi phạm vi hệ thống.

## Thành phần

- `backend/`: REST API Spring Boot và MQTT integration.
- `mobile/`: ứng dụng Android viết bằng Flutter.
- `firmware/node-auth/`: ESP32 xác thực và tương tác ngoài cửa.
- `firmware/node-control/`: ESP32 điều khiển khóa và cảnh báo trong nhà.
- `contracts/`: hợp đồng REST API, MQTT topic và JSON payload.
- `infrastructure/`: cấu hình MySQL và Mosquitto.
- `docs/`: tài liệu yêu cầu, kiến trúc, phần cứng và kiểm thử.

Xem yêu cầu hệ thống tại [`docs/01-requirements/requirement.md`](docs/01-requirements/requirement.md).

Thành viên mới xem checklist thiết lập môi trường tại [`docs/00-getting-started/01-getting-started.md`](docs/00-getting-started/01-getting-started.md).

Xem toàn bộ tài liệu tại [`docs/00-INDEX.md`](docs/00-INDEX.md). Quy trình CI/CD và phát hành backend được mô tả tại [`docs/08-process/ci-cd.md`](docs/08-process/ci-cd.md).

## Giao diện quản lý MySQL

Sau khi tạo file `.env` từ `.env.example`, khởi động các dịch vụ:

```powershell
docker compose up -d
```

Mở phpMyAdmin tại <http://localhost:8081> và đăng nhập bằng:

- Server: `mysql`
- Username: giá trị `MYSQL_USER` trong file `.env`
- Password: giá trị `MYSQL_PASSWORD` trong file `.env`

Có thể dùng tài khoản `root` cùng `MYSQL_ROOT_PASSWORD` khi thực sự cần quyền
quản trị toàn bộ database.
