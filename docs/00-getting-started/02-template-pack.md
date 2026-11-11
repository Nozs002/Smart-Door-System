---
title: Bộ mẫu tài liệu Smart Door System
type: reference
version: 1.1
lang: vi
tags: [standards, templates, documentation]
updated: 2026-09-23
---

# Bộ mẫu tài liệu

Các template chuẩn được lưu trong thư mục [`templates/`](templates/). Khi sử dụng, sao chép template tới khu vực tài liệu đích, đổi tên file theo `kebab-case` và thay toàn bộ nội dung placeholder.

## Danh sách template

| Template | Dùng cho | Vị trí tài liệu sau khi tạo |
| --- | --- | --- |
| [Module](templates/module-template.md) | Phạm vi, luồng, quy tắc và tích hợp của một module. | `02-modules/<ten-module>/README.md` |
| [API](templates/api-template.md) | Hợp đồng endpoint, request, response và mã lỗi. | `05-backend/` hoặc module phụ trách |
| [Biên bản họp](templates/meeting-note-template.md) | Nội dung họp, quyết định và công việc tiếp theo. | `10-meetings/YYYY-MM-DD-<chu-de>.md` |
| [Quyết định kỹ thuật](templates/technical-decision-template.md) | So sánh phương án và ghi nhận quyết định kiến trúc. | Khu vực phụ trách hoặc `09-architecture-future/` |
| [Pull Request](templates/pull-request-template.md) | Mô tả thay đổi, kiểm thử và checklist review. | Nội dung Pull Request trên GitHub |

## Cách sử dụng

1. Chọn template phù hợp trong bảng trên.
2. Sao chép file tới thư mục đích; không chỉnh trực tiếp bản template cho nội dung của một task cụ thể.
3. Đổi tên file rõ nghĩa theo dạng `kebab-case`.
4. Thay các placeholder như `<Tên module>` và `YYYY-MM-DD`.
5. Xóa các dòng hướng dẫn hoặc mục không áp dụng.
6. Thêm liên kết từ `00-INDEX.md`, README khu vực hoặc tài liệu module liên quan.
7. Chạy `git diff --check` trước khi commit.

## Quy tắc duy trì template

- Template chỉ chứa cấu trúc dùng chung, không chứa dữ liệu thật của một tính năng.
- Không đưa mật khẩu, token, API key hoặc thông tin bí mật vào template.
- Thay đổi cấu trúc template phải được thực hiện qua Pull Request và có review.
- Khi thêm, đổi tên hoặc xóa template, phải cập nhật danh sách trong tài liệu này.
- Ưu tiên Markdown link tương đối để liên kết hoạt động trên GitHub.

## Tài liệu liên quan

- [Quy chuẩn quản lý tài liệu](00-docs-vault.md)
- [Hướng dẫn bắt đầu phát triển](01-getting-started.md)
- [Chỉ mục tài liệu](../00-INDEX.md)
- [Quy định đóng góp](../../CONTRIBUTING.md)
