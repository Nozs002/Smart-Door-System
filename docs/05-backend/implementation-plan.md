---
title: Kế hoạch triển khai Backend
type: reference
version: 1.0
lang: vi
tags: [backend, roadmap, spring-boot, security, testing]
updated: 2026-10-03
---

# Kế hoạch triển khai Backend

## 1. Mục tiêu

Kế hoạch này đưa backend từ Spring Boot skeleton đến hệ thống có thể vận hành,
kiểm thử và mở rộng. Thứ tự ưu tiên là nền tảng an toàn, contract, dữ liệu, sau
đó mới triển khai từng vertical slice nghiệp vụ.

Mỗi giai đoạn phải tạo ra một thay đổi nhỏ có thể review và kiểm chứng. Không
gom toàn bộ kiến trúc, database và nghiệp vụ vào một Pull Request.

## 2. Trạng thái đầu vào

Đã có:

- Java 21, Spring Boot 4.1.1 và Maven Wrapper;
- dependency Web MVC, Security, Validation, JPA, Flyway và Actuator;
- Dockerfile non-root, Docker Compose cho Backend/MySQL/Mosquitto;
- schema bootstrap và mock data trong `infrastructure/mysql/init/`;
- GitHub Actions chạy Maven `verify`, build và phát hành Docker image.

Chưa có:

- cấu hình profile đầy đủ và validation cấu hình;
- cấu trúc module, error model, logging/correlation ID;
- migration Flyway trong backend;
- REST OpenAPI và MQTT contract cụ thể;
- entity, repository, use case, endpoint hoặc MQTT adapter;
- JWT implementation, authorization policy và threat model;
- integration/contract/security test.

## 3. Quality gate chung

Một hạng mục chỉ hoàn thành khi:

- hành vi và contract liên quan đã được review;
- test phù hợp chạy qua `./mvnw verify`;
- không chứa secret, credential hoặc dữ liệu nhạy cảm trong log/test fixture;
- migration chạy được trên MySQL tương thích production;
- cấu hình mới có mô tả, giá trị mặc định an toàn và validation;
- tài liệu setup/verification được cập nhật;
- Docker image vẫn build và chạy non-root;
- `git diff --check` không báo lỗi.

### 3.1. Quy ước chạy Maven

- Từ ngày 2026-10-03, việc chạy Maven để xác minh Backend do người dùng trực
  tiếp thực hiện.
- Agent không tự chạy Maven trong các tác vụ tiếp theo, trừ khi người dùng yêu
  cầu rõ ràng.
- Agent vẫn phải cung cấp lệnh kiểm tra phù hợp và nêu rõ thay đổi chưa được
  Maven xác minh nếu chưa nhận kết quả từ người dùng.
- Kết quả do người dùng cung cấp được ghi nhận làm bằng chứng kiểm thử; quy ước
  này không loại bỏ quality gate build/test trước khi merge.

### 3.2. Bằng chứng kiểm thử

Ngày 2026-10-03, người dùng đã chạy Maven sau khi bổ sung nền tảng xử lý lỗi và
cung cấp kết quả:

