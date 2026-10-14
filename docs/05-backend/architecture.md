---
title: Kiến trúc Backend
type: reference
version: 1.0
lang: vi
tags: [backend, architecture, spring-boot, security, observability]
updated: 2026-10-14
---

# Kiến trúc Backend

## 1. Mục tiêu và trạng thái

Tài liệu này định nghĩa kiến trúc mục tiêu cho Backend Smart Door System nhằm:

- dễ nâng cấp framework và thay thế hạ tầng;
- dễ bảo trì nhờ ranh giới module và phụ thuộc rõ ràng;
- dễ phát hiện, truy vết và gỡ lỗi nhưng không làm lộ dữ liệu nhạy cảm;
- áp dụng bảo mật theo mặc định cho REST API, MQTT, dữ liệu và secrets;
- kiểm thử được nghiệp vụ độc lập với MySQL, Mosquitto và HTTP.

Source hiện tại có nền tảng Spring Boot với Java 21, Spring Boot 4.1.1, JPA,
Flyway, Security, Validation, Actuator, MySQL/H2 và entity/repository 12 bảng.
Đã bổ sung service/controller đăng nhập và JWT, đã qua Maven verify ngày 14/10/2026;
các nghiệp vụ khác chưa triển khai. Các thành phần
trong tài liệu này là **kiến trúc mục tiêu**, không phải tất cả đã được triển
khai. REST API và MQTT contract vẫn đang là placeholder nên tài liệu không tự
định nghĩa endpoint, topic hoặc payload cụ thể.

## 2. Phạm vi và nguyên tắc

Backend chịu trách nhiệm:

- xác thực Account khách hàng và kiểm tra quyền sở hữu Door ở mọi use case;
- tách Account đăng nhập khỏi Cardholder được cấp RFID; không có Admin nền tảng;
- quản lý thẻ, PIN và cặp ESP32 theo từng Door; vân tay ngoài phạm vi;
- provision bộ Door/Node qua công cụ kỹ thuật, rồi cho khách hàng claim bằng mã kích hoạt;
- quản lý vòng đời thiết bị độc lập với kết nối online/offline và giữ lịch sử thay thế;
- xử lý yêu cầu truy cập và phát lệnh điều khiển qua MQTT;
- quản lý heartbeat, cảnh báo, trạng thái khóa tạm và lịch sử truy cập;
- cung cấp REST API cho Mobile App;
- lưu dữ liệu nghiệp vụ trong MySQL.

Các ràng buộc này tuân theo [quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md).
Account sở hữu nhiều Door; Door chưa claim có thể chưa có chủ, Door đã liên kết
có đúng một chủ. Dữ liệu và định tuyến thiết bị phải giữ ngữ cảnh cửa.

Các nguyên tắc bắt buộc:

1. **Modular monolith trước**: một ứng dụng triển khai, chia module theo nghiệp
   vụ. Không tách microservice khi chưa có nhu cầu vận hành được chứng minh.
2. **Feature first**: code của một tính năng nằm gần nhau; không tạo các thư mục
   toàn cục khổng lồ như `controller`, `service`, `repository`.
3. **Ranh giới module rõ ràng**: controller/handler gọi service; service sở hữu
   nghiệp vụ và transaction. Module khác chỉ dùng service public qua DTO/ID,
   không truy cập entity/repository nội bộ.
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

| Nhóm việc | Module | Trách nhiệm/bảng sở hữu |
| --- | --- | --- |
| Người 1 | `identity` | Account, đăng ký/đăng nhập, mật khẩu và JWT; bảng accounts. |
| Người 1 | `credentials` | Cardholder, cấp/thu hồi RFID, PIN; cardholders, rfid_credentials, system_pins. |
| Người 2 | `door` | Ownership, danh sách cửa, claim và mã kích hoạt; doors, door_activation_codes. |
| Người 2 | `device` | Provision Node, lifecycle, heartbeat, thay thế; devices, device_lifecycle_events. |
| Người 3 | `access` | Quyết định truy cập, lockout và lịch sử; access_logs, lockout_statuses. |
| Người 3 | `control` | Lệnh mở cửa, Node 2, ACK và timeout; không tạo bản sao entity Door/Device. |
| Người 4 | `settings` | Cấu hình ngưỡng, đồng bộ/ACK; door_configs. |
| Người 4 | `alert` | Cảnh báo, trạng thái xử lý và thông báo; alerts. |

