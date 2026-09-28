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
| `dashboard.html` | Tổng quan trạng thái hệ thống và cửa. |
| `door.html` | Chi tiết cửa và thao tác mở khóa. |
| `access.html` | Quản lý vân tay, thẻ RFID và mã PIN. |
| `alerts.html` | Cảnh báo và lịch sử hoạt động. |
| `settings.html` | Cài đặt ứng dụng và hệ thống. |

Tài nguyên dùng chung:

- `style.css`: theme, layout và component styles.
- `script.js`: chuyển theme, điều hướng và tương tác mô phỏng.
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

- Nút quay lại trong `door.html` đang điều hướng tới `index.html` thay vì
  `dashboard.html`; cần xác nhận đây có phải luồng mong muốn hay không.
- Font và avatar phụ thuộc tài nguyên bên ngoài nên có thể không hiển thị khi
  offline.
