---
title: Cấu hình môi trường Backend
type: reference
version: 1.0
lang: vi
tags: [backend, configuration, profiles, security]
updated: 2026-10-03
---

# Cấu hình môi trường Backend

## 1. Mục tiêu

Backend tách cấu hình theo ba Spring profile `local`, `test` và `prod`. Cấu hình
chung nằm trong `application.yml`; mỗi môi trường chỉ ghi đè giá trị thực sự
khác biệt.

Mọi cấu hình thuộc namespace `app` được bind vào `AppProperties` và validate khi
Spring khởi động. Cấu hình thiếu hoặc sai kiểu làm ứng dụng dừng sớm thay vì chỉ
phát sinh lỗi khi xử lý request.

## 2. Cấu trúc file

```text
backend/src/main/resources/
├── application.yml
├── application-local.yml
├── application-test.yml
└── application-prod.yml
```

| File | Trách nhiệm |
| --- | --- |
| `application.yml` | Tên ứng dụng, graceful shutdown, JPA/Flyway, error safety, Actuator và log correlation ID. |
| `application-local.yml` | MySQL/MQTT local hoặc Docker, local placeholders và log DEBUG cho code dự án. |
| `application-test.yml` | H2 độc lập, Flyway tắt tạm thời, credential chỉ dùng trong test và log gọn. |
| `application-prod.yml` | Biến môi trường bắt buộc, pool database hữu hạn và log production an toàn. |

Profile mặc định là `local`. Môi trường production phải đặt rõ
`SPRING_PROFILES_ACTIVE=prod`; không được phụ thuộc vào profile mặc định.

## 3. Typed configuration

`AppProperties` quản lý ba nhóm:

```text
app.jwt.*
app.mqtt.*
app.cors.*
```

Validation hiện tại yêu cầu:

- JWT issuer và audience không rỗng;
- JWT secret dài tối thiểu 32 ký tự;
- thời hạn access token có giá trị;
- MQTT host, username, password không rỗng;
- MQTT port trong khoảng `1..65535`;
- connection timeout có giá trị;
- danh sách CORS origin không rỗng.

Khi thêm cấu hình ứng dụng, phải bổ sung vào `AppProperties` thay vì đọc biến
môi trường trực tiếp trong service.

## 4. Profile `local`

Profile này hỗ trợ hai cách chạy:

1. Backend chạy trên máy, kết nối MySQL và Mosquitto ở `localhost`.
2. Backend chạy bằng Docker Compose, các biến trong Compose ghi đè host thành
   service `mysql` và `mosquitto`.

Thiết lập ban đầu:

```powershell
Copy-Item .env.example .env
docker compose up -d
```

Các giá trị `change_me` và local secret chỉ là placeholder phát triển, không
được dùng ở staging hoặc production.

## 5. Profile `test`

Test phải khai báo `@ActiveProfiles("test")`. Profile này:

- dùng H2 in-memory ở MySQL compatibility mode;
- không dùng credential hoặc database production;
- tắt Flyway tạm thời vì Backend chưa có migration trong `db/migration`;
- dùng TTL và timeout ngắn phục vụ test;
- không yêu cầu Mosquitto đang chạy chỉ để load application context.

H2 chỉ phù hợp cho context/unit/slice test. Query và migration quan trọng vẫn
phải được kiểm tra bằng MySQL tương thích production khi Flyway được triển khai.

## 6. Profile `prod`

Các biến sau không có giá trị mặc định và bắt buộc được cấp từ secret store hoặc
môi trường triển khai:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWT_ISSUER
JWT_AUDIENCE
JWT_SECRET
MQTT_HOST
MQTT_USERNAME
MQTT_PASSWORD
CORS_ALLOWED_ORIGINS
```

Ví dụ chỉ thể hiện tên biến, không phải secret thật:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://database:3306/smart_door"
$env:SPRING_DATASOURCE_USERNAME = "<from-secret-store>"
$env:SPRING_DATASOURCE_PASSWORD = "<from-secret-store>"
$env:JWT_ISSUER = "<configured-issuer>"
$env:JWT_AUDIENCE = "<configured-audience>"
$env:JWT_SECRET = "<minimum-32-character-secret>"
$env:MQTT_HOST = "<mqtt-host>"
$env:MQTT_USERNAME = "<from-secret-store>"
$env:MQTT_PASSWORD = "<from-secret-store>"
$env:CORS_ALLOWED_ORIGINS = "https://app.example.com"
```