Mỗi entity/repository có một module sở hữu. Module khác gọi service public với
DTO/ID, không truy cập repository hoặc sửa entity nội bộ. Ownership thuộc
door/service; lấy Account hiện tại thuộc shared/security. Phản ứng an toàn
tại chỗ thuộc firmware Node 2; sự kiện nhiệt độ được tiếp nhận qua alert,
không tạo module safety riêng trong khung này.

### 4.2. Cấu trúc package hiện hành

Nhóm thống nhất cấu trúc theo module với Controller–Service–Repository,
một JPA entity cho mỗi bảng. Chưa tách domain model và JPA entity thành hai
class. Khung package đã tạo bằng package-info.java; 12 entity và repository
đã thêm theo schema v2, xem [mapping JPA](entity-repository-mapping.md).
Đã bổ sung controller/service/DTO đăng nhập trong identity; các module nghiệp
vụ khác còn là khung. Xem [đăng nhập Backend](account-login.md).

```text
com.smartdoor.backend
├── identity/
│   ├── controller/
│   ├── service/
│   ├── entity/
│   ├── repository/
│   └── dto/
├── credentials/         # Cùng năm thư mục trên
├── door/
├── device/              # Thêm mqtt/ cho heartbeat
├── access/              # Thêm mqtt/ cho yêu cầu RFID/PIN
├── control/             # Thêm mqtt/ cho ACK/Exit
├── settings/            # Thêm mqtt/ cho ACK đồng bộ
├── alert/               # Thêm mqtt/ cho sự kiện cảnh báo
├── shared/              # api, error, security, observability đang có
├── infrastructure/
│   └── mqtt/            # Kết nối/publisher/dispatcher chung
└── configuration/       # Cấu hình Spring đang có
```

Luồng xử lý:

```text
REST controller hoặc MQTT handler → service → repository → database
                                      ↓
                                gọi service module khác qua DTO/ID
```

- Controller/handler validate dữ liệu đầu vào, không chứa logic nghiệp vụ.
- Service kiểm tra quyền, invariant và transaction; không tin Account từ body.
- Entity dùng JPA, chỉ thuộc một module; liên kết xuyên module ưu tiên ID.
- Repository chỉ được sử dụng trong module sở hữu.
- DTO request/response tách khỏi entity; không trả hash/secret ra API.
- Khung MQTT chưa kết nối Broker hoặc subscribe topic. Chốt contract trước
  triển khai; không tạo MQTT client riêng mỗi module.
- Test đặt dưới src/test/java/com/smartdoor/backend/<module>/ theo chức năng.
- Xem [hướng dẫn tổ chức module](module-structure.md) cho quy tắc dùng chung,
  giao tiếp và công việc song song.

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
- Validate DTO tại biên bằng Bean Validation; kiểm tra lại invariant trong
  service và entity của module.
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
- Account được lấy từ phiên xác thực; mọi truy vấn/cập nhật theo Door phải kiểm
  tra `owner_account_id`. Biết ID hoặc có token hợp lệ chưa đủ quyền truy cập.
- Cardholder và Door của một đăng ký RFID phải thuộc cùng Account. UID có phạm
  vi duy nhất theo cửa; thu hồi ở cửa A không thay đổi cửa B.
- Claim kiểm tra mã kích hoạt một lần, thời hạn và cửa chưa có chủ trong cùng
  transaction; chỉ một Account nhận sở hữu khi có request đồng thời.
- Provision được bảo vệ bằng cơ chế truy cập kỹ thuật nội bộ; không thêm quyền
  Admin toàn nền tảng vào tài khoản khách hàng.

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