```text
GlobalExceptionHandlerTests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Total: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Kết quả này xác minh global exception handling, validation error response và cơ
chế che giấu chi tiết lỗi ngoài dự kiến tại thời điểm kiểm tra.

## 4. Các giai đoạn triển khai

### Giai đoạn 0 — Chốt quyết định nền tảng

**Mục tiêu:** loại bỏ các điểm mơ hồ có thể gây làm lại hoặc tạo lỗ hổng.

**Công việc:**

- review [kiến trúc Backend](architecture.md);
- tạo quyết định kỹ thuật cho JWT, mã hóa trường dữ liệu, command delivery và
  integration test;
- chốt OpenAPI v1 và MQTT contract v1 ở mức đủ cho vertical slice đầu tiên;
- xác định môi trường local/staging/production, TLS termination và secret store.

**Điều kiện hoàn thành:** các quyết định mở cần cho Giai đoạn 1–3 có owner và
trạng thái rõ ràng; không nhất thiết chốt chi tiết của module chưa triển khai.

### Giai đoạn 1 — Baseline cấu hình và developer experience

**Mục tiêu:** ứng dụng khởi động nhất quán, fail fast và dễ phát triển.

**Công việc:**

- chuẩn hóa metadata Maven và kiểm tra dependency phù hợp Spring Boot hiện tại;
- tạo cấu hình chung cùng profile `local`, `test`, `prod`;
- tạo typed configuration cho JWT, MQTT, CORS và policy kỹ thuật;
- validate các thuộc tính bắt buộc khi khởi động;
- cấu hình datasource pool, timezone UTC, graceful shutdown và timeout;
- cập nhật `.env.example`, Docker Compose và README bằng placeholder an toàn;
- thêm kiểm thử context cho từng profile thiết yếu.

**Điều kiện hoàn thành:** local chạy bằng một quy trình được tài liệu hóa; test
không cần secret/database production; prod từ chối khởi động khi thiếu cấu hình
bắt buộc.

### Giai đoạn 2 — Error handling, observability và vận hành

**Mục tiêu:** lỗi có thể truy vết nhanh mà không lộ dữ liệu nhạy cảm.

**Công việc:**

- định nghĩa error code và Problem Details response;
- thêm global exception handler và validation error mapping;
- thêm correlation ID filter và response header;
- cấu hình log dễ đọc ở local, structured JSON ở prod;
- thiết lập quy tắc masking/redaction và kiểm thử log leakage;
- cấu hình Actuator liveness/readiness, metrics và exposure tối thiểu;
- thêm build info/version vào thông tin vận hành an toàn.

**Điều kiện hoàn thành:** một request lỗi có thể truy từ response đến log bằng
correlation ID; stack trace và secret không xuất hiện ở response/log mặc định;
health endpoint phản ánh đúng liveness/readiness.

### Giai đoạn 3 — Security foundation

**Mục tiêu:** thiết lập authentication/authorization và cryptographic boundary.

**Phụ thuộc:** quyết định JWT và threat model dữ liệu ở Giai đoạn 0.

**Công việc:**

- cấu hình SecurityFilterChain theo deny-by-default;
- triển khai BCrypt cho mật khẩu App và PIN với policy được kiểm thử;
- triển khai phát hành/xác minh JWT theo quyết định đã review;
- thực thi role và account status ở route lẫn use case;
- cấu hình CORS allowlist, request limits và security headers;
- thiết kế rate limit/replay protection cho login và lệnh mở cửa;
- thêm dependency/secret scan và security test vào CI;
- nếu được phê duyệt, triển khai HMAC/encryption cho trường dữ liệu cùng version
  khóa và kế hoạch rotation.

**Điều kiện hoàn thành:** test chứng minh request thiếu/sai token bị từ chối,
role không đủ không thể gọi use case, credential không bao giờ được lưu/log dạng
thuần và key rotation có đường nâng cấp nếu encryption được dùng.

### Giai đoạn 4 — Migration và persistence foundation

**Mục tiêu:** đưa schema về quyền quản lý của Flyway và tạo adapter dữ liệu an
toàn.

**Công việc:**

- tạo baseline migration từ schema hiện tại;
- giải quyết có chủ đích các chênh lệch trong tài liệu Database ERD;
- cấu hình Hibernate `validate`, UTC và transaction boundary;
- triển khai entity/repository theo từng module, không tạo toàn bộ trước nhu cầu;
- test migration và query quan trọng trên MySQL thật;
- xác định quy trình seed dữ liệu local/test tách khỏi production migration.

**Điều kiện hoàn thành:** database rỗng nâng được tới version mới nhất; database
đã baseline khởi động an toàn; schema drift làm build/test thất bại.

### Giai đoạn 5 — Vertical slice: Identity

**Mục tiêu:** hoàn thành login và authorization làm nền cho Mobile API.

**Công việc:**

- chốt và publish OpenAPI cho login/token lifecycle;
- triển khai domain/application/persistence/API cho identity;
- xử lý user `DISABLED` và audit sự kiện bảo mật;
- viết unit, controller/security, integration và contract test;
- cập nhật Mobile integration guide.

**Điều kiện hoàn thành:** luồng login hợp lệ/không hợp lệ/disabled và phân quyền
được kiểm thử end-to-end; không lộ lý do giúp dò tài khoản.

### Giai đoạn 6 — Vertical slice: Device lifecycle

**Mục tiêu:** onboarding hai node, heartbeat và trạng thái online/offline.

**Công việc:**

- chốt MQTT topic/payload/ACL và API quản trị thiết bị;
- triển khai MQTT adapter với validation, reconnect/backoff và metrics;
- lưu heartbeat, suy ra online/offline theo policy đã chốt;
- kiểm tra node type và trạng thái thiết bị trước khi nhận message;
- viết test idempotency, reconnect và malformed message.

**Điều kiện hoàn thành:** hai node có danh tính và quyền tối thiểu; heartbeat mất
hoặc lặp không làm hỏng trạng thái; sự cố broker quan sát được.

### Giai đoạn 7 — Vertical slice: RFID/PIN và điều khiển cửa

**Mục tiêu:** hoàn thành luồng truy cập cốt lõi từ Node 1 qua Backend tới Node 2.

**Công việc:**

- chốt contract auth request, access decision, unlock command và acknowledgment;
- triển khai policy RFID/PIN, lockout và cấu hình cửa từ database;
- ghi access log idempotent bằng `event_id`;
- triển khai outbox/retry/timeout theo quyết định command delivery;
- phân biệt command accepted, command acknowledged và trạng thái cửa vật lý;
- test concurrent failures, duplicate offline events và broker/database failure.

**Điều kiện hoàn thành:** RFID/PIN hợp lệ và bị từ chối đúng policy; không có
đường từ Node 1 điều khiển trực tiếp khóa; duplicate không tạo access log/lệnh
không kiểm soát.

### Giai đoạn 8 — Alerts, safety và remote control

**Mục tiêu:** hoàn thiện cảnh báo, báo cháy và mở cửa từ App.

**Công việc:**

- triển khai alert lifecycle và audit;
- triển khai cảnh báo sai nhiều lần/cửa mở lâu;
- chốt ownership và fail-safe cho high temperature trước khi code;
- triển khai remote unlock với authorization, rate limit và audit mạnh;
- kiểm thử mất mạng, message trễ/lặp và phục hồi sau lỗi.

**Điều kiện hoàn thành:** các tình huống trong tài liệu test scenario có kết quả
xác định; hành vi safety khi Backend/broker lỗi được tài liệu hóa và kiểm thử.

### Giai đoạn 9 — Hardening và release readiness

**Mục tiêu:** chuẩn bị chạy staging/production và nâng cấp an toàn.

**Công việc:**

- test tải cho login, heartbeat, access event và lịch sử;
- rà soát threat model, dependency, container và cấu hình TLS;
- kiểm tra backup/restore, migration và key/secret rotation;
- tạo dashboard/alert vận hành và runbook xử lý sự cố;
- kiểm tra SBOM/image scan, resource limits và graceful shutdown;
- diễn tập rollback/forward-fix và release theo SemVer.

**Điều kiện hoàn thành:** CI/CD quality gate xanh; có bằng chứng restore và
rotation; lỗi chính có metric/log/runbook; release candidate chạy ổn định trên
staging.

## 5. Thứ tự Pull Request đề xuất

1. `chore: establish backend configuration profiles`
2. `add: standardize backend errors and correlation logging`
3. `chore: secure actuator and add operational metrics`
4. `add: establish backend security foundation`
5. `add: baseline database migrations`
6. `add: implement identity vertical slice`
7. `add: implement device lifecycle vertical slice`
8. `add: implement local access vertical slice`
9. `add: implement alerts and safety flows`
10. `chore: harden backend release pipeline`

Tên và phạm vi PR có thể điều chỉnh sau review, nhưng mỗi PR phải độc lập, có
test và không trộn refactor không liên quan.

## 6. Theo dõi tiến độ

| Giai đoạn | Trạng thái ban đầu | Phụ thuộc chính |
| --- | --- | --- |
| 0. Quyết định nền tảng | Đang soạn | Review của nhóm |
| 1. Cấu hình | Chưa bắt đầu | Một phần Giai đoạn 0 |
| 2. Error/observability | Chưa bắt đầu | Giai đoạn 1 |
| 3. Security | Chưa bắt đầu | JWT và threat model |
| 4. Persistence | Chưa bắt đầu | Quyết định schema |
| 5. Identity | Chưa bắt đầu | Giai đoạn 3–4, OpenAPI |
| 6. Device lifecycle | Chưa bắt đầu | MQTT contract |
| 7. Access/door control | Chưa bắt đầu | Giai đoạn 3–6 |
| 8. Alerts/safety | Chưa bắt đầu | Giai đoạn 6–7 |
| 9. Hardening | Chưa bắt đầu | Các slice MVP |

Trạng thái chỉ được cập nhật cùng bằng chứng (PR, test hoặc tài liệu quyết định),
không đánh dấu hoàn thành dựa trên kế hoạch.

## 7. Rủi ro chính

| Rủi ro | Cách giảm thiểu |
| --- | --- |
| API/MQTT chưa chốt gây làm lại | Contract first, versioning và contract test. |
| H2 che giấu khác biệt MySQL | Integration test bằng MySQL thật. |
| DB commit nhưng MQTT publish thất bại | Outbox/idempotency/retry được quyết định trước slice access. |
| Log làm lộ PIN/token/UID | Redaction, allowlist field và test log leakage. |
| Mã hóa làm mất khả năng lookup/rotation | Threat model, HMAC/blind index và version khóa. |
| Actuator/debug bị public | Exposure tối thiểu, network policy và authorization. |
| Scope mở rộng quá sớm | Modular monolith và PR theo vertical slice. |
| Safety phụ thuộc Backend khi mất mạng | Chốt rõ fail-safe ở firmware và test offline. |

## 8. Tài liệu liên quan

- [Kiến trúc Backend](architecture.md)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- [Database ERD](../03-technical-reference/database-erd.md)
- [Kịch bản kiểm thử](../03-technical-reference/test-scenarios.md)
- [Quy trình CI/CD](../08-process/ci-cd.md)
