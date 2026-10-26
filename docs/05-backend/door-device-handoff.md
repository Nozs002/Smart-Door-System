---
title: Bàn giao quản lý cửa và vòng đời thiết bị - Người 2
type: reference
version: 1.0
lang: vi
tags: [backend, handoff, door, device, testing]
updated: 2026-10-16
---

# Bàn giao quản lý cửa và vòng đời thiết bị — Người 2

## 1. Đọc trước khi chạy

Đợt này hiện thực nghiệp vụ backend của Người 2 và các màn hình Flutter gọi
API tương ứng. Không làm đăng nhập của Người 1 hoặc kết nối MQTT thật. Có
interface để ghép các phần đó; mặc định không giả người dùng, ACK hoặc quyền
Broker nhằm làm cho luồng trông như đã tích hợp.

Để thử giao diện trước khi có đăng nhập, có entrypoint Flutter debug riêng
`lib/main_preview.dart`, dùng dữ liệu trong bộ nhớ và banner ghi rõ mô phỏng.
Preview không gọi API, không sửa database, không cấp token và không thay đổi
xác thực/readiness của Backend thật. Cách chạy và kịch bản thử nằm ở mục 8.

Phân biệt ba mức:

| Mức | Ý nghĩa trong lần bàn giao này |
| --- | --- |
| Có source | Service, API, CLI, màn hình và test đã được viết trong repository |
| Đã xác minh tĩnh | Đã đối chiếu source/contract; kiểm tra định dạng và liên kết tài liệu |
| Đã kiểm tra biên dịch Java | Toàn bộ source chính và test biên dịch bằng JDK 21 (`javac --release 21`) với dependency cache hiện có, exit code 0 |
| Đã chạy test Flutter | `flutter test --no-pub --reporter expanded`: 31/31 đạt (12 widget, 4 model, 4 giao tiếp HTTP cục bộ, 11 repository preview) |
| Đã phân tích/định dạng Flutter | `flutter analyze --no-pub`: No issues found; `dart format --output=none --set-exit-if-changed lib test`: đạt |
| Chưa nghiệm thu chạy thật | Chưa chạy Maven và workflow MySQL/MQTT hoàn chỉnh trong phiên này; kết quả Flutter được ghi riêng bên dưới |

Không coi danh sách test bên dưới là báo cáo test đã pass. Muốn xác nhận chạy
được, cần chạy các lệnh ở mục 6–8 và ghi nhận kết quả. `Started` hoặc health UP
chưa chứng minh claim đồng thời, phân quyền và thiết bị vật lý đã hoạt động.

## 2. Các phần đã thêm và thay đổi

