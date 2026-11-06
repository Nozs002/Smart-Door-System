---
title: Quản lý cửa và thiết bị
type: module
version: 1.0
lang: vi
tags: [module, door, provision, claim, ownership]
updated: 2026-10-16
---

# Quản lý cửa và thiết bị

## 1. Mục tiêu

Người 2 quản lý một bộ cửa từ lúc kỹ thuật viên đăng ký phần cứng đến lúc
khách hàng nhận sở hữu, xem và sửa thông tin cửa. Door là đối tượng được sở hữu;
Device là từng Node có thể được thay mà vẫn giữ Door và lịch sử cũ.

Tài liệu này mô tả phần backend và màn hình App được viết cùng giao diện tích
hợp. Chưa coi đăng nhập, MQTT, firmware hoặc App đã chạy end-to-end chỉ vì có API.
Xem [bàn giao và kiểm thử](../../05-backend/door-device-handoff.md) để phân biệt
phần có source, phần chưa chạy xác minh và phần cần ghép với đồng đội.

## 2. Phạm vi

### Bao gồm

- CLI kỹ thuật tạo một Door, đúng một Node 1 và một Node 2, cấu hình mặc định,
  hai bộ đếm sai RFID/PIN và mã kích hoạt trong một transaction.
- Cấp lại mã cho Door chưa có chủ, đồng thời thu hồi mã chưa dùng cũ.
- Claim bằng mã; danh sách cửa theo Account; xem, đổi tên và vị trí cửa.
- Hàm ownership dùng chung, giới hạn thử mã, xử lý retry và claim đồng thời.
- API thông tin Node, phối hợp với [module lifecycle](../03-device-lifecycle/README.md).

### Không bao gồm

- Đăng ký/đăng nhập, phát hành và xác minh JWT: Người 1.
- Cardholder, cấp RFID và đổi PIN: Người 1; access authentication: Người 3.
- Gửi cấu hình/PIN, ghi ACK đồng bộ: Người 4 và adapter thiết bị.
- Cấp credential/ACL Broker, vận chuyển MQTT và firmware thật: giai đoạn tích hợp.
- Chuyển chủ cửa, chia sẻ cửa, Admin nền tảng, xóa dữ liệu lịch sử.
- Đăng nhập Flutter: do Người 1 cung cấp; màn hình Người 2 đã có nguồn gọi REST,
  không tự tạo phiên hoặc token giả.

## 3. Thành phần liên quan

| Thành phần | Trách nhiệm |
| --- | --- |
| Backend `door` | Provision, mã claim, ownership, query/update Door |
| Backend `device` | Đăng ký Node và lịch sử ban đầu, thông tin lifecycle/kết nối |
| Backend `shared.security` | Lấy Account đã được xác thực qua adapter; không tin ID trong request |
| MySQL | FK, unique, transaction và khóa hàng; giữ dấu vết mã đã dùng |
| Mobile | Danh sách, claim, chi tiết/sửa cửa và thiết bị/lịch sử; token từ Người 1 |
| MQTT/firmware | Xác minh danh tính và trao đổi cấu hình sau này; không được giả bằng `device_code` |

## 4. Actor và quyền hạn

| Actor | Quyền hoặc hành động |
| --- | --- |
| Quy trình kỹ thuật qua CLI | Provision bộ cửa, cấp lại mã cho cửa chưa claim, đăng ký Node thay thế |
| Account ACTIVE đã xác thực | Claim bằng mã; xem/sửa cửa mình sở hữu |
| Account khác | Không xem/sửa được Door; trả 404 như khi Door không tồn tại |
| Module Người 1/3/4 | Gọi ownership service trước thao tác theo Door |

CLI sử dụng quyền truy cập môi trường/database của quy trình kỹ thuật; không
có endpoint công khai tương đương và không bổ sung role Admin cho Account.

## 5. Luồng chính

### 5.1. Provision

1. Kỹ thuật viên chạy CLI, cung cấp tên/vị trí Door và hai `device_code` khác nhau.
2. Kiểm tra dữ liệu, mã thiết bị không trùng và vai trò Node 1/Node 2.
3. Tạo Door `UNCLAIMED`, không chủ; hai Device `PROVISIONED`.
4. Tạo DoorConfig mặc định, hai dòng LockoutStatus RFID/PIN và sự kiện
   `PROVISIONED` cho từng Node. Không tự tạo PIN dùng chung.