Không đưa các lệnh có secret thật vào tài liệu, shell history hoặc source code.

## 7. Danh sách biến môi trường

| Biến | Local default | Prod | Ý nghĩa |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | Bắt buộc là `prod` | Profile đang chạy. |
| `SPRING_DATASOURCE_URL` | MySQL localhost | Bắt buộc | JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | `smart_door` | Bắt buộc | Tài khoản DB tối thiểu quyền. |
| `SPRING_DATASOURCE_PASSWORD` | `change_me` | Bắt buộc | Mật khẩu DB. |
| `JWT_ISSUER` | `smart-door-local` | Bắt buộc | JWT issuer. |
| `JWT_AUDIENCE` | `smart-door-mobile` | Bắt buộc | JWT audience. |
| `JWT_SECRET` | Local placeholder | Bắt buộc, ≥32 ký tự | Khóa ký JWT dự kiến. |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Mặc định `15m` | Tuổi access token. |
| `MQTT_HOST` | `localhost` | Bắt buộc | Broker host. |
| `MQTT_PORT` | `1883` | Mặc định `8883` | Broker port. |
| `MQTT_USERNAME` | `smart_door` | Bắt buộc | Tài khoản MQTT của Backend. |
| `MQTT_PASSWORD` | `change_me` | Bắt buộc | Mật khẩu MQTT. |
| `MQTT_CONNECTION_TIMEOUT` | `10s` | Mặc định `10s` | Timeout kết nối broker. |
| `CORS_ALLOWED_ORIGINS` | Hai localhost origin | Bắt buộc | Allowlist phân tách bằng dấu phẩy. |
| `DB_MAX_POOL_SIZE` | Theo Hikari | `10` | Số connection tối đa. |
| `DB_MIN_IDLE` | Theo Hikari | `2` | Số connection idle tối thiểu. |
| `DB_CONNECTION_TIMEOUT` | Theo Hikari | `30s` | Thời gian chờ connection. |
| `DB_VALIDATION_TIMEOUT` | Theo Hikari | `5s` | Thời gian validate connection. |

## 8. Quy tắc bảo mật

1. Chỉ commit placeholder trong `.env.example`; `.env` phải không được Git theo
   dõi.
2. Không tạo fallback secret cho profile `prod`.
3. JWT, database và MQTT phải dùng credential độc lập.
4. Không log object `AppProperties` hoặc giá trị biến môi trường.
5. CORS production chỉ chứa origin tin cậy, không dùng wildcard với credential.
6. MQTT production dự kiến dùng TLS ở port `8883`; trust store và hostname
   verification phải được bổ sung khi triển khai MQTT adapter.
7. Việc có `JWT_SECRET` trong cấu hình chưa đồng nghĩa JWT đã được triển khai.

## 9. Kiểm thử

`BackendApplicationTests` và `AppPropertiesTests` dùng profile `test` để kiểm
tra context cùng binding cấu hình. Người dùng chịu trách nhiệm chạy Maven theo
quy ước hiện tại; agent không tự chạy nếu không được yêu cầu.

Lệnh xác minh đề xuất:

```powershell
cd backend
.\mvnw.cmd test
```

## 10. Tài liệu liên quan

- [Kiến trúc Backend](architecture.md)
- [Kế hoạch triển khai Backend](implementation-plan.md)
- [Cấu trúc phản hồi REST API](api-response.md)
- [Quy trình CI/CD](../08-process/ci-cd.md)