| Nhóm | File/thư mục chính | Hành vi |
| --- | --- | --- |
| Door REST | [DoorController](../../backend/src/main/java/com/smartdoor/backend/door/controller/DoorController.java), [DTO](../../backend/src/main/java/com/smartdoor/backend/door/dto) | List/detail/update/claim; không nhận owner ID từ body |
| Door nghiệp vụ | [DoorService](../../backend/src/main/java/com/smartdoor/backend/door/service/DoorService.java) | Claim có khóa Door/mã, retry cùng Account, sửa thông tin và truy vấn theo chủ |
| Ownership | [DoorOwnershipService](../../backend/src/main/java/com/smartdoor/backend/door/service/DoorOwnershipService.java) | Dùng chung cho Người 1/3/4; 404 khi không sở hữu; có phiên bản khóa cho thao tác ghi |
| Mã kích hoạt | [ActivationCodes](../../backend/src/main/java/com/smartdoor/backend/door/service/ActivationCodes.java), [ClaimRateLimiter](../../backend/src/main/java/com/smartdoor/backend/door/service/ClaimRateLimiter.java) | Sinh ngẫu nhiên 8 ký tự, hash SHA-256, 7 ngày, giới hạn Account/IP |
| Công cụ kỹ thuật | [ProvisionCli](../../backend/src/main/java/com/smartdoor/backend/door/cli/ProvisionCli.java), [DoorProvisioningService](../../backend/src/main/java/com/smartdoor/backend/door/service/DoorProvisioningService.java) | Provision/cấp lại mã/đăng ký Node thay thế; không mở cổng HTTP |
| Device REST | [DeviceController](../../backend/src/main/java/com/smartdoor/backend/device/controller/DeviceController.java), [DTO](../../backend/src/main/java/com/smartdoor/backend/device/dto) | Node theo cửa, transition, replacement và lịch sử phân trang |
| Lifecycle | [DeviceLifecycleService](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceLifecycleService.java) | Khóa Door trước Device; điều kiện transition, active slot, event nguyên tử |
| Readiness | [DeviceReadinessPolicy](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceReadinessPolicy.java) | Đầu nối danh tính/ACK và điều kiện thay Node; mặc định từ chối |
| Heartbeat | [DeviceHeartbeatService](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceHeartbeatService.java), [DeviceConnectionPolicy](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceConnectionPolicy.java) | Nguồn đã xác thực, giờ Backend nhận, UNKNOWN/ONLINE/OFFLINE riêng từng Node |
| Quyền xử lý thiết bị | [DeviceOperationalService](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceOperationalService.java) | Module khác chỉ nhận mô tả Device sau kiểm tra đúng danh tính và ACTIVE; ONLINE không cấp quyền |
| Danh tính Account | [AccountPrincipal](../../backend/src/main/java/com/smartdoor/backend/shared/security/AccountPrincipal.java), [CurrentAccountProvider](../../backend/src/main/java/com/smartdoor/backend/shared/security/CurrentAccountProvider.java) | Chỉ dùng principal đúng kiểu và Account ACTIVE trong DB; không phát hành/xác minh JWT |
| Response/lỗi | [PageResponse](../../backend/src/main/java/com/smartdoor/backend/shared/api/PageResponse.java), [ErrorCode](../../backend/src/main/java/com/smartdoor/backend/shared/error/ErrorCode.java) | Phân trang ổn định; thêm 429 RATE_LIMITED; dùng envelope/Problem Details có sẵn |
| Repository | [door](../../backend/src/main/java/com/smartdoor/backend/door/repository), [device](../../backend/src/main/java/com/smartdoor/backend/device/repository) | Query theo phạm vi, phân trang, lookup hash và khóa pessimistic |
| Database | [Flyway migrations](../../backend/src/main/resources/db/migration) | V1 bootstrap schema, V2 unique lookup hash; không xóa volume |
| Mobile | [lib](../../mobile/lib) | REST client/repository/model, danh sách/claim/chi tiết cửa/Node/lịch sử, form và lỗi |
| Preview mobile | [main_preview.dart](../../mobile/lib/main_preview.dart), [preview](../../mobile/lib/preview) | Entrypoint debug riêng, repository trong bộ nhớ và điều khiển điều kiện giả để thử UI trước khi ghép đăng nhập |
| Tài liệu | [Module 02](../02-modules/02-device-management/README.md), [Module 03](../02-modules/03-device-lifecycle/README.md), [OpenAPI](../../contracts/api/door-device.openapi.yaml) | Quy tắc nghiệp vụ, API và ranh giới triển khai |

Đây là mô tả nhóm thay đổi trong đợt bàn giao, không phải tuyên bố mọi file
trong các thư mục trên đều mới. Những chức năng JWT, Cardholder/RFID/PIN,
điều khiển và cảnh báo vẫn thuộc người được phân công tương ứng.

## 3. Các quy tắc đã chốt và được dùng trong code

- Provision tạo Door UNCLAIMED không chủ, hai Node PROVISIONED đúng vai trò,
  cấu hình mặc định, hai lockout RFID/PIN và event provision trong transaction.
  Không tạo PIN mặc định hay credential MQTT giả.
- Mã 8 ký tự chữ/số ngẫu nhiên, bỏ `0/O/1/I/L`, hiển thị `XXXX-XXXX`.
  Backend chuẩn hóa chữ hoa, bỏ phân cách khi tra cứu; chỉ lưu SHA-256.
  Mã hiệu lực 7 ngày, dùng một lần. CLI in mã gốc một lần sau khi cấp thành công.
- Cấp lại mã chỉ cho cửa chưa có chủ; thu hồi mã chưa dùng còn hiệu lực trước đó.
  Không dùng cấp lại để chuyển chủ.
- Claim chuyển Door sang SETUP_PENDING; Node vẫn PROVISIONED. Retry cùng
  Account/mã đã dùng thành công trả Door cũ; Account khác bị từ chối.
