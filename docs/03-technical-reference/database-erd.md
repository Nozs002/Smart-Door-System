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
- Hệ thống chỉ quản lý **một cửa** (`Door`). Hai ESP32 cùng phục vụ cửa này và
  không được mô hình thành hai cửa riêng.
- Hệ thống có đúng hai node đang hoạt động với trách nhiệm tách biệt:
  - **Node 1 — Trạm Tương tác & Xác thực**: đặt ngoài cửa, đọc RFID/PIN, hiển thị
    thông tin và gửi yêu cầu xác thực về Backend; không nối trực tiếp với chốt
    khóa.
  - **Node 2 — Trạm Điều khiển & Cảnh báo an ninh**: đặt trong nhà, điều khiển
    relay khóa cửa, nút Exit, cảm biến nhiệt độ và còi cảnh báo.

## 2. Các bảng trong schema hiện tại

Kiểu dữ liệu dưới đây được lấy trực tiếp từ `init.sql`. Trong MySQL,
`BOOLEAN` là bí danh của `TINYINT(1)`, còn `DATETIME(3)` lưu thời gian với độ
chính xác đến mili giây.

### 2.1. `users` — Người dùng

Lưu quản trị viên, người dùng App và người được cấp thẻ RFID. Người chỉ được cấp
thẻ có thể không có tài khoản đăng nhập App.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh người dùng. |
| `display_name` | `VARCHAR(100)` | Bắt buộc | Tên hiển thị khi cấp thẻ hoặc xem thông tin. |
| `username` | `VARCHAR(100)` | UNIQUE, có thể NULL | Tên đăng nhập App. |
| `password_hash` | `VARCHAR(255)` | Có thể NULL | Mật khẩu App đã băm; không phải PIN mở cửa. |
| `created_at` | `DATETIME(3)` | Bắt buộc, mặc định thời điểm hiện tại | Thời điểm tạo. |
| `role` | `ENUM('ADMIN', 'USER')` | Bắt buộc, mặc định `USER` | Vai trò trong ứng dụng. |
| `status` | `ENUM('ACTIVE', 'DISABLED')` | Bắt buộc, mặc định `ACTIVE` | Trạng thái sử dụng tài khoản và thông tin xác thực của người dùng. |
| `phone` | `VARCHAR(20)` | UNIQUE, có thể NULL | Số điện thoại. |
| `name` | `VARCHAR(100)` | Có thể NULL | Họ tên đầy đủ. |

`status` cho phép vô hiệu hóa người dùng mà không xóa bản ghi hoặc lịch sử. Khi
chuyển người dùng sang `DISABLED`, Backend phải thực hiện trong cùng một giao
dịch:

1. Từ chối đăng nhập mới và mọi yêu cầu mở cửa gắn với người dùng đó.
2. Chuyển toàn bộ thẻ trong `rfid_credentials` của người dùng sang `REVOKED` và
   ghi `revoked_at` nếu chưa có.
3. Vô hiệu hóa toàn bộ thông tin vân tay của người dùng khi bảng quản lý vân tay
   được bổ sung vào schema.
4. Giữ nguyên người dùng, thẻ, vân tay và nhật ký cũ để phục vụ truy vết.

Việc đổi `users.status` không tự kích hoạt `ON DELETE CASCADE`, vì không có thao
tác xóa. Quy tắc vô hiệu hóa liên quan phải được Backend thực hiện bằng một giao
dịch nguyên tử. Khi chuyển người dùng trở lại `ACTIVE`, hệ thống không tự khôi
phục thẻ hoặc vân tay đã bị thu hồi; quản trị viên phải cấp lại rõ ràng.

### 2.2. `rfid_credentials` — Thẻ RFID đã cấp

Lưu các thẻ RFID đã đăng ký. Thẻ lạ không cần có bản ghi trong bảng này; lần
quẹt thẻ lạ được ghi vào nhật ký truy cập.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh bản ghi thẻ. |
| `uid` | `VARCHAR(128)` | UNIQUE, bắt buộc | Giá trị dùng để đối chiếu UID thẻ. |
| `status` | `ENUM('ACTIVE', 'REVOKED', 'EXPIRED')` | Bắt buộc, mặc định `ACTIVE` | Trạng thái vòng đời của thẻ. |
| `issued_at` | `DATETIME(3)` | Có thể NULL | Thời điểm cấp thẻ. |
| `revoked_at` | `DATETIME(3)` | Có thể NULL | Thời điểm thu hồi. Bắt buộc khi trạng thái là `REVOKED`. |
| `user_id` | `BIGINT UNSIGNED` | Bắt buộc, FK → `users.id` | Người được cấp thẻ. |

