---
title: Quy chuẩn quản lý tài liệu Smart Door System
type: standards
version: 1.1
lang: vi
tags: [standards, documentation, process]
updated: 2026-09-23
---

# Quy chuẩn quản lý tài liệu

Tài liệu này quy định cách tổ chức, đặt tên, liên kết và duy trì tài liệu trong dự án Smart Door System. Thành viên mới nên đọc tài liệu này trước khi tạo hoặc chỉnh sửa nội dung trong `docs/`.

## 1. Cấu trúc thư mục

```text
docs/
├── 00-INDEX.md
├── 00-getting-started/
│   ├── README.md
│   ├── 00-docs-vault.md
│   ├── 01-getting-started.md
│   ├── 02-template-pack.md
│   ├── 03-plantuml-conventions.md
│   └── templates/
├── 01-requirements/
├── 02-modules/
├── 03-technical-reference/
├── 04-srs-specs/
├── 05-backend/
├── 06-frontend/
├── 07-security-planning/
├── 08-process/
├── 09-architecture-future/
└── 10-meetings/
```

`00-INDEX.md` là điểm vào chính của kho tài liệu. Khi thêm một tài liệu quan trọng, phải bổ sung liên kết tới tài liệu đó trong chỉ mục hoặc README của khu vực tương ứng.

## 2. Mục đích của từng khu vực

| Thư mục | Nội dung |
| --- | --- |
| `00-getting-started/` | Hướng dẫn thiết lập môi trường, onboarding và quy chuẩn tài liệu. |
| `01-requirements/` | Yêu cầu nghiệp vụ, yêu cầu chức năng và phạm vi hệ thống. |
| `02-modules/` | Tài liệu theo từng module hoặc nhóm tính năng. |
| `03-technical-reference/` | Kiến trúc hiện tại, database, MQTT, phần cứng và tài liệu tham khảo kỹ thuật. |
| `04-srs-specs/` | Đặc tả yêu cầu phần mềm chính thức. |
| `05-backend/` | Thiết kế, API, dữ liệu và hướng dẫn phát triển Backend. |
| `06-frontend/` | Thiết kế và hướng dẫn phát triển Mobile/Frontend. |
| `07-security-planning/` | Kế hoạch bảo mật, threat model và đánh giá rủi ro. |
| `08-process/` | Quy trình phát triển, CI/CD, release và các SOP của nhóm. |
| `09-architecture-future/` | Đề xuất refactor và kiến trúc trong tương lai. |
| `10-meetings/` | Biên bản họp, quyết định và change request. |

## 3. Quy tắc đặt tên

- Tên thư mục và file sử dụng chữ thường theo dạng `kebab-case`.
- Không dùng dấu tiếng Việt, khoảng trắng hoặc ký tự đặc biệt trong tên file.
- Prefix hai chữ số thể hiện thứ tự đọc hoặc nhóm nội dung, ví dụ `00-`, `01-`, `02-`.
- Tên phải ngắn gọn nhưng mô tả đúng nội dung.
- Biên bản họp sử dụng định dạng `YYYY-MM-DD-<chu-de>.md`.
- Không dùng các tên chung chung như `note.md`, `new.md`, `temp.md` hoặc `final-final.md`.

Ví dụ:

```text
device-lifecycle.md
mqtt-authentication-flow.md
2026-09-23-sprint-planning.md
```

## 4. Nội dung tài liệu

- Mỗi tài liệu chỉ nên tập trung vào một chủ đề chính.
- Nội dung phải phản ánh trạng thái thực tế của source code và hệ thống.
- Không suy đoán API, cấu trúc database hoặc hành vi hệ thống. Nội dung chưa được xác minh phải được đánh dấu rõ là đề xuất hoặc cần xác minh.
- Sơ đồ, payload, endpoint và cấu hình phải có ví dụ khi điều đó giúp người đọc kiểm chứng nội dung.
- Tài liệu dài nên được chia thành các phần hoặc file nhỏ và liên kết qua chỉ mục.
- Không đưa mật khẩu, token, API key, private key hoặc thông tin bí mật vào tài liệu.

## 5. Liên kết và điều hướng

- Ưu tiên Markdown link tương đối để liên kết hoạt động trên GitHub.
- Không để tài liệu quan trọng ở trạng thái không có liên kết từ `00-INDEX.md` hoặc README khu vực.
- Khi di chuyển hoặc đổi tên file, phải cập nhật tất cả liên kết tham chiếu.
- Trước khi commit, kiểm tra repository không còn đường dẫn cũ hoặc link hỏng.

Ví dụ:

```markdown
[Hướng dẫn bắt đầu](01-getting-started.md)
[Quy trình CI/CD](../08-process/ci-cd.md)
```

## 6. Frontmatter

Tài liệu có nhu cầu phân loại hoặc sử dụng trong công cụ quản lý kiến thức nên có frontmatter:

```yaml
---
title: Tên tài liệu
type: reference
version: 1.0
lang: vi
tags: [backend, mqtt]
updated: YYYY-MM-DD
---
```

Các giá trị `type` khuyến nghị:

- `moc`: mục lục hoặc trang điều hướng.
- `standards`: quy chuẩn của dự án.
- `reference`: tài liệu tham khảo kỹ thuật.
- `requirements`: yêu cầu hệ thống.
- `module`: tài liệu module hoặc tính năng.
- `sop`: quy trình thực hiện công việc.
- `meeting`: biên bản họp.
- `decision`: quyết định kỹ thuật hoặc nghiệp vụ.

Không bắt buộc thêm frontmatter vào file chỉ dùng làm placeholder.

## 7. Chọn vị trí lưu tài liệu

- Hướng dẫn cho thành viên mới → `00-getting-started/`.
- Yêu cầu nghiệp vụ → `01-requirements/`.
- Tính năng thuộc một module → `02-modules/<ten-module>/`.
- Database, MQTT, phần cứng hoặc tham khảo kỹ thuật dùng chung → `03-technical-reference/`.
- Đặc tả SRS → `04-srs-specs/`.
- Nội dung riêng của Backend → `05-backend/`.
- Nội dung riêng của Mobile/Frontend → `06-frontend/`.
- Bảo mật và threat modeling → `07-security-planning/`.
- Quy trình, CI/CD và release → `08-process/`.
- Kiến trúc đề xuất chưa áp dụng → `09-architecture-future/`.
- Biên bản họp và quyết định nhóm → `10-meetings/`.

Nếu một tài liệu phù hợp với nhiều khu vực, lưu tại khu vực chịu trách nhiệm chính và tạo liên kết từ các khu vực liên quan; không sao chép thành nhiều bản độc lập.

## 8. Quy trình cập nhật tài liệu

1. Cập nhật nhánh `dev` và tạo nhánh công việc riêng.
2. Chỉnh sửa hoặc thêm tài liệu vào đúng thư mục.
3. Cập nhật `00-INDEX.md` hoặc README của khu vực nếu cần.
4. Kiểm tra link, đường dẫn và thông tin bí mật.
5. Chạy `git diff --check`.
6. Commit với tiền tố `docs`, ví dụ:

   ```text
   docs: update backend setup guide
   ```

7. Push nhánh và tạo Pull Request vào `dev`.

## 9. Tài liệu liên quan

- [Chỉ mục tài liệu](../00-INDEX.md)
- [Hướng dẫn bắt đầu phát triển](01-getting-started.md)
- [Bộ mẫu tài liệu](02-template-pack.md)
- [Quy định đóng góp](../../CONTRIBUTING.md)
- [Quy trình CI/CD](../08-process/ci-cd.md)