5. Sinh mã kích hoạt ngẫu nhiên, lưu hash cùng hạn sử dụng.
6. Sau khi transaction thành công, in mã gốc một lần cho kỹ thuật viên bàn giao.

Nếu một bước lỗi, rollback cả bộ. CLI không phải cơ chế retry provision tự động:
chạy lại với `device_code` đã tồn tại bị từ chối thay vì tạo bộ cửa thứ hai.

### 5.2. Claim

1. Account đã được xác thực gửi `POST /api/v1/doors/claim` với `activationCode`.
2. Kiểm tra giới hạn thử mã trên Account và IP kết nối trực tiếp; chuẩn hóa mã.
3. Tìm mã theo hash; khóa Door/mã trong transaction và kiểm tra lại trạng thái.
4. Kiểm tra chưa thu hồi, chưa hết hạn, bộ Node hợp lệ và Door chưa có chủ.
5. Gán `owner_account_id`, chuyển Door sang `SETUP_PENDING`, đánh dấu mã đã dùng.
6. Trả Door; không tự activate Node và không gửi lệnh mở khóa.

Retry cùng Account với đúng mã đã dùng thành công trả cùng Door, kể cả khi mã
đã qua ngày hết hạn sau lần dùng đầu. Account khác không nhận được quyền hoặc
thông tin chi tiết bộ cửa. Hai Account claim đồng thời chỉ một bên được sở hữu.

### 5.3. Xem/sửa cửa

Danh sách chỉ truy vấn Door thuộc Account hiện tại. Xem/sửa theo ID phải kiểm
tra ownership. PATCH nhận `name` và `location`; tên có nội dung, tối đa 100 ký
tự; vị trí tùy chọn, tối đa 255 ký tự. Không sửa chủ, ID hoặc trạng thái qua API này.

## 6. Luồng lỗi và ngoại lệ

| Trường hợp | Cách xử lý |
| --- | --- |
| Chưa có xác thực/không nhận diện Account | 401; không dùng Account demo mặc định |
| Account bị vô hiệu hóa | Không cho dùng nghiệp vụ dù còn SecurityContext |
| Mã sai định dạng, không tồn tại, hết hạn, bị thu hồi hoặc đã thuộc Account khác | Từ chối claim, không đổi dữ liệu; không trả ID/chủ của bộ cửa |
| Thử mã vượt ngưỡng | 429; đợi cửa sổ giới hạn kết thúc |
| Door không tồn tại/không thuộc Account | 404 |
| Hai request claim/đổi dữ liệu xung đột | Khóa transaction và unique bảo vệ; không ghi chủ thứ hai |
| Trùng device_code khi provision | Từ chối, rollback Door, Node, mã và lịch sử đã tạo |
| Cấp lại mã cho cửa đã claim | Từ chối; không dùng cấp lại để chuyển quyền sở hữu |

Response thành công dùng `ApiResponse<T>`; lỗi dùng `application/problem+json`
và `errorCode`. Chi tiết schema nằm trong OpenAPI; không bọc lỗi trong response
thành công hoặc trả mã kích hoạt/hash trong DTO.

## 7. Quy tắc nghiệp vụ

- Mã gồm 8 ký tự chữ/số, trình bày `XXXX-XXXX`, loại `0`, `O`, `1`, `I`, `L`.
  Chấp nhận nhập dạng có dấu gạch hoặc liền nhau, không phân biệt hoa/thường.
- Mã dùng một lần, hiệu lực 7 ngày; chỉ lưu hash và dấu vết dùng/thu hồi.
  Cấp lại mã giữ lịch sử cũ, thu hồi các mã chưa dùng còn hiệu lực.
- Giới hạn claim là 5 lần/phút trên Account và IP. Bộ giới hạn hiện tại nằm
  trong bộ nhớ một instance; triển khai nhiều instance phải thay bằng bộ lưu
  chia sẻ. Không tin `X-Forwarded-For` do client tự đặt.
