---
title: Kiến trúc Backend
type: reference
version: 1.0
lang: vi
tags: [backend, architecture, spring-boot, security, observability]
updated: 2026-10-03
---

# Kiến trúc Backend

## 1. Mục tiêu và trạng thái

Tài liệu này định nghĩa kiến trúc mục tiêu cho Backend Smart Door System nhằm:

- dễ nâng cấp framework và thay thế hạ tầng;
- dễ bảo trì nhờ ranh giới module và phụ thuộc rõ ràng;
- dễ phát hiện, truy vết và gỡ lỗi nhưng không làm lộ dữ liệu nhạy cảm;
- áp dụng bảo mật theo mặc định cho REST API, MQTT, dữ liệu và secrets;
- kiểm thử được nghiệp vụ độc lập với MySQL, Mosquitto và HTTP.

Trạng thái source hiện tại chỉ là Spring Boot skeleton với Java 21, Spring Boot
4.1.1, JPA, Flyway, Security, Validation, Actuator, MySQL và H2. Các thành phần
trong tài liệu này là **kiến trúc mục tiêu**, không phải tất cả đã được triển
khai. REST API và MQTT contract vẫn đang là placeholder nên tài liệu không tự
định nghĩa endpoint, topic hoặc payload cụ thể.

## 2. Phạm vi và nguyên tắc

Backend chịu trách nhiệm:

- xác thực tài khoản App, phân quyền `ADMIN` và `USER`;
- quản lý người dùng, thẻ, PIN, cửa và hai ESP32;
- xử lý yêu cầu truy cập và phát lệnh điều khiển qua MQTT;
- quản lý heartbeat, cảnh báo, trạng thái khóa tạm và lịch sử truy cập;
- cung cấp REST API cho Mobile App;
- lưu dữ liệu nghiệp vụ trong MySQL.

Các nguyên tắc bắt buộc:

1. **Modular monolith trước**: một ứng dụng triển khai, chia module theo nghiệp
   vụ. Không tách microservice khi chưa có nhu cầu vận hành được chứng minh.
2. **Feature first**: code của một tính năng nằm gần nhau; không tạo các thư mục
   toàn cục khổng lồ như `controller`, `service`, `repository`.
3. **Dependency hướng vào trong**: HTTP, MQTT và database phụ thuộc vào use case
   nghiệp vụ; domain không phụ thuộc framework hoặc transport.
4. **Contract first**: endpoint và MQTT message chỉ được triển khai sau khi
   contract tương ứng được chốt trong `contracts/`.
5. **Secure by default**: từ chối khi chưa xác thực hoặc chưa đủ quyền, validate
   mọi input, không log secrets hay thông tin xác thực.
6. **Observable by design**: lỗi có mã ổn định, request có correlation ID và các
   tác vụ quan trọng có audit trail.
7. **Migration only**: schema production thay đổi qua Flyway; không dùng ORM tự
   động sửa schema.

## 3. Bối cảnh hệ thống

```text
Mobile App --HTTPS/JSON--> Backend --JDBC/TLS--> MySQL
                              |
                              +--MQTT/TLS--> Mosquitto <--MQTT/TLS--> ESP32
                              |
                              +--metrics/health/logs--> nền tảng vận hành
```

- HTTPS và MQTT TLS là yêu cầu cho môi trường triển khai ngoài máy phát triển.
- Mosquitto thực thi ACL theo danh tính client; Backend không thay thế quyền của
  broker.
- ESP32 Node 1 chỉ gửi dữ liệu xác thực. Chỉ Node 2 được nhận lệnh điều khiển
  khóa.
- Trạng thái lệnh khóa không được coi là trạng thái vật lý nếu chưa có phản hồi
  cảm biến.

## 4. Kiến trúc trong ứng dụng

### 4.1. Module nghiệp vụ

