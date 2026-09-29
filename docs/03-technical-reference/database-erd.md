---
title: Database ERD
type: technical-reference
version: 1.0
lang: vi
tags: [database, erd, mysql]
updated: 2026-09-29
---

# Database ERD

Tài liệu này giải thích các bảng dữ liệu của Smart Door System và đối chiếu giữa:

- **Schema hiện tại**: cấu trúc đang được khởi tạo bởi
  [`infrastructure/mysql/init/init.sql`](../../infrastructure/mysql/init/init.sql).
- **Mô hình mục tiêu**: thiết kế nghiệp vụ trong Business Analysis Document (BAD),
  dùng làm định hướng hoàn thiện ERD và schema.

Khi hai nguồn khác nhau, `init.sql` được xem là cấu trúc đang triển khai. Những
thuộc tính chỉ có trong BAD được ghi rõ là đề xuất, không được xem là đã tồn tại
trong database.

## 1. Nguyên tắc dữ liệu

- PIN mở cửa và mật khẩu đăng nhập App không được lưu dạng rõ.
- Mọi lần thử truy cập phải có thể ghi nhận cả kết quả chấp nhận và từ chối.
- `event_id` phải ổn định để thiết bị gửi lại sự kiện offline mà không tạo log
  trùng.
- Vô hiệu hóa người dùng hoặc thu hồi thẻ không được làm mất lịch sử đã ghi.
- Trạng thái nhận lệnh mở khóa không đồng nghĩa cửa vật lý đã mở nếu chưa có cảm
  biến xác nhận.
- Một `Door` là một cửa nghiệp vụ. Hai ESP32 phục vụ cùng một cửa không được mô
  hình thành hai cửa riêng.

## 2. Các bảng trong schema hiện tại

### 2.1. `users` — Người dùng

Lưu quản trị viên, người dùng App và người được cấp thẻ RFID. Người chỉ được cấp
thẻ có thể không có tài khoản đăng nhập App.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh người dùng. |
| `display_name` | Bắt buộc | Tên hiển thị khi cấp thẻ hoặc xem thông tin. |
| `username` | UNIQUE, có thể NULL | Tên đăng nhập App. |
| `password_hash` | Có thể NULL | Mật khẩu App đã băm; không phải PIN mở cửa. |
| `created_at` | Bắt buộc | Thời điểm tạo. |
| `role` | `ADMIN`, `USER` | Vai trò trong ứng dụng. |
| `phone` | UNIQUE, có thể NULL | Số điện thoại. |
| `name` | Có thể NULL | Họ tên đầy đủ. |

BAD đề xuất thêm trạng thái người dùng để hỗ trợ vô hiệu hóa tài khoản mà không
xóa lịch sử. Cột này chưa có trong schema hiện tại.

### 2.2. `rfid_credentials` — Thẻ RFID đã cấp

Lưu các thẻ RFID đã đăng ký. Thẻ lạ không cần có bản ghi trong bảng này; lần
quẹt thẻ lạ được ghi vào nhật ký truy cập.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh bản ghi thẻ. |
| `uid` | UNIQUE, bắt buộc | Giá trị dùng để đối chiếu UID thẻ. |
| `status` | `ACTIVE`, `REVOKED`, `EXPIRED` | Trạng thái vòng đời của thẻ. |
| `issued_at` | Có thể NULL | Thời điểm cấp thẻ. |
| `revoked_at` | Có thể NULL | Thời điểm thu hồi. Bắt buộc khi trạng thái là `REVOKED`. |
| `user_id` | FK → `users.id` | Người được cấp thẻ. |

Quan hệ hiện tại là một người dùng có thể có nhiều thẻ. Khi xóa người dùng,
schema hiện tại xóa dây chuyền các thẻ của người đó.

BAD đề xuất thêm `expires_at` để quản lý hạn sử dụng. Thuộc tính này chưa có
trong schema.