- Mỗi Account sở hữu nhiều cửa; mỗi cửa có một chủ sau claim. Không chia sẻ/chuyển chủ.
- PATCH cập nhật cả tên và vị trí: tên bắt buộc; bỏ/null/để trống vị trí sẽ xóa vị trí.
- Mỗi Door có tối đa một ACTIVE cho mỗi vai trò; giữ Node cũ và lịch sử khi thay.
- RETIRE cần lý do và là kết thúc; replacement cần lý do và Node mới cùng cửa/vai trò.
  API ACTIVATE/DISABLE/REACTIVATE vẫn cho phép lý do tùy chọn, tối đa 500 ký tự.
  App không hiện ô lý do khi ACTIVATE/REACTIVATE, chỉ yêu cầu xác nhận.
- Chưa heartbeat là UNKNOWN; chưa quá 90 giây là ONLINE; quá 90 giây là OFFLINE.
  Đúng mốc 90 giây vẫn ONLINE. Heartbeat không sửa lifecycle hoặc version ACK.
- Timeout backend điều chỉnh bằng `app.device.heartbeat-timeout-seconds` (mặc định
  90). Chu kỳ gửi 30 giây là mốc thống nhất thử nghiệm, chưa có firmware gửi thật.
- Giới hạn claim 5 lần/phút trên Account và IP transport; bộ nhớ có giới hạn,
  một instance. Restart làm mất cửa sổ hiện tại; nhiều instance cần bộ lưu chung.

Các DTO thời gian nghiệp vụ là `LocalDateTime` UTC, JSON có thể không kèm `Z`.
Client phải hiểu là UTC rồi chuyển múi giờ. Envelope `timestamp` là `Instant` có `Z`.

## 4. Cách ghép với Người 1 và Người 4

### Người 1 — Account đang đăng nhập

Filter xác minh JWT của Người 1 cần đặt principal vào SecurityContext theo
[AccountPrincipal](../../backend/src/main/java/com/smartdoor/backend/shared/security/AccountPrincipal.java),
có `Long accountId()`. `CurrentAccountProvider.requireAccountId()` kiểm tra:
đã xác thực, đúng kiểu principal, ID dương và Account còn ACTIVE trong database.
Không dùng username, ID trong body/header tự khai hoặc Account cố định làm quyền.

Controller của Người 2 đã lấy Account qua provider. Chưa ghép filter JWT thì
API trả 401, kể cả client tự đặt chuỗi Bearer bất kỳ. Seed Account không tạo
token hoặc thông tin đăng nhập hoạt động.

Các module khác dùng:

```java
doorOwnershipService.requireOwner(accountId, doorId);
```

Nếu cần sửa dữ liệu theo Door trong cùng transaction, xem hàm khóa
`requireOwnerForUpdate`. Mọi writer của bộ Door/Device tuân thứ tự khóa Door
trước rồi mới mã/Device để giảm đảo thứ tự khóa.

Phía Flutter, Người 1 cung cấp [AccessTokenProvider](../../mobile/lib/core/access_token_provider.dart)
và truyền vào `SmartDoorApp(tokenProvider: ..., onSignInRequested: ...)`.
Provider trả token còn hợp lệ; refresh/lưu trữ token do module Account quản lý.
App bình thường qua `lib/main.dart` mặc định SignedOut, không có màn hình đăng
nhập giả hoặc token hardcode. Preview UI riêng không gọi API hay cấp danh tính
cho Backend.

### Người 4 và adapter thiết bị — readiness

Triển khai bean `DeviceReadinessPolicy` với bằng chứng đúng Node/phiên bản:

- `canActivate(device)`: danh tính đã xác minh, cấu hình hiện hành và PIN cần
  thiết cho vai trò đã được xác nhận áp dụng. Không suy ra từ việc lưu cấu hình
  vào DB hoặc chỉ nhận heartbeat.
- `canReplace(previous, replacement)`: ngoài Node mới sẵn sàng, có bằng chứng
  thu hồi quyền Node cũ theo workflow đã thống nhất. Mặc định trả false.

`DeviceIntegrationConfiguration` cấp policy từ chối khi chưa có bean tích hợp.
Vì thế API activate/reactivate/replace mặc định trả 409. Test dùng policy giả;
không thêm `ready=true` vào request công khai hoặc mặc định trả true để demo.

