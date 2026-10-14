---
title: Tài liệu Backend
type: moc
lang: vi
tags: [backend, spring-boot]
updated: 2026-10-14
---

# Tài liệu Backend

Khu vực lưu tài liệu thiết kế và phát triển Spring Boot Backend, bao gồm API, bảo mật, dữ liệu, MQTT integration và migration.

## Tài liệu

- [Đăng nhập Backend](account-login.md) — contract, JWT, rate limit và xác minh.

- [Mapping JPA và repository](entity-repository-mapping.md) — 12 bảng, khóa ghép,
  enum, version, truy vấn phạm vi, khóa hàng và cách kiểm thử.

- [Cấu trúc module và làm việc song song](module-structure.md) — tám module,
  controller/service/entity/repository/dto và quy tắc thư mục dùng chung.

- [Kiến trúc Backend](architecture.md) — kiến trúc mục tiêu, ranh giới module,
  cấu hình, bảo mật, quan sát, dữ liệu, MQTT và kiểm thử.
- [Cấu trúc xử lý lỗi Backend](error-handling.md) — error code, exception,
  Problem Details, correlation ID, quy tắc title tiếng Việt và cách kiểm thử.
- [Cấu trúc phản hồi REST API](api-response.md) — `ApiResponse<T>` cho response
  JSON thành công và Problem Details cho response lỗi.
- [Cấu hình môi trường Backend](environment-configuration.md) — profiles
  `local`, `test`, `prod`, typed configuration và biến môi trường.
- [Nền tảng bảo mật Backend](security-foundation.md) — security filter chain,
  CORS, BCrypt và chuẩn hóa lỗi `401/403`.

## Hiện trạng

Đã có nền tảng cấu hình, bảo mật, response/lỗi và mapping 12 entity/repository.
Đã bổ sung controller/service/DTO đăng nhập và JWT; Maven verify ngày 14/10/2026
đạt 25 test và build JAR thành công. Xem bằng chứng tại tài liệu đăng nhập.
Các nghiệp vụ khác, ownership service, MQTT client và Flyway migration
chưa triển khai. Local/prod cần schema v2 đã khởi tạo để Hibernate
validate; cấu hình Flyway hiện có chưa thay thế quy trình khởi tạo schema.

Theo quy ước dự án, người dùng chạy Maven; agent chỉ chạy khi được yêu cầu
rõ ràng. Có source test không đồng nghĩa đã có kết quả kiểm thử mới.

## Liên kết

- [Chỉ mục tài liệu](../00-INDEX.md)
- [Tài liệu module](../02-modules/README.md)
- [Tham khảo kỹ thuật](../03-technical-reference/README.md)
- [API template](../00-getting-started/templates/api-template.md)