### 2.3. `doors` — Cửa

Lưu cửa mà hệ thống giám sát và điều khiển.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh cửa. |
| `name` | Bắt buộc | Tên hiển thị của cửa. |
| `operating_mode` | `AUTOMATIC`, `SECURITY` | Chế độ vận hành. |
| `updated_at` | Tự cập nhật | Thời điểm thay đổi gần nhất. |

BAD đề xuất `commanded_lock_state` để lưu trạng thái chốt theo lệnh gần nhất.
Giá trị này chỉ phản ánh lệnh điều khiển, không khẳng định trạng thái vật lý của
cửa. Cột này chưa có trong schema.

### 2.4. `door_configs` — Cấu hình cửa

Lưu các quy tắc và ngưỡng vận hành.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh cấu hình. |
| `max_failed_attempts` | Số dương, mặc định 5 | Số lần sai dẫn đến khóa tạm thời. |
| `lockout_seconds` | Số dương, mặc định 300 | Thời gian khóa, tính bằng giây. |
| `temperature_threshold` | Bắt buộc | Ngưỡng nhiệt độ cảnh báo. |
| `open_timeout_seconds` | Số dương hoặc NULL | Thời gian cửa được phép mở trước khi cảnh báo. |

Schema hiện tại chưa có `door_id`, vì vậy chưa xác định cấu hình nào thuộc cửa
nào. BAD đề xuất dùng `door_id` đồng thời làm PK và FK tới `doors.id`, tạo quan
hệ 1–1 giữa cửa và cấu hình, đồng thời bổ sung `updated_at`.

### 2.5. `system_pins` — PIN chung mở cửa

Lưu PIN dùng chung để mở cửa. Đây là dữ liệu khác với mật khẩu đăng nhập App.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh bản ghi PIN. |
| `pin_hash` | Bắt buộc | PIN đã băm; không lưu PIN thuần. |
| `status` | BOOLEAN, mặc định TRUE | PIN có đang hiệu lực hay không. |
| `changed_at` | Bắt buộc | Thời điểm thay đổi PIN. |

Schema hiện tại cho phép lưu lịch sử PIN bằng cách vô hiệu hóa bản ghi cũ và tạo
bản ghi mới. Tuy nhiên, chưa có ràng buộc bảo đảm chỉ một PIN hoạt động và chưa
xác định PIN thuộc cửa nào.

BAD đề xuất mô hình mỗi cửa có tối đa một PIN chung hiện hành:

- `door_id` vừa là PK vừa là FK tới `doors.id`.
- `pin_hash` chứa giá trị đã bảo vệ.
- `status` thể hiện hiệu lực.
- `version` hỗ trợ theo dõi đồng bộ PIN xuống thiết bị.
- `changed_at` lưu lần đổi gần nhất.

### 2.6. `devices` — Thiết bị ESP32

Lưu Node 1 và Node 2 đã đăng ký với hệ thống.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh thiết bị trong database. |
| `device_code` | UNIQUE, bắt buộc | Mã thiết bị khi đăng ký và trao đổi dữ liệu. |
| `node_type` | `NODE_1`, `NODE_2` | Loại node xác thực hoặc điều khiển. |
| `status` | `ACTIVE`, `INACTIVE`, `DISABLED` | Trạng thái sử dụng. |
| `last_heartbeat_at` | Có thể NULL | Lần gần nhất Backend nhận heartbeat. |
| `registered_at` | Bắt buộc | Thời điểm đăng ký. |

Schema hiện tại chưa liên kết thiết bị với cửa. BAD đề xuất thêm `door_id` làm FK
tới `doors.id`, tạo quan hệ một cửa có nhiều thiết bị. Sa bàn hiện tại dự kiến
có một Node 1 và một Node 2 cho cùng một cửa.

### 2.7. `access_logs` — Nhật ký truy cập