| Module | Trách nhiệm chính |
| --- | --- |
| `identity` | Đăng nhập, JWT, vai trò và trạng thái tài khoản. |
| `access` | RFID/PIN, quyết định cho phép, lockout và access log. |
| `door` | Cấu hình cửa, trạng thái lệnh và điều khiển từ App. |
| `device` | Onboarding, vòng đời và heartbeat của hai node. |
| `alert` | Tạo, xác nhận và giải quyết cảnh báo. |
| `safety` | Luồng báo cháy và quy tắc fail-safe đã được chốt. |
| `shared` | Kiểu giá trị, error model và tiện ích kỹ thuật dùng chung tối thiểu. |

Module chỉ gọi public application API của module khác. Không truy cập trực tiếp
repository hoặc entity nội bộ của module khác. Giao tiếp nội bộ dùng lời gọi
đồng bộ hoặc domain event trong cùng process; chỉ dùng event khi giúp tách phụ
thuộc hoặc cần xử lý sau transaction.

### 4.2. Cấu trúc package đề xuất

```text
com.smartdoor.backend
├── identity
│   ├── api             # REST DTO/controller và mapping transport
│   ├── application     # use case, command/query, transaction boundary
│   ├── domain          # entity/value object/policy thuần Java
│   └── infrastructure  # JPA, JWT, adapter ngoài
├── access
├── door
├── device
├── alert
├── safety
├── shared
│   ├── error
│   ├── security
│   └── observability
└── configuration
```

Quy tắc phụ thuộc:

```text
api ───────────────┐
infrastructure ────┼──> application ──> domain
configuration ─────┘
```

- Controller không chứa nghiệp vụ và không trả JPA entity.
- Application service xác định transaction boundary và điều phối use case.
- Domain chứa quy tắc nghiệp vụ, không import Spring/JPA nếu không cần thiết.
- Adapter JPA/MQTT/JWT triển khai interface do application hoặc domain sở hữu.
- DTO transport, entity persistence và domain model được tách khi vòng đời hoặc
  yêu cầu bảo mật khác nhau.

## 5. Quản lý cấu hình

Sử dụng cấu hình typed bằng `@ConfigurationProperties` và validate khi khởi
động. Không đọc biến môi trường rải rác trong code.

| Profile | Mục đích | Quy tắc |
| --- | --- | --- |
| `local` | Phát triển trên máy | Kết nối Docker MySQL/Mosquitto; log dễ đọc; không chứa secret trong Git. |
| `test` | Unit/integration test | Cấu hình độc lập, thời gian và adapter có thể thay thế; không gọi production. |
| `prod` | Container triển khai | Secret bắt buộc từ môi trường/secret store; fail fast nếu thiếu; debug tắt. |

Các nhóm cấu hình tối thiểu: datasource, Flyway, JWT, MQTT, CORS, actuator,
logging và chính sách nghiệp vụ. Giá trị nghiệp vụ có thể thay đổi như số lần
sai, thời gian khóa và ngưỡng nhiệt độ phải lấy từ database, không hard-code
trong profile.

## 6. API và xử lý lỗi

- REST dùng JSON và một base path/version được chốt trong OpenAPI.
- Response JSON thành công dùng `ApiResponse<T>`; response lỗi dùng Problem
  Details. Không trả HTTP `200` cho lỗi và không bọc endpoint hạ tầng hoặc nội
  dung không phải JSON.
- Validate DTO tại biên bằng Bean Validation; validate lại invariant trong
  domain.
- Lỗi API dùng `application/problem+json` theo Problem Details, có các trường
  ổn định: `type`, `title`, `status`, `detail`, `instance`, `errorCode`,
  `correlationId`; lỗi validation có danh sách field lỗi an toàn.
- `title` của mọi error code phải là tiếng Việt có dấu, ngắn gọn và không chứa
  dữ liệu động hoặc nhạy cảm; client phải dùng `errorCode` để xử lý bằng máy.
- Không trả stack trace, câu SQL, class name nội bộ hoặc secret cho client.
- Có global exception mapping; exception không xác định trả mã lỗi chung và
  được log một lần tại biên với correlation ID.
- Các thao tác lặp lại do reconnect/offline phải có idempotency key ổn định;
  `access_logs.event_id` là ràng buộc chống ghi trùng cho sự kiện truy cập.

## 7. Bảo mật

### 7.1. Xác thực và phân quyền

