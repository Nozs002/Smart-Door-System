---
title: Mô tả chức năng đăng nhập Account
type: requirements
version: 0.1
lang: vi
tags: [identity, login, mobile, jwt]
updated: 2026-10-14
---

# Mô tả chức năng đăng nhập Account

## 1. Mục tiêu và trạng thái

Cho phép khách hàng xác thực bằng tài khoản để sử dụng các chức năng quản lý
cửa trên Mobile App. Chức năng thuộc phạm vi Người 1, module `identity`.

**Trạng thái: đã chốt contract Backend; Maven verify ngày 14/10/2026 thành công (25 test).**
Xem [contract đăng nhập v1](../../contracts/api/login.md) và
[triển khai Backend](../05-backend/account-login.md) cho các quyết định đã chốt;
Mobile và các chức năng ngoài đăng nhập vẫn là đề xuất. Các yêu cầu đã có trong
tài liệu dự án được nêu ở mục 2 và 7. Luồng chi tiết, giao diện và tiêu chí kiểm
thử dưới đây là đề xuất mô tả chức năng; các chính sách ở mục 9 cần được chốt
trước khi triển khai. Tài liệu này chưa thay thế REST API contract.

Backend hiện có entity `Account`, `AccountRepository.findByUsername`, BCrypt
và nền tảng Security stateless. Đã bổ sung controller/service đăng nhập và phát
hành/xác minh JWT HS256; access token mặc định 15 phút, không refresh token.

## 2. Phạm vi và tác nhân

- Tác nhân chính: khách hàng có Account, đăng nhập trên Mobile App.
- Thành phần tham gia: Mobile App, Backend và bảng `accounts` trong MySQL.
- Account đăng nhập tách biệt với Cardholder. Cardholder, RFID và PIN cửa không
  được dùng làm thông tin đăng nhập App.
- Account có thể sở hữu từ 0 đến nhiều cửa; không có cửa không phải lý do từ
  chối đăng nhập. Không có vai trò Admin nền tảng trong phạm vi hiện tại.
- Đăng nhập không gửi lệnh mở cửa và không tham gia MQTT/Firmware.

Đăng ký, đăng xuất, đổi/quên mật khẩu và quản lý thông tin tài khoản được mô
tả riêng. Tài liệu chỉ nêu liên hệ của chúng với hiệu lực phiên đăng nhập.

## 3. Đầu vào, điều kiện và kết quả

| Nội dung | Mô tả |
| --- | --- |
| Đầu vào | Tên đăng nhập (`username`) và mật khẩu của Account. |
| Điều kiện để thành công | Backend truy cập được dữ liệu; Account tồn tại, có trạng thái `ACTIVE` và mật khẩu khớp hash. |
| Kết quả thành công | App nhận thông tin xác thực theo contract được chốt và chuyển vào khu vực dành cho Account đã đăng nhập. |
| Kết quả thất bại | Không cấp token hoặc cho phép truy cập chức năng cần xác thực; App hiển thị lỗi phù hợp. |

Schema hiện tại giới hạn `username` ở 100 ký tự và lưu `password_hash`, không
lưu mật khẩu rõ. Đây là giới hạn dữ liệu hiện có, chưa xác định đầy đủ quy tắc
nhập liệu, chuẩn hóa username hoặc độ dài mật khẩu của API.

## 4. Luồng chính đề xuất

1. Khách hàng mở màn hình đăng nhập và nhập username, mật khẩu.
2. App kiểm tra trường bắt buộc và các giới hạn đã được contract quy định.
3. Khi khách hàng chọn **Đăng nhập**, App chuyển sang trạng thái đang xử lý và
   gửi thông tin qua HTTPS tới API đăng nhập.
4. Backend kiểm tra đầu vào tại biên, tra cứu Account và đối chiếu mật khẩu
   bằng `PasswordEncoder` BCrypt; không so sánh với mật khẩu lưu dạng rõ.
5. Backend chỉ cho phép Account `ACTIVE` đăng nhập. Account không tồn tại,
   mật khẩu sai hoặc Account `DISABLED` đều không được cấp token.
6. Nếu hợp lệ, Backend phát hành JWT theo chính sách đã được phê duyệt và trả
   kết quả theo cấu trúc phản hồi chung của REST API.
