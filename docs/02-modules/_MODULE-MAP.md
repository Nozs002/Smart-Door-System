---
title: Module Map — Smart Door System
type: moc
lang: vi
tags: [modules, roadmap]
updated: 2026-09-23
---

# Module Map

Bảng này định nghĩa các module nghiệp vụ cốt lõi và phạm vi kỹ thuật chính của Smart Door System. Trạng thái tài liệu không đại diện cho trạng thái hoàn thành source code.

## Danh sách module

| # | Module | Phạm vi chính | Thành phần liên quan | Trạng thái tài liệu |
| --- | --- | --- | --- | --- |
| 01 | Authentication & Authorization | Đăng nhập, JWT, vai trò Admin/User và kiểm soát quyền truy cập. | Mobile, Backend, MySQL | Chưa tạo |
| 02 | Device Management | Onboarding, cấu hình và quản lý thiết bị cửa. | Mobile, Backend, MySQL | Chưa tạo |
| 03 | Device Lifecycle | Heartbeat, trạng thái online/offline và khả năng reconnect. | Backend, MQTT, ESP32 | Chưa tạo |
| 04 | Local Access Authentication | Xác thực RFID/PIN và xử lý khi mất kết nối mạng. | ESP32 Auth, Backend, MQTT, MySQL | Chưa tạo |
| 05 | Door Control | Mở/đóng khóa tại chỗ hoặc từ xa và theo dõi trạng thái cửa. | Mobile, Backend, MQTT, ESP32 Control | Chưa tạo |
| 06 | Access Logs | Ghi nhận và truy vấn lịch sử ra vào. | Mobile, Backend, MySQL | Chưa tạo |
| 07 | Alerts & Security Rules | Sai thông tin xác thực nhiều lần, cửa mở lâu và cảnh báo xâm nhập. | Mobile, Backend, MQTT, ESP32 | Chưa tạo |
| 08 | Fire Safety | Phát hiện nhiệt độ cao, kích hoạt còi và tự động mở cửa thoát hiểm. | ESP32 Control, MQTT, Backend, Mobile | Chưa tạo |

## Tên thư mục chuẩn

Khi bắt đầu viết tài liệu cho một module, sử dụng đúng tên sau:

```text
01-authentication-authorization/
02-device-management/
03-device-lifecycle/
04-local-access-authentication/
05-door-control/
06-access-logs/
07-alerts-security-rules/
08-fire-safety/
```

Không tạo thư mục module chỉ để giữ chỗ. Chỉ tạo khi bắt đầu viết tài liệu và phải có ít nhất `README.md`.

## Trạng thái tài liệu

Sử dụng một trong các giá trị:

- `Chưa tạo`: chưa có tài liệu module.
- `Đang soạn`: tài liệu đang được xây dựng hoặc chưa được review.
- `Đã review`: nội dung đã được một thành viên khác kiểm tra.
- `Cần cập nhật`: tài liệu không còn đồng bộ với source code hoặc yêu cầu.

## Quy tắc cập nhật

- Thêm module mới ở cuối danh sách và sử dụng số thứ tự tiếp theo.
- Chỉ tách module khi có phạm vi nghiệp vụ và trách nhiệm rõ ràng.
- Nếu đổi tên module, cập nhật đồng thời tên thư mục, bảng này và mọi liên kết liên quan.
- Khi tài liệu module được tạo hoặc review, cập nhật trạng thái trong cùng Pull Request.
- Các thay đổi phạm vi lớn phải đối chiếu với [yêu cầu hệ thống](../01-requirements/requirement.md).

## Tài liệu liên quan

- [Hướng dẫn quản lý module](README.md)
- [Module template](../00-getting-started/templates/module-template.md)
- [Quy chuẩn PlantUML](../00-getting-started/03-plantuml-conventions.md)
- [Kiến trúc hệ thống](../03-technical-reference/architecture.md)