Quan hệ hiện tại là một người dùng có thể có nhiều thẻ. Khi xóa người dùng,
schema hiện tại xóa dây chuyền các thẻ của người đó.

BAD đề xuất thêm `expires_at` để quản lý hạn sử dụng. Thuộc tính này chưa có
trong schema.

### 2.3. `doors` — Cửa duy nhất của hệ thống

Lưu cửa duy nhất mà hệ thống giám sát và điều khiển. Schema hiện tại vẫn cho
phép nhiều bản ghi để không làm mất tính tổng quát của cấu trúc SQL, nhưng phạm
vi triển khai của dự án chỉ sử dụng một bản ghi cửa.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh cửa. |
| `name` | `VARCHAR(100)` | Bắt buộc | Tên hiển thị của cửa. |
| `operating_mode` | `ENUM('AUTOMATIC', 'SECURITY')` | Bắt buộc, mặc định `SECURITY` | Chế độ vận hành. |
| `commanded_lock_state` | `ENUM('LOCKED', 'UNLOCKED')` | Có thể NULL, mặc định NULL | Trạng thái chốt theo lệnh điều khiển gần nhất. |

`commanded_lock_state` chỉ phản ánh lệnh điều khiển gần nhất mà hệ thống đã ghi
nhận, không khẳng định trạng thái vật lý của cửa. `LOCKED` biểu thị lệnh khóa gần
nhất, `UNLOCKED` biểu thị lệnh mở khóa gần nhất và `NULL` nghĩa là chưa ghi nhận
lệnh điều khiển nào. Muốn xác định trạng thái vật lý cần có cảm biến phản hồi và
một trường dữ liệu riêng.

### 2.4. `door_configs` — Cấu hình cửa

Lưu các quy tắc và ngưỡng vận hành.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh cấu hình. |
| `door_id` | `BIGINT UNSIGNED` | Bắt buộc, UNIQUE, FK → `doors.id` | Cửa sử dụng cấu hình này. |
| `max_failed_attempts` | `INT UNSIGNED` | Bắt buộc, số dương, mặc định 3 | Số lần sai dẫn đến khóa tạm thời; có thể cấu hình. |
| `lockout_seconds` | `INT UNSIGNED` | Bắt buộc, số dương, mặc định 60 | Thời gian khóa tính bằng giây; có thể cấu hình. |
| `temperature_threshold` | `DECIMAL(5,2)` | Bắt buộc | Ngưỡng nhiệt độ cảnh báo. |
| `open_timeout_seconds` | `INT UNSIGNED` | Số dương hoặc NULL | Thời gian cửa được phép mở trước khi cảnh báo. |

`door_id` tạo quan hệ 1–1 giữa cửa và cấu hình nhờ ràng buộc `UNIQUE`. Khi mã cửa
thay đổi, khóa ngoại được cập nhật theo; khi cửa bị xóa, cấu hình tương ứng cũng
bị xóa. Schema vẫn giữ `id` làm khóa chính độc lập. BAD còn đề xuất bổ sung
`updated_at`, nhưng cột này chưa có trong schema hiện tại.

### 2.5. `system_pins` — PIN chung mở cửa

Lưu PIN dùng chung để mở cửa. Đây là dữ liệu khác với mật khẩu đăng nhập App.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh bản ghi PIN. |
| `pin_hash` | `VARCHAR(255)` | Bắt buộc | PIN đã băm; không lưu PIN thuần. |
| `status` | `BOOLEAN` | Bắt buộc, mặc định `TRUE` | PIN có đang hiệu lực hay không. |
| `changed_at` | `DATETIME(3)` | Bắt buộc, mặc định thời điểm hiện tại | Thời điểm thay đổi PIN. |

Schema hiện tại cho phép lưu lịch sử PIN bằng cách vô hiệu hóa bản ghi cũ và tạo
bản ghi mới. Tuy nhiên, chưa có ràng buộc bảo đảm chỉ một PIN hoạt động và chưa
xác định PIN thuộc cửa nào.

Với phạm vi một cửa, mô hình mục tiêu có tối đa một PIN chung hiện hành:

- `door_id` vừa là PK vừa là FK tới `doors.id`.
- `pin_hash` chứa giá trị đã bảo vệ.
- `status` thể hiện hiệu lực.
- `version` hỗ trợ theo dõi đồng bộ PIN xuống thiết bị.
- `changed_at` lưu lần đổi gần nhất.

### 2.6. `devices` — Thiết bị ESP32