- Mật khẩu App và PIN được băm bằng BCrypt theo yêu cầu dự án; không mã hóa có
  thể giải ngược và không lưu giá trị thuần.
- JWT access token phải có thời hạn ngắn, kiểm tra chữ ký, issuer, audience,
  thời gian hết hạn và trạng thái người dùng. Thuật toán, cơ chế refresh/revoke
  và thời hạn token phải được ghi thành quyết định kỹ thuật trước triển khai.
- Phân quyền ở cả HTTP route và application use case; không chỉ ẩn chức năng ở
  Mobile App.
- Account `DISABLED`, credential bị thu hồi hoặc device bị vô hiệu hóa phải bị
  từ chối ở application layer.

### 7.2. Secrets và mã hóa

- Secret chỉ đến từ biến môi trường hoặc secret store; không commit secret thật.
- Không dùng chung secret JWT, tài khoản database và tài khoản MQTT.
- TLS bảo vệ dữ liệu khi truyền ở môi trường không phải local.
- Khóa mã hóa, nếu có, phải tách khỏi ciphertext, hỗ trợ định danh phiên bản khóa
  và rotation.
- UID RFID cần đối chiếu chính xác. Trước khi quyết định mã hóa tại database,
  phải chọn một trong các thiết kế đã threat-model: giá trị chuẩn hóa được bảo
  vệ bằng HMAC để tra cứu, hoặc ciphertext xác thực kèm blind index. Không tự
  thêm encryption converter khi chưa giải quyết lookup, uniqueness và rotation.
- Phạm vi mã hóa trường PII (`phone`, `name`) vẫn là quyết định mở. Database
  volume encryption và backup encryption thuộc tầng hạ tầng, không thay thế
  kiểm soát truy cập ở ứng dụng.

### 7.3. Input, logging và abuse prevention

- Giới hạn kích thước, độ dài, enum, định dạng và khoảng giá trị của mọi input.
- CORS dùng allowlist theo môi trường; CSRF được quyết định theo cách lưu token,
  không tắt mặc định mà không có lý do.
- Không log password, PIN, JWT, cookie, Authorization header, UID thẻ đầy đủ,
  khóa mã hóa hoặc payload chứa dữ liệu nhạy cảm.
- Login, đổi credential và lệnh mở cửa cần rate limit/replay protection phù hợp
  sau khi chốt kiến trúc triển khai.
- Dependency scanning, secret scanning và security test phải được thêm vào CI.

## 8. Quan sát và gỡ lỗi

### 8.1. Logging

- Log có timestamp UTC, level, service, environment, correlation ID và event
  name ổn định; production ưu tiên JSON để máy đọc.
- Nhận `X-Correlation-ID` hợp lệ hoặc sinh ID mới, trả lại trong response và
  truyền qua message metadata khi contract cho phép.
- Dùng parameterized logging; không nối chuỗi dữ liệu nhạy cảm.
- `DEBUG` chỉ bật tạm thời theo package. Không bật SQL bind parameters hoặc
  security trace trong production.
- Audit log nghiệp vụ khác application log: ai làm gì, trên đối tượng nào, khi
  nào và kết quả gì; không ghi secret hoặc credential thuần.

### 8.2. Health, metrics và tracing

- Actuator chỉ public `health/liveness` và `health/readiness` khi cần.
- Endpoint chứa environment, beans, config hoặc loggers phải tắt hoặc bảo vệ
  bằng mạng nội bộ và quyền quản trị.
- Readiness kiểm tra phụ thuộc bắt buộc; liveness không phụ thuộc MySQL/MQTT để
  tránh restart loop khi hạ tầng lỗi.
- Metrics tối thiểu: request latency/error, DB pool, JVM, MQTT connect/reconnect,
  message rejected, auth denied, access decision và command timeout.
- Distributed tracing là bước sau; correlation ID là nền tảng bắt buộc trước.

## 9. Dữ liệu và transaction

- Flyway là nguồn migration của Backend. Cần chuyển schema bootstrap hiện tại
  sang migration có version trước khi phát triển entity.