Lưu các lần thử mở cửa, mở thành công, bị từ chối hoặc thất bại khi điều khiển.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh dòng log. |
| `event_id` | UNIQUE, bắt buộc | Chống ghi trùng khi gửi bù dữ liệu offline. |
| `method` | ENUM | `RFID`, `PIN`, `APP`, `AUTO_PROXIMITY`, `FIRE_EMERGENCY`. |
| `result` | ENUM | `GRANTED`, `DENIED`, `FAILED`. |
| `reason` | Có thể NULL | Lý do từ chối, thất bại hoặc mở khẩn cấp. |
| `occurred_at` | Bắt buộc | Thời điểm sự kiện xảy ra tại nguồn. |

Schema hiện tại chưa cho biết sự kiện thuộc cửa, người dùng, thẻ hay thiết bị
nào. BAD đề xuất bổ sung:

- `door_id` — FK bắt buộc tới cửa phát sinh sự kiện.
- `user_id` — FK có thể NULL khi không xác định được người dùng.
- `rfid_credential_id` — FK có thể NULL khi không có thẻ đã nhận diện.
- `source_device_id` — FK có thể NULL khi không xác định được Node nguồn.
- `received_at` — thời điểm Backend nhận và lưu sự kiện.
- Cho phép `occurred_at` là NULL nếu Node không có đồng hồ đáng tin cậy.

Các trường hợp điển hình:

- Mở bằng PIN chung: `user_id` và `rfid_credential_id` là NULL.
- Quẹt thẻ lạ: `user_id` và `rfid_credential_id` là NULL.
- Mở từ App: thường có `user_id`, không có `rfid_credential_id`.

### 2.8. `lockout_statuses` — Trạng thái khóa tạm thời

Lưu bộ đếm sai và thời điểm hết khóa. Bảng này khác `door_configs`:
`door_configs` giữ quy tắc, còn `lockout_statuses` giữ trạng thái đang diễn ra.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh trạng thái. |
| `method` | UNIQUE, `RFID` hoặc `PIN` | Phương thức đang được theo dõi. |
| `failed_count` | Mặc định 0 | Số lần sai liên tiếp. |
| `locked_until` | Có thể NULL | Thời điểm hết khóa. |
| `updated_at` | Tự cập nhật | Lần cập nhật bộ đếm gần nhất. |

Ràng buộc UNIQUE trên `method` làm trạng thái khóa mang tính toàn hệ thống. BAD
đề xuất đổi thành khóa chính ghép `(door_id, method)` để mỗi cửa có bộ đếm RFID
và PIN riêng. Khi xác thực thành công hoặc hết thời gian khóa, bộ đếm phải được
xóa theo quy tắc nghiệp vụ đã thống nhất.

### 2.9. `alerts` — Cảnh báo

Lưu các cảnh báo phát sinh trong hệ thống.

| Cột | Ràng buộc | Ý nghĩa |
| --- | --- | --- |
| `id` | PK, tự tăng | Định danh cảnh báo. |
| `type` | ENUM | `TOO_MANY_FAILURES`, `HIGH_TEMPERATURE`, `DOOR_OPEN_TOO_LONG`. |
| `status` | ENUM | `NEW`, `ACKNOWLEDGED`, `RESOLVED`. |
| `triggered_at` | Bắt buộc | Thời điểm phát sinh. |
| `resolved_at` | Có thể NULL | Thời điểm kết thúc; bắt buộc khi đã `RESOLVED`. |

Schema hiện tại chưa xác định cảnh báo thuộc cửa nào và chưa biểu diễn mức độ.
BAD đề xuất thêm `door_id` làm FK tới `doors.id` và thêm `severity`.

## 3. Quan hệ dữ liệu

### 3.1. Quan hệ đã triển khai

| Quan hệ | Bội số | Cách triển khai |
| --- | --- | --- |
| `users` — `rfid_credentials` | 1–N | `rfid_credentials.user_id` → `users.id`. |