- Mục tiêu: Flyway là nguồn migration của Backend. Hiện đã có 12 entity/repository
  theo schema bootstrap v2 nhưng chưa có migration trong Backend. Local/prod
  cần schema v2 khởi tạo từ hạ tầng để Hibernate validate; cần chuyển schema
  bootstrap sang migration có version và kiểm chứng quy trình baseline/nâng cấp
  trước khi dùng Flyway để quản lý thay đổi schema.
- Production đặt Hibernate schema management ở chế độ `validate`.
- Migration đã phát hành không được sửa; bổ sung migration mới và có kế hoạch
  rollback/forward-fix.
- Transaction chỉ bao quanh use case cần tính nguyên tử. Publish MQTT không thể
  tham gia transaction MySQL; lệnh cần bảo đảm giao hàng phải dùng outbox hoặc
  cơ chế retry/idempotency đã được thiết kế rõ.
- Thời gian lưu UTC trong Backend/database; chuyển múi giờ khi hiển thị.
- Vô hiệu hóa Cardholder và thu hồi RFID liên quan phải thực hiện trong một
  transaction; giữ lịch sử. Vô hiệu hóa Account là use case riêng; chính sách
  ảnh hưởng đến credential cục bộ/offline cần được đặc tả trước triển khai.
- Không xóa dây chuyền lịch sử khi ngừng sử dụng Door hoặc Device. Lifecycle
  transition ghi actor/quy trình, thời gian và lý do. Schema v2 và transition
  ở [Database ERD](../03-technical-reference/database-erd.md); Backend còn phải
  hiện thực use case, không dùng heartbeat ghi đè trạng thái vòng đời.
- Claim và thay thiết bị phải có kiểm soát đồng thời; một Door có tối đa một
  thiết bị hoạt động cho mỗi vai trò Node, thiết bị cũ giữ định danh lịch sử.

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
- Broker và Backend đối chiếu danh tính thiết bị với Door/Node đã provision,
  không tin riêng ID trong payload. Node 2 chỉ nhận lệnh cho đúng Door của mình.
- Online/offline/UNKNOWN được suy ra từ heartbeat nhận tại Backend và timeout;
  mỗi Node có trạng thái riêng. Thiết bị online nhưng bị vô hiệu hóa/ngừng sử
  dụng vẫn bị từ chối nghiệp vụ.

## 11. Chiến lược kiểm thử

| Tầng | Mục tiêu |
| --- | --- |
| Unit | Quy tắc entity và service nghiệp vụ, không khởi động Spring. |
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

1. OpenAPI tổng thể, endpoint ngoài đăng nhập, pagination và error code;
   đăng nhập đã chốt tại [contract v1](../../contracts/api/login.md).
2. MQTT: topic, ACL, QoS, retained flag, payload và versioning.
3. JWT mở rộng: refresh/revoke và rotation nhiều khóa. Bước đăng nhập đã chốt
   HS256, issuer/audience từ cấu hình, access token 15 phút, không refresh.
4. Mã hóa trường: threat model và phạm vi UID RFID/PII.
5. Delivery command: outbox, retry, timeout và acknowledgment cho mở khóa.
6. Runtime: nơi kết thúc TLS, metrics backend, log collector và secret store.
7. Test integration: Testcontainers hay dịch vụ CI tương đương.
8. Device: workflow lifecycle theo schema v2, policy mã kích hoạt, cấp/thu hồi credential
   và ACL, timeout heartbeat, đồng bộ cấu hình và lịch sử thay thế.

## 14. Tài liệu liên quan

- [Cấu trúc xử lý lỗi Backend](error-handling.md)
- [Cấu trúc phản hồi REST API](api-response.md)
- [Cấu hình môi trường Backend](environment-configuration.md)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- [Database ERD](../03-technical-reference/database-erd.md)
- [MQTT topics](../../contracts/mqtt/topics.md)
- [REST API contract](../../contracts/api/README.md)
- [Quy trình CI/CD](../08-process/ci-cd.md)
