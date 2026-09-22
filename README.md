# Smart Door System

Monorepo cho hệ thống kiểm soát cửa thông minh, gồm Backend API, Mobile App,
firmware cho hai ESP32 và hạ tầng Docker.

## Thành phần

- `backend/`: REST API Spring Boot và MQTT integration.
- `mobile/`: ứng dụng Flutter.
- `firmware/node-auth/`: ESP32 xác thực và tương tác ngoài cửa.
- `firmware/node-control/`: ESP32 điều khiển khóa và cảnh báo trong nhà.
- `contracts/`: hợp đồng REST API, MQTT topic và JSON payload.
- `infrastructure/`: cấu hình MySQL và Mosquitto.
- `docs/`: tài liệu yêu cầu, kiến trúc, phần cứng và kiểm thử.

Xem yêu cầu hệ thống tại [`docs/requirement.md`](docs/requirement.md).

Quy trình CI/CD và phát hành backend được mô tả tại [`docs/ci-cd.md`](docs/ci-cd.md).

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