Đây là quan hệ có khóa ngoại duy nhất trong schema hiện tại. Các bảng còn lại
đang đứng độc lập.

### 3.2. Quan hệ mục tiêu theo BAD

| Quan hệ | Bội số | Khóa ngoại hoặc khóa chính đề xuất |
| --- | --- | --- |
| `users` — `rfid_credentials` | 1–N | `rfid_credentials.user_id`. |
| `doors` — `door_configs` | 1–1 | `door_configs.door_id` vừa là PK vừa là FK. |
| `doors` — `system_pins` | 1–1 | `system_pins.door_id` vừa là PK vừa là FK. |
| `doors` — `devices` | 1–N | `devices.door_id`. |
| `doors` — `access_logs` | 1–N | `access_logs.door_id`. |
| `users` — `access_logs` | 1–N, tùy trường hợp | `access_logs.user_id`, cho phép NULL. |
| `rfid_credentials` — `access_logs` | 1–N, tùy trường hợp | `access_logs.rfid_credential_id`, cho phép NULL. |
| `devices` — `access_logs` | 1–N, tùy trường hợp | `access_logs.source_device_id`, cho phép NULL. |
| `doors` — `lockout_statuses` | 1–N | PK ghép `(door_id, method)`. |
| `doors` — `alerts` | 1–N | `alerts.door_id`. |

## 4. Chênh lệch cần xử lý

| Khu vực | Schema hiện tại | Mô hình BAD |
| --- | --- | --- |
| Phạm vi PIN | PIN toàn hệ thống, có nhiều bản ghi | Một PIN hiện hành cho mỗi cửa, có phiên bản đồng bộ |
| Cấu hình cửa | Không có FK tới cửa | Quan hệ 1–1 với cửa |
| Thiết bị | Không biết thiết bị thuộc cửa nào | Mỗi thiết bị thuộc một cửa |
| Nhật ký | Không liên kết cửa/người/thẻ/thiết bị | Có các FK tùy theo nguồn sự kiện |
| Khóa tạm thời | Một trạng thái cho mỗi phương thức trên toàn hệ thống | Một trạng thái cho mỗi `(door, method)` |
| Cảnh báo | Không liên kết cửa, không có mức độ | Thuộc một cửa và có `severity` |
| Trạng thái người dùng | Chưa có | Có trạng thái hoạt động/vô hiệu hóa |
| Hạn thẻ RFID | Trạng thái có `EXPIRED` nhưng không có ngày hết hạn | Có `expires_at` |

Các thay đổi ở mục này là đề xuất thiết kế. Cần cập nhật migration, mock data,
Backend entity/repository, API contract, firmware sync và kiểm thử cùng nhau khi
nhóm quyết định triển khai.

## 5. Quy tắc PIN và khóa tạm thời cần chốt

BAD tạm chọn PIN dùng chung, ngưỡng `N = 3` và khóa `T = 60` giây. Trong khi đó,
schema hiện tại mặc định `max_failed_attempts = 5` và `lockout_seconds = 300`.
Dữ liệu mẫu còn sử dụng các giá trị khác nhau. Nhóm cần thống nhất một chính
sách trước khi triển khai rule engine để tránh Backend, firmware và giao diện
hiển thị khác nhau.

Không được ghi PIN thuần vào `access_logs`, cảnh báo, tài liệu mẫu hoặc log ứng
dụng. Nhật ký chỉ lưu phương thức, kết quả và lý do tổng quát.

## 6. Nguồn tham chiếu

- [Schema MySQL hiện tại](../../infrastructure/mysql/init/init.sql)
- [Dữ liệu mẫu MySQL](../../infrastructure/mysql/init/mockdata.sql)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- Business Analysis Document Smart Access Control IoT, phiên bản 1.0.1,
  ngày 23/09/2026 (tệp người dùng cung cấp).