Disable/retire hiện đổi quyền trong backend và ghi lịch sử. Chúng chưa khẳng
định Broker đã thu hồi credential, cache RFID/PIN trên ESP32 đã xóa hoặc một
ESP32 mất mạng đã ngừng vận hành. Không nghiệm thu các tác động đó trước MQTT thật.

### Người phụ trách MQTT/firmware

Sau khi xác thực transport và đối chiếu ACL với Device/Door, gọi
`DeviceHeartbeatService.recordHeartbeat(AuthenticatedDeviceIdentity, firmwareVersion)`.
Không tạo identity này chỉ từ `doorId/deviceCode` trong payload chưa xác minh.
Không có REST heartbeat công khai; applied version cần đầu nối ACK riêng.

Module xử lý xác thực/cấu hình sử dụng `DeviceOperationalService.requireActive(identity)`
để từ chối Node không ACTIVE dù còn ONLINE. Kết quả là kiểm tra tại thời điểm
gọi; thao tác ghi/gửi lệnh tiếp theo vẫn phải kiểm tra lifecycle trong workflow
và phối hợp thu hồi quyền bên ngoài, không coi một lần đọc là khóa Broker.

## 5. Database và migration

[V1](../../backend/src/main/resources/db/migration/V1__bootstrap_schema.sql)
là schema bootstrap v2 cho database rỗng, tương ứng schema hiện hành trong
`infrastructure/mysql/init`. [V2](../../backend/src/main/resources/db/migration/V2__activation_code_hash_lookup.sql)
thêm unique hash để tra cứu mã không mơ hồ. Không tạo mã cho seed và không đổi
chủ Door đã có.

- Database rỗng do backend quản lý: Flyway chạy V1 rồi V2.
- Database đã bootstrap v2 nhưng chưa có lịch sử Flyway: cấu hình baseline
  hiện tại ghi mốc 1, sau đó chạy V2. Baseline không sửa schema cũ.
- Database thuộc mô hình cũ (ví dụ bảng `users`, thiếu Door/ownership): chưa có
  chuyển đổi tự động; cần mapping và migration riêng theo [database setup](../03-technical-reference/database-setup.md).
- Nếu V2 báo trùng hash, kiểm tra dữ liệu trên bản sao và xử lý nguyên nhân;
  không tự xóa bảng/mã hoặc bỏ unique constraint để chạy tiếp.

Sửa SQL init không cập nhật volume cũ. Không cần reset volume để nhận backend
mới. Các file bootstrap/seed hiện tại được giữ, không gán cửa cũ về Account ID 1.

## 6. Chạy backend mới và CLI

Mở Docker Desktop, chờ engine sẵn sàng. Chạy ở thư mục gốc repository:

```powershell
docker compose up -d --build backend
docker compose ps
docker compose logs --tail=100 backend
curl.exe http://localhost:8080/actuator/health
```

Backend dùng MySQL trong network Compose. Không cần mở cổng MySQL ra host để
chạy CLI theo cách này. Nếu báo port 8080 đã dùng, tìm đúng ứng dụng/container
đang giữ cổng trước khi khởi động backend mới. Nếu `.env` thiếu JWT_ISSUER,
JWT_AUDIENCE hoặc CORS_ALLOWED_ORIGINS, bổ sung theo `.env.example` và cấu hình
môi trường nhóm, rồi recreate backend; không dùng giá trị rỗng để vượt validation.
Chỉ bổ sung các key còn thiếu, giữ secret hiện có; không sao chép đè `.env`
bằng `.env.example`.

Provision một bộ mới; đổi mã demo nếu đã từng chạy lệnh:

```powershell
docker compose run --rm --no-deps -e "SPRING_PROFILES_ACTIVE=local,provision" backend --tool.action=provision "--tool.name=Cua demo" --tool.node1=DEMO-N1-20261016 --tool.node2=DEMO-N2-20261016 "--tool.location=Tang 1"
```

Profile `provision` tắt HTTP, không giữ cổng 8080, chạy một tác vụ rồi đóng
context. MySQL phải đang sẵn sàng vì `--no-deps` không khởi động database thay bạn.
Kết quả gồm Door ID, mã chỉ hiển thị một lần và hạn UTC. Lưu mã vào kênh bàn giao
phù hợp; không commit hoặc chụp log có mã vào báo cáo công khai.

