---
title: Bộ chuẩn tài liệu & quy trình
type: moc
lang: vi
tags: [standards, kit, onboarding, process]
updated: 2026-06-22
---

# 🧱 Tài liệu hướng dẫn cho người mới (Smart Door System)

> Điểm vào cho **người mới**. Dự án được tổ chức: cấu trúc folder đánh số, quy ước đặt tên, song ngữ, wikilink/MOC, và nguyên tắc **mọi việc đều đóng gói + có SOP**. Mục tiêu: đỡ tốn thời gian training, dễ scale, áp dụng lại nhanh.

## Đọc theo thứ tự (người mới)
1. [[00-docs-process-standards]] — **luật nền**: cây folder, đặt tên, song ngữ, wikilink, where-to-put, "đóng gói mọi việc".

## Nguyên tắc cốt lõi
- **Đánh số folder** `00 → NN` theo thứ tự đọc; module-first cho tài liệu tính năng.
- **Song ngữ** VI + `.en` tương đương; **wikilink + MOC/Home**, không note mồ côi.
- **Không đoán — mở code/DB, dẫn `file:line`**; cái chưa chắc → OPEN QUESTION (xem [[07-investigation-playbooks]]).
- **Tài liệu theo thứ tự phụ thuộc**: document module sau khi đã có module nguồn dữ liệu của nó (vd Logistics sau Purchasing + System Setup).
- **Truy vết 2 chiều**: meeting ↔ CR ↔ Jira ↔ commit (ghim cả timestamp recording).
- **Kỷ luật Jira (luật Feroz)**: mọi việc có task · task↔PR · track tới production · Epic cho chương trình.

## Liên quan
- [[README|00-getting-started]]