Lưu hai ESP32 phục vụ cửa duy nhất của hệ thống.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh thiết bị trong database. |
| `device_code` | `VARCHAR(100)` | UNIQUE, bắt buộc | Mã thiết bị khi đăng ký và trao đổi dữ liệu. |
| `node_type` | `ENUM('NODE_1', 'NODE_2')` | Bắt buộc | Vai trò phần cứng của node. |
| `status` | `ENUM('ACTIVE', 'INACTIVE', 'DISABLED')` | Bắt buộc, mặc định `INACTIVE` | Trạng thái sử dụng. |
| `last_heartbeat_at` | `DATETIME(3)` | Có thể NULL | Lần gần nhất Backend nhận heartbeat. |
| `registered_at` | `DATETIME(3)` | Bắt buộc, mặc định thời điểm hiện tại | Thời điểm đăng ký. |

Ý nghĩa của `node_type`:

| Giá trị | Vai trò | Thiết bị chính |
| --- | --- | --- |
| `NODE_1` | Trạm Tương tác & Xác thực ngoài cửa | RFID RC522, keypad, OLED |
| `NODE_2` | Trạm Điều khiển & Cảnh báo trong nhà | Relay khóa, nút Exit, DHT11, buzzer |

Node 1 chỉ thu nhận RFID/PIN và gửi yêu cầu xác thực; node này không được nối
trực tiếp với chốt khóa. Khi Backend chấp nhận yêu cầu, Node 2 nhận lệnh MQTT và
kích hoạt relay để mở khóa. Node 2 đồng thời xử lý cảnh báo nhiệt độ và cảnh báo
xâm nhập.

Schema hiện tại chưa liên kết thiết bị với cửa và cũng chưa bảo đảm chỉ có một
Node 1 cùng một Node 2 đang hoạt động. Mô hình mục tiêu bổ sung `door_id` làm FK
tới `doors.id`. Trong phạm vi dự án, cửa duy nhất có đúng hai node hoạt động;
database vẫn có thể giữ bản ghi node cũ ở trạng thái `INACTIVE` hoặc `DISABLED`
khi thay thế phần cứng.

### 2.7. `access_logs` — Nhật ký truy cập

Lưu các lần thử mở cửa, mở thành công, bị từ chối hoặc thất bại khi điều khiển.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh dòng log. |
| `event_id` | `VARCHAR(100)` | UNIQUE, bắt buộc | Chống ghi trùng khi gửi bù dữ liệu offline. |
| `method` | `ENUM('RFID', 'PIN', 'APP', 'AUTO_PROXIMITY', 'FIRE_EMERGENCY')` | Bắt buộc | Phương thức truy cập hoặc mở khẩn cấp. |
| `result` | `ENUM('GRANTED', 'DENIED', 'FAILED')` | Bắt buộc | Kết quả xử lý sự kiện. |
| `reason` | `VARCHAR(500)` | Có thể NULL | Lý do từ chối, thất bại hoặc mở khẩn cấp. |
| `occurred_at` | `DATETIME(3)` | Bắt buộc | Thời điểm sự kiện xảy ra tại nguồn. |

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

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh trạng thái. |
| `method` | `ENUM('RFID', 'PIN')` | UNIQUE, bắt buộc | Phương thức đang được theo dõi. |
| `failed_count` | `INT UNSIGNED` | Bắt buộc, mặc định 0 | Số lần sai liên tiếp. |
| `locked_until` | `DATETIME(3)` | Có thể NULL | Thời điểm hết khóa. |
| `updated_at` | `DATETIME(3)` | Bắt buộc, tự cập nhật | Lần cập nhật bộ đếm gần nhất. |

Ràng buộc UNIQUE trên `method` làm trạng thái khóa mang tính toàn hệ thống. BAD
đề xuất đổi thành khóa chính ghép `(door_id, method)` để cửa duy nhất có bộ đếm
RFID và PIN riêng. Khi xác thực thành công hoặc hết thời gian khóa, bộ đếm phải
được xóa theo quy tắc nghiệp vụ đã thống nhất.

### 2.9. `alerts` — Cảnh báo

Lưu các cảnh báo phát sinh trong hệ thống.

| Cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK, tự tăng | Định danh cảnh báo. |
| `type` | `ENUM('TOO_MANY_FAILURES', 'HIGH_TEMPERATURE', 'DOOR_OPEN_TOO_LONG')` | Bắt buộc | Loại cảnh báo. |
| `status` | `ENUM('NEW', 'ACKNOWLEDGED', 'RESOLVED')` | Bắt buộc, mặc định `NEW` | Trạng thái xử lý. |
| `triggered_at` | `DATETIME(3)` | Bắt buộc, mặc định thời điểm hiện tại | Thời điểm phát sinh. |
| `resolved_at` | `DATETIME(3)` | Có thể NULL | Thời điểm kết thúc; bắt buộc khi đã `RESOLVED`. |

Schema hiện tại chưa xác định cảnh báo thuộc cửa nào và chưa biểu diễn mức độ.
BAD đề xuất thêm `door_id` làm FK tới `doors.id` và thêm `severity`.

