---
title: Quyết định sở hữu cửa và vòng đời thiết bị
type: requirements
lang: vi
tags: [scope, ownership, device-lifecycle]
updated: 2026-10-13
---

# Quyết định sở hữu cửa và vòng đời thiết bị

Năm quyết định dưới đây đã được thống nhất ngày 13/10/2026 và là cơ sở cập
nhật yêu cầu, kiến trúc, mô hình dữ liệu và contract. Đây là yêu cầu mục tiêu;
schema bootstrap, API và firmware chưa được xem là đã triển khai chỉ vì tài
liệu được cập nhật.

## 1. Account và quyền sở hữu Door

- Database phục vụ nhiều khách hàng, mỗi khách hàng có một Account đăng nhập App.
- Một Account sở hữu từ 0 đến nhiều Door. Mỗi Door đã liên kết có đúng một chủ,
  xác định bằng `owner_account_id`; Door chưa claim chưa có chủ.
- Chủ cửa được quản lý thiết bị, cấu hình, PIN, RFID, lệnh điều khiển, lịch sử
  và cảnh báo của các cửa mình sở hữu. Backend kiểm tra sở hữu ở mọi use case.
- Phạm vi hiện tại không có Account Admin nền tảng, dashboard quản trị nền
  tảng, chia sẻ cửa giữa Account hoặc chuyển quyền sở hữu.

## 2. Account và Cardholder

- Account chứa thông tin đăng nhập; Cardholder là người được cấp RFID, thuộc
  danh sách của một Account và không đăng nhập App trong phạm vi hiện tại.
- RFIDCredential liên kết một Cardholder với một Door. Backend kiểm tra
  Cardholder thuộc cùng Account sở hữu Door trước khi cấp hoặc sửa thẻ.
- Một UID có thể đăng ký tại nhiều cửa; tính duy nhất và thu hồi áp dụng theo
  từng cửa. PIN chung cũng thuộc từng cửa.
- Chủ cửa muốn sử dụng RFID có thể tạo Cardholder cho chính mình; hồ sơ này
  vẫn tách khỏi Account.
- Vân tay nằm ngoài phạm vi: không thiết kế cảm biến, bảng, API, MQTT hoặc
  màn hình vân tay. Mockup cũ có vân tay không phải yêu cầu triển khai.

## 3. Provision và claim bộ cửa

1. Quy trình kỹ thuật provision Door và một cặp Node 1/Node 2, cấp danh tính
   thiết bị và mã kích hoạt cho bộ cửa qua công cụ nội bộ/CLI.
2. Door chưa có chủ; khách hàng đăng nhập, chọn thêm cửa và gửi mã kích hoạt.
3. Backend xác minh mã và đúng cặp Node, kiểm tra cửa chưa có chủ, rồi gán
   Account trong một transaction. Hai Account cùng claim chỉ một bên thành công.
4. Retry của cùng Account phải trả kết quả nhất quán; không tạo Door mới hoặc
   đổi chủ. Claim không tự gửi lệnh mở khóa.
5. Việc đưa vào vận hành phải kiểm tra danh tính thiết bị và việc thiết lập
   cấu hình/PIN; claim thành công không đồng nghĩa thiết bị đã sẵn sàng.

Mã kích hoạt là bí mật dùng một lần, có thời hạn; lưu giá trị hash và dấu vết
sử dụng. `device_code` là định danh công khai, không phải bằng chứng sở hữu.
Provision là quy trình kỹ thuật, không bổ sung vai trò Admin nền tảng vào App.
Thay ESP32 thực hiện trên Door hiện có và giữ lịch sử thiết bị cũ.

## 4. Vòng đời và kết nối thiết bị

Backend quản lý đăng ký, gán cửa, kích hoạt, vô hiệu hóa, thay thế và ngừng sử
dụng thiết bị; lưu lịch sử chuyển trạng thái, người/quy trình thực hiện, lý do
và thời điểm. Không xóa thiết bị đã có lịch sử nghiệp vụ.

Trạng thái vòng đời thể hiện quyền sử dụng; online/offline thể hiện kết nối.
Mất heartbeat không tự vô hiệu hóa hoặc retire thiết bị. Backend suy ra kết
nối từ thời điểm nhận heartbeat và ngưỡng quá hạn; chưa nhận heartbeat là
`UNKNOWN`. Mỗi Node có trạng thái riêng, không suy ra Node còn lại offline.

Thiết bị bị vô hiệu hóa/ngừng sử dụng không được xử lý nghiệp vụ chỉ vì còn
online. Schema v2 dùng lifecycle `PROVISIONED/ACTIVE/DISABLED/RETIRED`, với
PROVISIONED là Node đã gán bộ cửa nhưng chưa vận hành; xem [đặc tả database](../03-technical-reference/database-erd.md)
cho transition và lịch sử. Timeout heartbeat và workflow thực thi còn phải
đặc tả cùng firmware; schema mới chưa đồng nghĩa Backend đã hiện thực use case.

## 5. Phạm vi dữ liệu, quyền và định tuyến

- Cấu hình, PIN, RFID, bộ đếm sai, thiết bị, log và cảnh báo phải xác định Door.
  Account và danh sách Cardholder có phạm vi khách hàng; không trộn hai phạm vi.
- Backend lấy Account từ phiên xác thực, kiểm tra sở hữu trước khi đọc/ghi dữ
  liệu hoặc gửi lệnh; không tin Account do request body cung cấp.
- Danh tính MQTT của thiết bị phải khớp liên kết Door/Node trong Backend và
  ACL của Broker. Không tin riêng `door_id` trong payload.
- Lệnh chỉ tới Node 2 của cửa đích; dữ liệu và thông báo chỉ tới đúng chủ cửa.
- Mỗi cửa có tối đa một Node hoạt động cho mỗi vai trò; bản ghi thiết bị cũ
  được giữ để truy vết. Thay thiết bị phải kiểm tra đồng thời và thu hồi quyền
  của thiết bị cũ.
- Demo phần cứng có một cửa với hai ESP32. Kiểm chứng nhiều cửa dùng thiết bị
  mô phỏng có định danh riêng, không hardcode Account hoặc Door ID.

## Nội dung còn phải đặc tả

Cần hiện thực transition lifecycle, chốt cách provision credential/MQTT ACL, chính
sách mã kích hoạt, timeout heartbeat, đồng bộ cấu hình và phản hồi áp dụng,
kênh offline giữa hai Node, migration và thời hạn giữ lịch sử. Quyết định bỏ
khoảng cách/AUTOMATIC hoặc thay đổi cảm biến chưa nằm trong năm điểm này.

## Tài liệu liên quan

- [Yêu cầu hệ thống](requirement.md)
- [Database ERD](../03-technical-reference/database-erd.md)
- [Kiến trúc Backend](../05-backend/architecture.md)
- [Kế hoạch triển khai Backend](../05-backend/implementation-plan.md)
