---
title: Tài liệu Backend
type: moc
lang: vi
tags: [backend, spring-boot]
updated: 2026-10-28
---

# Tài liệu Backend

Khu vực lưu tài liệu thiết kế và phát triển Spring Boot Backend, bao gồm API, bảo mật, dữ liệu, MQTT integration và migration.

## Tài liệu

- [Hoàn thiện Người 1, Người 4](person1-person4-completion.md) — chức năng,
  thay đổi DB, MQTT, cách chạy API/Android và kiểm thử đầu cuối không cần ESP32.

- [Đăng ký và hồ sơ Account](account-registration-profile.md) — API, persistence và tích hợp Mobile.

- [Đăng nhập Backend](account-login.md) — contract, JWT, rate limit và xác minh.

- [Bàn giao backend Người 2](door-device-handoff.md) — phạm vi đã triển khai,
  provision/claim, lifecycle, lệnh chạy, checklist kiểm thử và đầu nối Người 1/4.

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

Đã có nền tảng cấu hình, bảo mật, response/lỗi, mapping entity/repository,
đăng nhập JWT, đăng ký/hồ sơ Account, ownership Door và lifecycle thiết bị.
Xem bằng chứng và giới hạn triển khai tại các tài liệu tương ứng bên trên.
Đã có Flyway V1–V6, Cardholder/RFID/PIN, thu hồi phiên/đổi mật khẩu, MQTT cho
cấu hình/ACK/cảnh báo, outbox và inbox. Ngày 28/10/2026 đã chạy Maven verify
đạt 160 tests và kiểm thử đầu cuối trên Docker MySQL/Mosquitto với hai Node
giả lập. Luồng mở khóa/lịch sử truy cập Người 3, firmware và OS push chưa được
xem là hoàn thành. Xem báo cáo Người 1/4 để biết cách chạy và giới hạn.

Theo quy ước dự án, người dùng chạy Maven; agent chỉ chạy khi được yêu cầu
rõ ràng. Có source test không đồng nghĩa đã có kết quả kiểm thử mới.

## Liên kết

- [Chỉ mục tài liệu](../00-INDEX.md)
- [Tài liệu module](../02-modules/README.md)
- [Tham khảo kỹ thuật](../03-technical-reference/README.md)
- [API template](../00-getting-started/templates/api-template.md)