- `device_code` là định danh công khai, không thay thế mã kích hoạt hay credential.
- Ownership lấy từ Account xác thực, không có trường `accountId` trong request claim/update.
- `requireOwner(accountId, doorId)` là hàm dùng chung cho cả đọc và ghi nghiệp vụ.
- Claim thành công không đồng nghĩa đã hoàn thành cấu hình, PIN hoặc xác minh thiết bị.
- Không log mã gốc, hash, PIN, token, mật khẩu hoặc credential. Đầu ra mã một lần
  của CLI là bàn giao bí mật có chủ đích, cần bảo vệ như phiếu kích hoạt.

## 8. Dữ liệu và tích hợp

| Bảng | Vai trò |
| --- | --- |
| `doors` | Chủ sở hữu, tên, vị trí, trạng thái và version |
| `door_activation_codes` | Hash, hạn dùng, consumed/revoked |
| `devices` | Node thuộc Door và vai trò cố định |
| `device_lifecycle_events` | Lịch sử provision và vòng đời |
| `door_configs`, `lockout_statuses` | Dữ liệu nền được tạo khi provision |
| `accounts` | Account do Người 1 quản lý; không tạo Account mới trong module này |

| REST API | Mục đích |
| --- | --- |
| `GET /api/v1/doors?page=0&size=20` | Danh sách cửa thuộc Account |
| `POST /api/v1/doors/claim` | Claim bằng mã |
| `GET /api/v1/doors/{doorId}` | Chi tiết cửa thuộc Account |
| `PATCH /api/v1/doors/{doorId}` | Đổi tên/vị trí |

REST contract: [door-device.openapi.yaml](../../../contracts/api/door-device.openapi.yaml).
MQTT topic/payload thật chưa triển khai trong giai đoạn này.

Người 1 cần ghép danh tính đã xác minh vào `CurrentAccountProvider`; Người 2
cung cấp ownership service. Chưa có adapter xác thực thì API từ chối 401;
không bật cách truyền Account ID tùy ý bằng HTTP để thử nhanh.

## 9. Kiểm thử

Đây là checklist nghiệm thu, không phải báo cáo tất cả đã chạy thành công.
Lệnh chạy và trạng thái xác minh ở [hướng dẫn bàn giao](../../05-backend/door-device-handoff.md).

- [ ] Provision tạo đúng bộ Door/Node, cấu hình, hai lockout, mã và event.
- [ ] Provision lỗi/trùng mã thiết bị rollback tất cả dữ liệu liên quan.
- [ ] Hash khác mã gốc; mã đúng định dạng và hạn 7 ngày.
- [ ] Claim đúng; retry cùng Account nhất quán; Account khác không chiếm được cửa.
- [ ] Hai Account claim đồng thời trên MySQL chỉ có một chủ.
- [ ] Cấp lại làm mã cũ vô hiệu; không cấp lại sau claim.
- [ ] Giới hạn thử mã hoạt động độc lập trên Account/IP.
- [ ] Query/update không đọc hoặc sửa cửa Account khác.
- [ ] Chưa xác thực nhận 401; DTO không lộ mã/hash/chủ khác.

## 10. Vấn đề cần làm rõ

Không cần bổ sung tài liệu nghiệp vụ để bắt đầu backend của module này.
Các đầu nối cần hoàn thiện để nghiệm thu tích hợp:

- [ ] Người 1 cung cấp JWT/principal Account đã xác minh, gồm chính sách Account DISABLED.
- [ ] Nhóm chỉ định người phụ trách cấp/thu hồi credential và ACL MQTT.
- [ ] MQTT/firmware xác minh được đúng Node, cấu hình/PIN và quy trình bàn giao vật lý.
- [ ] Khi triển khai nhiều backend, thay rate limiter trong bộ nhớ bằng lưu trữ chung.
- [ ] Ghép token provider của Người 1 và nghiệm thu các màn hình Flutter với backend thật.

## 11. Tài liệu liên quan

- [Module Map](../_MODULE-MAP.md)
- [Quyết định sở hữu và vòng đời](../../01-requirements/ownership-device-lifecycle-decisions.md)
- [Database ERD](../../03-technical-reference/database-erd.md)
- [Device Lifecycle](../03-device-lifecycle/README.md)
- [Bàn giao backend Người 2](../../05-backend/door-device-handoff.md)