7. App tiếp nhận token theo chính sách lưu trữ được chốt, xóa mật khẩu khỏi
   trạng thái form và chuyển tới khu vực sau đăng nhập. Account chưa có cửa
   vẫn được vào App; vị trí điều hướng cụ thể cần thống nhất với luồng cửa.
8. Các request tiếp theo dùng token; Backend xác minh token và kiểm tra quyền
   nghiệp vụ cho từng thao tác.

## 5. Luồng lỗi và ngoại lệ đề xuất

| Tình huống | Hành vi mong đợi |
| --- | --- |
| Thiếu username hoặc mật khẩu | App báo tại trường nhập; Backend vẫn validate nếu request được gửi trực tiếp. |
| Đầu vào vi phạm contract | Backend trả lỗi validation theo cấu trúc chung, không phản chiếu mật khẩu. |
| Username không tồn tại, mật khẩu sai hoặc Account bị vô hiệu hóa | Hiển thị thông báo chung, ví dụ “Không thể đăng nhập với thông tin đã cung cấp”; không tiết lộ Account có tồn tại hoặc trạng thái của Account. |
| Mất mạng hoặc timeout | App báo không kết nối được, kết thúc trạng thái đang xử lý và cho phép thử lại; không coi là sai mật khẩu. |
| Backend/database gặp lỗi | Trả lỗi hệ thống an toàn; không cấp token, không trả chi tiết SQL hoặc stack trace. |
| Vượt giới hạn thử đăng nhập | Xử lý theo chính sách chống dò mật khẩu ở mục 9; chưa áp dụng một ngưỡng cụ thể. |
| Token hết hạn hoặc không hợp lệ khi gọi API | Backend từ chối xác thực; App xử lý theo chính sách refresh hoặc yêu cầu đăng nhập lại được chốt. |

HTTP status, endpoint, DTO và mã lỗi đăng nhập cụ thể phải được thống nhất
trong `contracts/api/` trước triển khai. Response thành công dùng
`ApiResponse<T>`; lỗi dùng Problem Details, không trả HTTP `200` cho lỗi.

## 6. Màn hình Mobile đề xuất

- Có trường tên đăng nhập, trường mật khẩu được che và nút **Đăng nhập**.
- Có thể bật/tắt hiển thị mật khẩu; không điền sẵn thông tin tài khoản demo.
- Khi gửi request, hiển thị tiến trình và ngăn gửi lặp từ nút đăng nhập.
- Khi có lỗi, cho phép sửa dữ liệu hoặc thử lại và đưa nút về trạng thái sử dụng.
- Không hiển thị hoặc ghi log token/mật khẩu; không lưu mật khẩu để tự đăng nhập.
- Mockup HTML chỉ là tham khảo giao diện, không xác định contract hay policy.

Chức năng ghi nhớ phiên, tự đăng nhập khi mở App, liên kết đăng ký/quên mật
khẩu và màn hình đích sau đăng nhập cần được review riêng trước triển khai.

## 7. Ràng buộc bảo mật đã có

- Mật khẩu được băm bằng BCrypt; không lưu, log hoặc trả mật khẩu/hash ra API.
- Không log JWT, Authorization header hoặc payload chứa dữ liệu xác thực.
- Sử dụng HTTPS khi triển khai ngoài môi trường local.
- Chỉ public endpoint đăng nhập sau khi contract được phê duyệt; các API khác
  tiếp tục được bảo vệ theo mặc định.
- Xác minh JWT gồm chữ ký, issuer, audience, thời hạn và trạng thái Account.
- Backend lấy Account hiện tại từ ngữ cảnh xác thực; không tin Account ID do
  request body gửi để xác định quyền.
- Token hợp lệ không thay thế kiểm tra quyền sở hữu cửa. Đăng nhập không tự
  cấp quyền tới cửa của Account khác.
- Cần có biện pháp chống dò mật khẩu; ngưỡng và cơ chế thực thi chưa chốt.

## 8. Tiêu chí nghiệm thu đề xuất