Cấp lại mã cho Door chưa claim; số `101` dưới đây là ví dụ, phải thay bằng Door ID thực:

```powershell
docker compose run --rm --no-deps -e "SPRING_PROFILES_ACTIVE=local,provision" backend --tool.action=reissue --tool.door-id=101
```

Đăng ký Node thay thế (Door ID/Device ID là ví dụ):

```powershell
docker compose run --rm --no-deps -e "SPRING_PROFILES_ACTIVE=local,provision" backend --tool.action=replacement --tool.door-id=101 --tool.old-device-id=201 --tool.device-code=DEMO-N1-REPLACEMENT "--tool.reason=Thay Node hong"
```

Lệnh in ID/code Device mới, chỉ đăng ký PROVISIONED cùng vai trò; không tự
retire Node cũ, không cấp credential Broker và không tự activate.

Sau khi Người 1 ghép xác thực, App hoặc REST client có token hợp lệ dùng các
body sau với endpoint trong [OpenAPI](../../contracts/api/door-device.openapi.yaml):

```json
{"activationCode":"K7MP-4Q2D"}
```

Mã trên chỉ minh họa định dạng, không phải mã đã phát hành. Body sửa cửa:

```json
{"name":"Cửa chính","location":"Tầng 1"}
```

## 7. Kiểm thử backend

Theo quy ước repository, người dùng chạy Maven ở thư mục `backend`:

```powershell
.\mvnw.cmd verify
```

Profile test dùng H2 và không yêu cầu Docker/MySQL/MQTT. Cần JDK 21 và Maven
dependency tải được. Không thay parent/dependency chỉ để che lỗi mạng/cache.
Các test controller/MockMvc có thể gắn principal giả đúng kiểu trong phạm vi
test để kiểm tra API và phân quyền trước khi có JWT thật. Đây không phải cơ
chế đăng nhập cho App/Postman; xác minh JWT thật vẫn phải kiểm thử sau khi ghép
Người 1. Lệnh Maven ở trên do người dùng chạy theo quy ước dự án.

| Test mới | Phạm vi thiết kế |
| --- | --- |
| `ActivationCodeAndLimiterTests` | Định dạng/chuẩn hóa/hash mã, cửa sổ rate limit |
| `DoorWorkflowTests` | Provision, claim/retry/phân quyền, hết hạn/cấp lại, sửa cửa, Account DISABLED |
| `DoorConcurrencyTests` | Hai request claim tranh cùng mã, retry không đổi chủ; vẫn cần xác minh khóa hàng trên MySQL thật |
| `DoorControllerTests`, `CurrentAccountProviderTests` | Binding/validation HTTP, lấy Account qua principal đúng kiểu, từ chối giả mạo và Account DISABLED |
| `DoorSecurityIntegrationTests` | Chạy qua SecurityFilterChain thật: không phiên/Bearer tự khai bị từ chối; Account khác không xem được cửa |
| `DeviceConnectionPolicyTests` | UNKNOWN/ONLINE/OFFLINE và ranh giới timeout bằng Clock cố định |
| `DeviceLifecycleServiceTests` | Transition, readiness từ chối, active slot, replacement/thu hồi quyền và event |
| `DeviceLifecyclePersistenceTests` | Lưu lifecycle hai Node, đối chiếu trạng thái Door và giữ lịch sử thay Node trên H2 |
| `WorkflowRollbackTests` | Gây lỗi lưu event để kiểm tra rollback provision và lifecycle qua transaction service thật |
| `DeviceHeartbeatServiceTests`, `DeviceOperationalServiceTests` | Danh tính đúng cửa/vai trò, heartbeat không ghi ACK, từ chối Node không ACTIVE ở nghiệp vụ |
| `DeviceQueryServiceTests`, `DeviceControllerTests` | Ownership, Node đúng Door, DTO và lịch sử có phân trang/lọc |

Tổng cộng đã thêm 63 phương thức kiểm thử Java cho phần Người 2. Toàn bộ source
Java và test đã biên dịch bằng JDK 21 với dependency cache trong máy. Chưa chạy
Maven hoặc thực thi các test Java trong phiên này; biên dịch thành công và số
lượng test không phải bằng chứng kiểm thử hành vi đã đạt.

Còn phải kiểm chứng trên MySQL thật trước nghiệm thu:

