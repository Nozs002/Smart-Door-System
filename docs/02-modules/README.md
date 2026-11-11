---
title: Tài liệu module Smart Door System
type: moc
lang: vi
tags: [modules, features]
updated: 2026-09-23
---

# Tài liệu module

Thư mục này là điểm tập trung tài liệu theo từng module nghiệp vụ của Smart Door System. Mỗi module mô tả một lát cắt hoàn chỉnh qua các thành phần có liên quan như Mobile, Backend, Database, MQTT và Firmware.

Xem danh sách và trạng thái các module tại [Module Map](_MODULE-MAP.md).

## Cấu trúc một module

```text
02-modules/
└── NN-<ten-module>/
    ├── README.md
    └── diagrams/
        ├── <ten-so-do>.puml
        └── <ten-so-do>.svg
```

Trong đó:

- `NN-<ten-module>/`: số thứ tự và tên module theo dạng `kebab-case`.
- `README.md`: tài liệu chính của module.
- `diagrams/`: source PlantUML và SVG được sinh từ source khi module cần sơ đồ.

Không bắt buộc tạo `diagrams/` nếu module chưa có sơ đồ. Không tạo sẵn thư mục rỗng chỉ để giữ cấu trúc.

## Nội dung tối thiểu

Tài liệu `README.md` của mỗi module cần mô tả:

1. Mục tiêu và phạm vi.
2. Actor và quyền hạn.
3. Thành phần Mobile, Backend, Database, MQTT hoặc Firmware liên quan.
4. Luồng chính, luồng lỗi và ngoại lệ.
5. Quy tắc nghiệp vụ.
6. API, MQTT topic và dữ liệu liên quan.
7. Yêu cầu bảo mật.
8. Kịch bản kiểm thử.
9. Vấn đề chưa được quyết định.
10. Liên kết tới source hoặc tài liệu tham khảo.

Sử dụng [module template](../00-getting-started/templates/module-template.md) khi tạo tài liệu mới. Sơ đồ phải tuân theo [quy chuẩn PlantUML](../00-getting-started/03-plantuml-conventions.md).

## Quy tắc quản lý

- Mỗi module chỉ có một tài liệu chính để tránh nội dung trùng lặp.
- Nội dung phải phản ánh trạng thái thực tế của code và hợp đồng hệ thống.
- Nội dung chưa được triển khai phải ghi rõ `Đề xuất` hoặc `Chưa triển khai`.
- Không đưa mật khẩu, token, API key hoặc thông tin bí mật vào tài liệu.
- Khi thêm, đổi tên hoặc loại bỏ module, phải cập nhật `_MODULE-MAP.md`.
- Dùng Markdown link tương đối; không dùng wikilink tới file không tồn tại.

## Quy trình thêm module

1. Chọn số thứ tự tiếp theo trong [Module Map](_MODULE-MAP.md).
2. Tạo thư mục `NN-<ten-module>/`.
3. Sao chép module template thành `README.md`.
4. Điền nội dung đã được xác minh và thêm sơ đồ nếu cần.
5. Cập nhật trạng thái trong `_MODULE-MAP.md`.
6. Chạy `git diff --check` và tạo Pull Request vào `dev`.

## Tài liệu liên quan

- [Chỉ mục tài liệu](../00-INDEX.md)
- [Quy chuẩn quản lý tài liệu](../00-getting-started/00-docs-vault.md)
- [Bộ mẫu tài liệu](../00-getting-started/02-template-pack.md)
- [Yêu cầu hệ thống](../01-requirements/requirement.md)