- Production đặt Hibernate schema management ở chế độ `validate`.
- Migration đã phát hành không được sửa; bổ sung migration mới và có kế hoạch
  rollback/forward-fix.
- Transaction chỉ bao quanh use case cần tính nguyên tử. Publish MQTT không thể
  tham gia transaction MySQL; lệnh cần bảo đảm giao hàng phải dùng outbox hoặc
  cơ chế retry/idempotency đã được thiết kế rõ.
- Thời gian lưu UTC trong Backend/database; chuyển múi giờ khi hiển thị.
- Vô hiệu hóa user và thu hồi credential liên quan phải thực hiện trong một
  transaction như tài liệu database quy định.

## 10. MQTT

- Topic, QoS, retained flag, payload, quyền publish/subscribe và version message
  phải được chốt tại `contracts/mqtt/topics.md` trước triển khai.
- Mỗi message có schema version, message/event ID, device ID và timestamp khi
  đồng hồ nguồn đáng tin cậy.
- Validate topic và payload trước khi chuyển vào application layer.
- Consumer phải idempotent; malformed/unauthorized message bị từ chối, đo metric
  và log metadata an toàn.
- Có reconnect với exponential backoff và jitter; không retry vô hạn theo vòng
  lặp nóng.
- Lệnh mở khóa cần correlation/command ID, thời hạn và phản hồi xác nhận riêng;
  nhận lệnh không đồng nghĩa cửa đã mở vật lý.

## 11. Chiến lược kiểm thử

| Tầng | Mục tiêu |
| --- | --- |
| Unit | Domain policy và use case, không khởi động Spring. |
| Slice | Controller/security mapping và JPA adapter theo phạm vi nhỏ. |
| Integration | MySQL/Mosquitto thật qua container khi cần kiểm chứng hành vi. |
| Contract | OpenAPI và MQTT schema tương thích producer/consumer. |
| End-to-end | Luồng quan trọng: login, RFID/PIN, mở cửa, heartbeat, alert. |
| Security | Quyền truy cập, input độc hại, secret/log leakage và dependency scan. |

H2 không được coi là bằng chứng đầy đủ cho câu SQL hoặc migration MySQL. Test
database quan trọng phải chạy với MySQL tương thích production.

## 12. Build, triển khai và khả năng nâng cấp

- Maven Wrapper và Java 21 là đường build chuẩn; `verify` là quality gate.
- Dependency version được quản lý tập trung qua Spring Boot BOM; hạn chế pin
  version riêng nếu không có lý do.
- Docker image chạy non-root như hiện tại; cần bổ sung healthcheck và JVM option
  qua môi trường, không bake secret vào image.
- Nâng Spring Boot theo từng thay đổi nhỏ, đọc migration guide và chạy đầy đủ
  unit/integration/contract test.
- CI phải kiểm tra format/static analysis, test, migration, dependency/secret
  scan và Docker build trước merge.

## 13. Quyết định còn mở

Các mục sau phải có quyết định kỹ thuật hoặc contract được review trước khi code:

1. OpenAPI: base path, endpoint, DTO, pagination và error code.
2. MQTT: topic, ACL, QoS, retained flag, payload và versioning.
3. JWT: thuật toán/khoá, issuer/audience, access/refresh lifecycle và revoke.
4. Mã hóa trường: threat model và phạm vi UID RFID/PII.
5. Delivery command: outbox, retry, timeout và acknowledgment cho mở khóa.
6. Runtime: nơi kết thúc TLS, metrics backend, log collector và secret store.
7. Test integration: Testcontainers hay dịch vụ CI tương đương.

## 14. Tài liệu liên quan

- [Kế hoạch triển khai Backend](implementation-plan.md)
- [Cấu trúc xử lý lỗi Backend](error-handling.md)
- [Cấu trúc phản hồi REST API](api-response.md)
- [Cấu hình môi trường Backend](environment-configuration.md)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- [Database ERD](../03-technical-reference/database-erd.md)
- [MQTT topics](../../contracts/mqtt/topics.md)
- [REST API contract](../../contracts/api/README.md)
- [Quy trình CI/CD](../08-process/ci-cd.md)