| Mã | Kịch bản và kết quả cần kiểm chứng |
| --- | --- |
| LOGIN-01 | Account `ACTIVE` nhập đúng thông tin: nhận token hợp lệ và vào App. |
| LOGIN-02 | Account `ACTIVE` chưa sở hữu cửa: đăng nhập thành công. |
| LOGIN-03 | Sai mật khẩu, username không tồn tại hoặc Account `DISABLED`: không cấp token và cùng thông báo xác thực chung. |
| LOGIN-04 | Thiếu hoặc sai định dạng đầu vào: bị từ chối ở Backend dù bỏ qua kiểm tra của App. |
| LOGIN-05 | Mất mạng, timeout hoặc lỗi server: App thoát trạng thái đang xử lý, không vào khu vực đã xác thực. |
| LOGIN-06 | Nhấn đăng nhập nhiều lần khi đang xử lý: App không gửi request lặp từ nút. |
| LOGIN-07 | JWT sai chữ ký, sai issuer/audience hoặc hết hạn: API được bảo vệ từ chối request. |
| LOGIN-08 | Account bị vô hiệu hóa sau đăng nhập: không tiếp tục thực hiện nghiệp vụ được bảo vệ. |
| LOGIN-09 | Token của Account A không cho phép đọc/ghi cửa của Account B. |
| LOGIN-10 | Response và log không chứa mật khẩu, hash, token hoặc chi tiết nội bộ ngoài token được cấp trong response đăng nhập theo contract. |

Sau khi chốt mục 9, bổ sung kịch bản cho refresh/revoke, đăng xuất, đổi mật
khẩu, nhiều thiết bị và giới hạn thử đăng nhập. Các kịch bản trên là yêu cầu
kiểm thử tương lai, chưa phải bằng chứng test đã chạy.

## 9. Theo dõi quyết định

Các mục username, mật khẩu, JWT, token và rate limit cho Backend lần này đã
chốt tại contract v1; bảng dưới là danh sách chủ đề theo dõi. Refresh, logout,
đổi mật khẩu, Mobile và audit persistence vẫn chưa triển khai/chốt đầy đủ.

| Chủ đề | Nội dung cần quyết định |
| --- | --- |
| Username | Phân biệt hoa/thường, xử lý khoảng trắng, ký tự cho phép và tính nhất quán với đăng ký/database. |
| Mật khẩu đầu vào | Giới hạn độ dài và cách xử lý Unicode/khoảng trắng nhất quán với đăng ký, BCrypt và đổi mật khẩu. |
| JWT | Thuật toán ký, quản lý/rotation khóa, claims, TTL và sai lệch đồng hồ cho phép. |
| Phiên/token | Có refresh token hay không; thời hạn phiên, rotation/revoke và hành vi khi access token hết hạn. |
| Đăng xuất và đổi mật khẩu | Thu hồi phiên hiện tại hay toàn bộ phiên; hiệu lực của token đã phát hành. |
| Nhiều thiết bị | Cho phép bao nhiêu phiên đồng thời và cách quản lý phiên. |
| Chống dò mật khẩu | Giới hạn theo Account/IP hoặc cơ chế khác, ngưỡng, thời gian chờ và thông báo lỗi. Không dùng mặc định lockout RFID/PIN của cửa cho đăng nhập App. |
| Mobile | Nơi lưu token an toàn, khôi phục phiên khi mở App và điều hướng sau đăng nhập. |
| API và audit | Endpoint/DTO/error code, dữ liệu audit được phép lưu và thời hạn lưu. |

## 10. Tài liệu và source liên quan

- [Yêu cầu hệ thống](../01-requirements/requirement.md)
- [Quyết định sở hữu cửa và vòng đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md)
- [Kiến trúc Backend](../05-backend/architecture.md)
- [Nền tảng bảo mật Backend](../05-backend/security-foundation.md)
- [Cấu trúc phản hồi REST API](../05-backend/api-response.md)
- [REST API contract](../../contracts/api/README.md)
- [Account entity](../../backend/src/main/java/com/smartdoor/backend/identity/entity/Account.java)
- [Account repository](../../backend/src/main/java/com/smartdoor/backend/identity/repository/AccountRepository.java)
- [Security configuration](../../backend/src/main/java/com/smartdoor/backend/configuration/SecurityConfiguration.java)
- [Mockup Mobile](../06-frontend/mockups/README.md)