## 3. Quan hệ dữ liệu

### 3.1. Quan hệ đã triển khai

| Quan hệ | Bội số | Cách triển khai |
| --- | --- | --- |
| `users` — `rfid_credentials` | 1–N | `rfid_credentials.user_id` → `users.id`. |
| `doors` — `door_configs` | 1–1 | `door_configs.door_id` → `doors.id`, có ràng buộc UNIQUE. |

Các bảng còn lại chưa có khóa ngoại trong schema hiện tại.

### 3.2. Quan hệ mục tiêu theo BAD

| Quan hệ | Bội số | Khóa ngoại hoặc khóa chính đề xuất |
| --- | --- | --- |
| `users` — `rfid_credentials` | 1–N | `rfid_credentials.user_id`. |
| `doors` — `door_configs` | 1–1 | `door_configs.door_id` là FK có ràng buộc UNIQUE. |
| `doors` — `system_pins` | 1–1 | `system_pins.door_id` vừa là PK vừa là FK. |
| `doors` — `devices` | 1–N bản ghi; đúng 2 node hoạt động | `devices.door_id`; gồm một `NODE_1` và một `NODE_2` hoạt động, các node cũ được giữ để truy vết. |
| `doors` — `access_logs` | 1–N | `access_logs.door_id`. |
| `users` — `access_logs` | 1–N, tùy trường hợp | `access_logs.user_id`, cho phép NULL. |
| `rfid_credentials` — `access_logs` | 1–N, tùy trường hợp | `access_logs.rfid_credential_id`, cho phép NULL. |
| `devices` — `access_logs` | 1–N, tùy trường hợp | `access_logs.source_device_id`, cho phép NULL. |
| `doors` — `lockout_statuses` | 1–N | PK ghép `(door_id, method)`. |
| `doors` — `alerts` | 1–N | `alerts.door_id`. |

## 4. Chênh lệch cần xử lý

| Khu vực | Schema hiện tại | Mô hình BAD |
| --- | --- | --- |
| Phạm vi PIN | PIN toàn hệ thống, có nhiều bản ghi | Một PIN hiện hành cho cửa duy nhất, có phiên bản đồng bộ |
| Cấu hình cửa | Đã có FK UNIQUE tới cửa | Quan hệ 1–1 với cửa đã được triển khai |
| Thiết bị | Không biết thiết bị thuộc cửa nào và chưa giới hạn node hoạt động | Cửa duy nhất có một `NODE_1` xác thực và một `NODE_2` điều khiển/cảnh báo |
| Nhật ký | Không liên kết cửa/người/thẻ/thiết bị | Có các FK tùy theo nguồn sự kiện |
| Khóa tạm thời | Một trạng thái cho mỗi phương thức trên toàn hệ thống | Một trạng thái cho mỗi `(door, method)` |
| Cảnh báo | Không liên kết cửa, không có mức độ | Thuộc cửa duy nhất và có `severity` |
| Trạng thái người dùng | Có `ACTIVE` và `DISABLED` | Backend phải vô hiệu hóa toàn bộ thẻ và vân tay liên quan khi người dùng chuyển sang `DISABLED` |
| Hạn thẻ RFID | Trạng thái có `EXPIRED` nhưng không có ngày hết hạn | Có `expires_at` |

Các thay đổi ở mục này là đề xuất thiết kế. Cần cập nhật migration, mock data,
Backend entity/repository, API contract, firmware sync và kiểm thử cùng nhau khi
nhóm quyết định triển khai.

## 5. Quy tắc PIN và khóa tạm thời

Hệ thống dùng PIN chung cho cửa. Chính sách mặc định là khóa tạm sau `N = 3`
lần xác thực sai liên tiếp trong `T = 60` giây. Hai giá trị này được lưu trong
`door_configs.max_failed_attempts` và `door_configs.lockout_seconds`; người dùng
có quyền cấu hình có thể thay đổi chúng. Backend, firmware và giao diện phải đọc
cấu hình hiện hành thay vì mã hóa cứng giá trị mặc định.

Không được ghi PIN thuần vào `access_logs`, cảnh báo, tài liệu mẫu hoặc log ứng
dụng. Nhật ký chỉ lưu phương thức, kết quả và lý do tổng quát.

## 6. Nguồn tham chiếu

- [Schema MySQL hiện tại](../../infrastructure/mysql/init/init.sql)
- [Dữ liệu mẫu MySQL](../../infrastructure/mysql/init/mockdata.sql)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- Business Analysis Document Smart Access Control IoT, phiên bản 1.0.1,
  ngày 23/09/2026 (tệp người dùng cung cấp).