1. Hai transaction/Account claim cùng mã đồng thời: đúng một chủ, cùng Account
   retry trả cùng Door, không event/Node/Door trùng.
2. Provision lỗi giữa transaction không để lại bộ cửa thiếu Node hoặc mã.
3. Không có hai ACTIVE cùng vai trò; replacement retire slot cũ trước khi
   activate slot mới để đáp ứng generated-column unique constraint của MySQL.
4. Chèn lỗi lưu event phải rollback thay đổi Device.
5. Xác thực Account A không xem/sửa Door hoặc Device của Account B.
6. Áp dụng migration trên database rỗng và bản sao database bootstrap v2 có dữ liệu.

H2 và mock unit test không thay thế kiểm chứng khóa hàng, MySQL enum/generated
column hoặc giao dịch nhiều kết nối. [verify.sql](../../infrastructure/mysql/verify.sql)
là kiểm tra ràng buộc schema/seed riêng; không phải test hoàn chỉnh API mới.

## 8. Màn hình Flutter và cách thử

Đã chạy `flutter analyze --no-pub`: không có vấn đề; kiểm tra định dạng Dart đạt.
`flutter test --no-pub --reporter expanded`: 31/31 test đạt, gồm form/điều hướng/trạng thái
màn hình, mô hình dữ liệu, repository preview và HttpServer cục bộ kiểm tra request/response. Các
test dùng repository/token giả trong test, không thay thế việc ghép JWT và
backend thật hoặc kiểm thử trên điện thoại/ESP32.

Đã có các màn hình danh sách cửa, claim bằng mã, chi tiết/sửa tên-vị trí,
chi tiết Device và lifecycle, lịch sử có phân trang/lọc Device. Node hiển thị
lifecycle và kết nối riêng. Form ACTIVATE/REACTIVATE chỉ có xác nhận, không có
ô lý do; thao tác vẫn được ghi vào lịch sử. DISABLE có lý do tùy chọn, còn
RETIRE và replacement bắt buộc lý do. API vẫn chấp nhận lý do tùy chọn cho
ACTIVATE/REACTIVATE để giữ tương thích với các client khác.
Entrypoint bình thường không có màn hình đăng nhập hay token demo. Có thêm
entrypoint preview riêng bên dưới để kiểm tra UI bằng dữ liệu giả.

### 8.1. Thử bằng tay trên Android trước khi có đăng nhập

Trong thư mục `mobile`, dừng phiên Flutter hiện tại bằng `q` và chạy:

```powershell
flutter devices
flutter run --debug -t lib/main_preview.dart -d DEVICE_ID
```

Thay `DEVICE_ID` bằng ID điện thoại Android trong kết quả `flutter devices`.
Chạy `flutter pub get` trước nếu máy chưa có dependency. Preview không cần
Docker, MySQL, JWT, `API_BASE_URL` hay kết nối Backend. `main_preview.dart` chỉ
cho chạy ở debug và từ chối profile/release bằng kiểm tra runtime.

`PreviewApp` dùng `PreviewDoorRepository` trong `mobile/lib/preview`, giữ dữ
liệu trong bộ nhớ. Banner có nút `Đặt lại` và hai công tắc ban đầu tắt:

- `Sẵn sàng`: mô phỏng đã xác minh danh tính và áp dụng cấu hình/PIN.
- `Thu hồi`: mô phỏng đã thu hồi quyền Broker của Node cũ khi thay thiết bị.

Đây là điều kiện giả phục vụ UI, không phải MQTT/ACK thật, không sửa policy
Backend. `Đặt lại` hoặc khởi động lại App/hot restart đưa dữ liệu về ban đầu
và tắt hai công tắc; hot reload thông thường giữ dữ liệu trong bộ nhớ.

