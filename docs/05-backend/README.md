---
title: Tài liệu Backend
type: moc
lang: vi
tags: [backend, spring-boot]
updated: 2026-10-03
---

# Tài liệu Backend

Khu vực lưu tài liệu thiết kế và phát triển Spring Boot Backend, bao gồm API, bảo mật, dữ liệu, MQTT integration và migration.

## Tài liệu

- [Kiến trúc Backend](architecture.md) — kiến trúc mục tiêu, ranh giới module,
  cấu hình, bảo mật, quan sát, dữ liệu, MQTT và kiểm thử.
- [Kế hoạch triển khai Backend](implementation-plan.md) — lộ trình theo giai
  đoạn, quality gate, quy ước kiểm thử Maven, thứ tự Pull Request và rủi ro.
- [Cấu trúc xử lý lỗi Backend](error-handling.md) — error code, exception,
  Problem Details, correlation ID, quy tắc title tiếng Việt và cách kiểm thử.
- [Cấu trúc phản hồi REST API](api-response.md) — `ApiResponse<T>` cho response
  JSON thành công và Problem Details cho response lỗi.
- [Cấu hình môi trường Backend](environment-configuration.md) — profiles
  `local`, `test`, `prod`, typed configuration và biến môi trường.

## Liên kết

- [Chỉ mục tài liệu](../00-INDEX.md)
- [Tài liệu module](../02-modules/README.md)
- [Tham khảo kỹ thuật](../03-technical-reference/README.md)
- [API template](../00-getting-started/templates/api-template.md)

