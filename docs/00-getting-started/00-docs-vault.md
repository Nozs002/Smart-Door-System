---
title: Docs & Process Standards — Luật nền (project-agnostic)
type: sop
version: 1.0
lang: vi
tags: [standards, conventions, process]
updated: 2026-06-19
---

# 📐 Nguyên tắc quản lý tài liệu

> Bộ quy ước **dùng cho dự án**. Người mới đọc file này để hiểu cấu trúc tổ chức tài liệu của dự án.

## 1. Cấu trúc vault

Cây chuẩn — tài liệu xuyên suốt đánh số `00 → NN`, tài liệu tính năng đi theo **module**:

```
docs/
├── 00-INDEX.md                 # điểm vào DUY NHẤT, mọi thứ link từ đây
├── 00-getting-started/         # exec summary, quick ref, overview, foundation/
├── 01-requirements/            # yêu cầu khách/CEO, functional req
├── 02-modules/                 # ⭐ trục chính: mỗi module 1 folder NN-name/
│   ├── _MODULE-MAP.md          #   bản đồ module
│   ├── _registry/              #   module-registry (tên wikilink chuẩn)
│   ├── _appendix/              #   open-questions, ba-crossref…
│   └── NN-<module>/            #   README + VI/ + EN/ + screens/
├── 03-technical-reference/     # API endpoints, data model, tech ref
├── 04-srs-specs/               # SRS
├── 05-backend-audit/  06-frontend-audit/  07-security-planning/  08-scalability/
├── 09-process/                 # SOP đánh số 00..NN + templates/ + _packages/ + _standards/ + en/
├── 10-architecture-future/     # kiến trúc tương lai/refactor
├── 11-meetings/                # _templates/ _registry/ change-requests/ clients/ 2026/MM/
└── _learning/                  # ghi chú học/handoff
```

**Quy tắc:**
- Prefix số 2 chữ số theo **thứ tự đọc**; file phụ trợ dùng prefix `_` (vd `_MODULE-MAP`, `_templates`).
- Một chủ đề = một note ngắn (≤ ~300 dòng). Note dài → tách + link.

## 2. Đặt tên
- Folder/file: `NN-kebab-case` (vd `04-logistics-director`). Slug không dấu, ngắn (≤6 từ).
- Meeting: `2026/MM/YYYY-MM-DD-<client>-<chu-de>.md`. CR: `CR-NNNN-<slug>.md`. Work package: `_packages/2026/MM/YYYY-MM-DD-<slug>.md`.

## 3. Song ngữ (bilingual)
- Mặc định VI; bản EN là `<tên>.en.md` hoặc vault `EN/` gương y hệt VI (**tên file giống nhau**).
- Liên kết chéo: bản VI có `🇬🇧 [[...en|EN]]`, bản EN có `🇻🇳 [[...|VI]]`.
- ⚠️ **Wikilink chỉ trỏ trong cùng bản** (EN↔EN, VI↔VI). EN↔VI **chỉ** nối ở Home/đầu file bằng link tương đối → đổi/ tạo bản dịch không vỡ wikilink.

## 4. Liên kết & điều hướng (Obsidian/LLM-wiki)
- Dùng `[[wikilink]]` theo tên chuẩn trong registry. **Không note mồ côi**; mỗi note có breadcrumb 🏠 + Related.
- Mỗi khu vực có **MOC/Home** (mục lục). Mọi thứ truy được từ `00-INDEX`.
- **Audit link** sau khi viết (script trong skill) — mọi `[[...]]` phải resolve.

## 5. Độ tin & truy vết (provenance)
- **Frontmatter `type` (OKF):** mọi note có `type` → trở thành node query được bởi LLM (xem [[04-okf-module-content-standard]]). Bảng `type` chuẩn:

  | Nhóm | `type` |
  |---|---|
  | Xuyên suốt | `sop` · `moc` · `reference` · `registry` · `standards` |
  | Module | `module-home` · `screen` · `data-lineage` · `diagram` · `entity` · `api-reference` · `formula` · `rbac` · `handoff` |
  | Họp/việc | `meeting` · `change-request` · `work-package` · `daily-log` · `client` |

- **Verified / provenance:** công thức/endpoint/quy tắc phải trích nguồn `file:line` (FE+BE) hoặc URL. Frontmatter `verified: true|false`; chưa verify → `verified: false` + banner ⚠️ đầu file. Inline gắn cờ **✅ verified-in-code** / **❌ not-in-code (UI-only)**; chỗ chưa rõ ghi "chưa biết công thức", **không bịa**.
- **Truy vết 2 chiều**: yêu cầu nghiệp vụ ↔ Change Request ↔ Jira ↔ commit/PR. Mỗi CR ghi `source_meeting` + `modules` + `jira`.
- **Ánh xạ trạng thái CR ↔ Jira:** `requested`→(chưa có Jira) · `approved`→To Do · `in-progress`→In Progress · `testing`→In Review · `done`→Done · ngoài luồng: `rejected`/`deferred`.

## 6. Nguyên tắc "đóng gói mọi việc"
- Mọi việc lớn xong → **đóng gói** thành work package 10 mục (skill `package-work`, [[14-work-packaging-sop]]).
- Quy trình tái dùng nhiều lần → **nâng thành SOP đánh số** trong `09-process`.
- Task giao dev/Jira → theo [[02-template-pack|template task chuẩn]] (Current/Expected + use case + link doc + ảnh).

## 7. Where-to-put (để tài liệu ở đâu)
- Tính năng 1 module → `02-modules/NN-<module>/`.
- Quy trình/SOP → `09-process/`. Biên bản/CR → `11-meetings/`. Kiến trúc tương lai → `10-architecture-future/`.
- Chi tiết: [[00-where-to-put-docs]].

## Related
- [[01-skills-catalog]] · [[02-template-pack]] · [[03-new-project-scaffold]] · [[04-okf-module-content-standard]] · [[05-skill-authoring-standard]] · [[06-mermaid-conventions]] · [[14-work-packaging-sop]]