| Bước thử | Kết quả mong đợi trong preview |
| --- | --- |
| Mở danh sách | Có hai cửa đã claim; cửa ID `1` `ACTIVE`, cửa ID `2` `SETUP_PENDING` |
| Xem thiết bị cửa `1` | Node `11` `ACTIVE/ONLINE`, Node `12` `ACTIVE/OFFLINE`, Node `13` `PROVISIONED/UNKNOWN` chờ thay Node 1 |
| Thêm cửa với `K7MP-4Q2D` | Cửa thứ ba xuất hiện; gửi lại mã trả cùng cửa; mã khác bị báo không hợp lệ (`400` mô phỏng) |
| Sửa tên/vị trí | Danh sách và chi tiết hiển thị dữ liệu đã sửa trong bộ nhớ |
| Activate Node của cửa `2` khi `Sẵn sàng` tắt | Hiển thị thiếu điều kiện, chưa đổi lifecycle |
| Bật `Sẵn sàng`, activate rồi disable/reactivate | Lifecycle và lịch sử được cập nhật; thử retire trên một Node khác nếu cần |
| Thay Node `11` bằng `13` | Thiếu `Sẵn sàng` hoặc `Thu hồi` thì lỗi; bật đủ thì Node cũ `RETIRED`, Node mới `ACTIVE` |
| Mở lịch sử rồi bấm `Đặt lại` | Xem được sự kiện đã thao tác; reset phục hồi bộ dữ liệu và điều kiện ban đầu |

Các trạng thái ONLINE/OFFLINE/UNKNOWN là dữ liệu mẫu để xem cách hiển thị,
không phải heartbeat từ ESP32. Preview chứng minh được form, điều hướng,
hiển thị và phản ứng của UI với repository giả. Nó không kiểm chứng API,
transaction MySQL, ownership, JWT, bảo mật hoặc hoạt động phần cứng.

### 8.2. Chạy App với API thật sau khi ghép đăng nhập

Trong thư mục `mobile`:

```powershell
flutter pub get
flutter analyze
flutter test
flutter run --debug -t lib/main.dart -d DEVICE_ID --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` là địa chỉ host từ Android emulator. Thiết bị thật cần API_BASE_URL
trỏ tới IP máy chạy backend trong cùng mạng và cổng truy cập được; thay
`DEVICE_ID` bằng ID thiết bị đang dùng. Client dùng
`dart:io` cho Android; không cam kết chạy web bằng `flutter run -d chrome`.
HTTP local phục vụ phát triển; triển khai thật cần HTTPS.

Khi chưa được cấp `AccessTokenProvider`, màn hình báo cần đăng nhập là hành vi
đúng. Ghép token hợp lệ rồi thử luồng provision → claim → list → sửa → xem Node
→ xem lịch sử. Activate/replace vẫn cần readiness thật; bản UI không giả ACK.

## 9. Tài liệu/dữ liệu còn cần để tích hợp

Không thiếu tài liệu để viết phần nghiệp vụ backend đã chốt. Để nghiệm thu
toàn hệ thống, nhóm cần cung cấp:

| Người phụ trách | Cần cung cấp | Ảnh hưởng nếu chưa có |
| --- | --- | --- |
| Người 1 | JWT filter đặt AccountPrincipal, token provider Flutter, chính sách hết hạn/refresh | API/App thực chưa có phiên Account |
| Người 4 + firmware | ACK đúng Device/version, điều kiện cấu hình/PIN theo từng Node | Activate/reactivate bị từ chối |
| MQTT/firmware | Danh tính, credential/ACL, thu hồi Node cũ và xác nhận, retry khi lỗi | Không heartbeat thật; replacement không hoàn tất |
| Nhóm | Topic/payload/QoS/retained, chống replay, quy trình bàn giao mã và thiết bị | Chưa thể kết luận thiết bị an toàn khi vận hành |
| Người quản lý DB cũ | Mapping Account/Cardholder/Door nếu dữ liệu vẫn là schema trước v2 | Không tự di chuyển bằng baseline |

Các điểm này là công việc tích hợp đã được tách có chủ đích. Không yêu cầu
Người 2 viết lại đăng nhập, điều khiển cửa hoặc toàn bộ hệ thống cảnh báo.

## 10. Tài liệu liên quan

- [Module Map](../02-modules/_MODULE-MAP.md)
- [Module 02: quản lý cửa](../02-modules/02-device-management/README.md)
- [Module 03: lifecycle](../02-modules/03-device-lifecycle/README.md)
- [OpenAPI Người 2](../../contracts/api/door-device.openapi.yaml)
- [Quyết định sở hữu và lifecycle](../01-requirements/ownership-device-lifecycle-decisions.md)
- [Database ERD](../03-technical-reference/database-erd.md)
- [Cấu hình môi trường](environment-configuration.md)
