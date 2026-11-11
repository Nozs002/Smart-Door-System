# HTML Mobile UI Mockups

Thư mục này chứa prototype HTML dùng làm tài liệu tham khảo khi triển khai giao
diện Android bằng Flutter. Các file HTML, CSS và JavaScript không được đóng gói
vào ứng dụng trong `mobile/` và không phải là một Flutter Web project.

> [!WARNING]
> Đây chưa phải bản thiết kế giao diện hoàn thiện hoặc cuối cùng. Bố cục, màu
> sắc, nội dung và luồng tương tác vẫn có thể thay đổi. Prototype hiện vẫn còn
> một số lỗi cần khắc phục trước khi được dùng làm cơ sở thiết kế chính thức.

## Màn hình

| File | Nội dung |
| --- | --- |
| `index.html` | Đăng nhập và điểm bắt đầu của prototype. |
| `profile.html` | Xem hồ sơ Account mẫu, mở từ avatar góc trái dashboard; có nút quay lại và liên kết tới cửa sở hữu. |
| `register.html` | Đăng ký Account: tên hiển thị, username, mật khẩu và xác nhận; chỉ mô phỏng. |
| `dashboard.html` | Tổng quan, chọn cửa thuộc sở hữu, quản lý truy cập và điều khiển cửa. |
| `users.html` | Mockup mô hình người dùng cũ; cần chuyển thành quản lý Cardholder. |
| `fingerprints.html` | Mockup cũ ngoài phạm vi; không triển khai vân tay. |
| `rfid.html` | Cấp phát và quản lý thẻ RFID. |
| `pin.html` | Thay đổi mã PIN và xem chính sách khóa tạm. |
| `alerts.html` | Cảnh báo và lịch sử hoạt động. |
| `settings.html` | Cài đặt ứng dụng và hệ thống. |

Tài nguyên dùng chung:

- `style.css`: theme, layout và component styles.
- `script.js`: chuyển theme, điều hướng và tương tác mô phỏng.
- `register.js`: kiểm tra form đăng ký, hiện/ẩn mật khẩu và trạng thái gửi; không lưu hoặc gửi mật khẩu.
- `house_bg.jpg`, `OIP.webp`: hình ảnh minh họa cục bộ.

## Xem prototype

Có thể mở `index.html` trực tiếp bằng trình duyệt. Để tránh khác biệt về quyền
truy cập file cục bộ, nên chạy một static web server từ thư mục này, ví dụ bằng
extension Live Server của Visual Studio Code.

Prototype sử dụng Google Fonts và ảnh avatar từ `i.pravatar.cc`, nên các tài
nguyên này cần kết nối Internet. Nội dung, trạng thái và thao tác hiện đều là dữ
liệu mô phỏng; prototype không kết nối Backend, MQTT hoặc thiết bị thật.

## Quy ước triển khai Flutter

- Dùng mockup để tham khảo bố cục, màu sắc và luồng tương tác; không nhúng HTML
  hoặc JavaScript vào ứng dụng Android.
- Tái tạo màn hình bằng widget Flutter trong feature tương ứng dưới `mobile/lib/`.
- Không sao chép dữ liệu mẫu, URL ảnh bên ngoài hoặc thông tin giả lập vào logic
  production nếu chưa được xác nhận.
- Mọi khác biệt giữa mockup và yêu cầu/contract hiện hành phải ưu tiên yêu cầu và
  contract của dự án.

## Vấn đề đã biết

- Theo [quyết định ngày 13/10/2026](../../01-requirements/ownership-device-lifecycle-decisions.md),
  cần tách Account đăng nhập và Cardholder, thêm danh sách cửa thuộc sở hữu cùng
  luồng claim bằng mã kích hoạt, hiển thị lifecycle và kết nối riêng từng Node.
- Nội dung `ADMIN/USER`, vân tay và thu hồi vân tay còn trong HTML/JavaScript
  là mô hình cũ. Chúng không phải yêu cầu triển khai; màn hình vân tay và các
  liên kết liên quan cần loại khỏi bản thiết kế giao diện tiếp theo.

- Font và avatar phụ thuộc tài nguyên bên ngoài nên có thể không hiển thị khi
  offline.

Dashboard có bộ chọn 3 cửa mẫu; tên cửa trên thẻ tổng quan và hộp điều khiển được đồng bộ khi chuyển cửa. Lệnh khóa/mở và bộ hẹn giờ tự khóa được mô phỏng riêng cho từng cửa trong phiên xem trang.

Mục **Cấu hình cửa** có công tắc nhanh bảo mật, báo cháy và cảnh báo cửa mở quá lâu. Mỗi cửa có cấu hình mẫu riêng trong sessionStorage của tab; trang settings nhận tham số door, lưu cấu hình đúng cửa và quay lại dashboard của cửa đó. Chế độ bảo mật được đồng bộ giữa công tắc và form cài đặt. Ngưỡng báo cháy và thời gian cửa mở hiển thị ngay dưới công tắc tương ứng, đồng bộ với settings theo từng cửa; không lặp lại ở danh sách bên dưới.

Trong settings, Báo cháy và Cảnh báo cửa mở quá lâu có hai công tắc độc lập, mặc định bật. Tắt công tắc sẽ khóa ô ngưỡng tương ứng và giữ giá trị để dùng lại khi bật. Trạng thái được lưu mẫu riêng theo cửa qua nút Lưu cấu hình; dashboard hiển thị Đã tắt cho cảnh báo tương ứng. Đây là tương tác prototype, chưa thay đổi contract hay hành vi thiết bị thật.
