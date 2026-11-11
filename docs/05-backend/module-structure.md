---
title: Cấu trúc module Backend và làm việc song song
type: reference
lang: vi
updated: 2026-10-13
---

# Cấu trúc module Backend và làm việc song song

Cấu trúc source ở [com.smartdoor.backend](../../backend/src/main/java/com/smartdoor/backend)
theo tám module trong [kiến trúc Backend](architecture.md). Mỗi module có
controller, service, entity, repository, dto. package-info.java mô tả trách
nhiệm và giữ thư mục trong Git. Đã thêm entity/repository cho 12 bảng;
đã bổ sung service/controller/DTO đăng nhập và JWT trong identity, đã qua
Maven verify ngày 14/10/2026. Đã bổ sung đăng ký/hồ sơ nhưng chưa chạy Maven
cho thay đổi mới; các nghiệp vụ khác chưa triển khai. Xem
[mapping JPA](entity-repository-mapping.md) và [đăng nhập](account-login.md).

## Ví dụ triển khai một chức năng

```text
device/
├── controller/DeviceController.java
├── service/DeviceService.java
├── entity/Device.java
├── entity/DeviceLifecycleEvent.java
├── repository/DeviceRepository.java
├── repository/DeviceLifecycleEventRepository.java
├── dto/DeviceResponse.java
├── dto/DisableDeviceRequest.java
└── mqtt/HeartbeatHandler.java
```

Device, DeviceLifecycleEvent và hai repository đã được tạo; các class
controller/service/DTO/handler còn là hướng dẫn cho bước triển khai. Một JPA
entity mỗi bảng là đủ trong giai đoạn này. Controller nhận DTO và gọi service;
service kiểm tra chủ cửa/lifecycle, cập nhật Device và event trong transaction.
Không tạo entity rỗng gắn @Entity vì sẽ ảnh hưởng Hibernate validate khi schema
chưa được áp dụng. Service lớn có thể tách Query/Lifecycle/HeartbeatService
trong cùng package mà không chuyển entity sang shared.

## Module giao tiếp với nhau

| Module gọi | Service của module sở hữu | Mục đích |
| --- | --- | --- |
| credentials/control/settings/alert/device | door/service | Kiểm tra Account sở hữu cửa |
| access | credentials/service | Kiểm tra RFID/PIN đúng cửa |
| control/settings | device/service | Đọc Node hợp lệ hoặc ghi nhận phiên bản đã áp dụng |
| access | settings/service | Đọc ngưỡng lockout |
| access | alert/service | Tạo cảnh báo sai nhiều lần |

Service public trả DTO/giá trị cần thiết, không trả entity/repository. Các tên
API nội bộ và DTO cần thống nhất trước khi code để có thể mock trong unit test.
Không tạo dependency ngược gây vòng như credentials → access → credentials.
Door claim phối hợp Device cần interface rõ ràng để không tạo door ↔ device;
workflow điều phối và transaction sẽ được chốt khi triển khai claim.

## Thư mục dùng chung

| Thư mục | Nội dung | Điều phối |
| --- | --- | --- |
| shared/api | ApiResponse và response advice đang có | Người 1 |
| shared/error | Exception nền, ErrorCode và handler chung | Người 1; mọi người đề xuất mã lỗi |
| shared/security | Lấy Account từ phiên xác thực; security handlers đang có | Người 1 |
| shared/observability | Correlation ID và tiện ích log | Người 1 |
| infrastructure/mqtt | Client, publisher, dispatcher và reconnect | Người 2 |
| configuration | Bean/properties/Security cấu hình ứng dụng | Chủ phần tương ứng |
| src/main/resources/db/migration | Migration Flyway khi bắt đầu triển khai | Người 2 điều phối |

Không gom entity nghiệp vụ vào shared. Không tạo thư mục migration rỗng hoặc
baseline migration chưa được kiểm chứng. JWT-specific code thuộc identity;
MQTT handler nghiệp vụ thuộc <module>/mqtt và gọi service sau validation.

## Quy tắc làm song song

1. Người 1: identity + credentials; người 2: door + device; người 3:
   access + control; người 4: settings + alert.
2. Chốt contract REST/MQTT và API service giữa module trước triển khai.
3. Mỗi người sửa module mình; thay file chung phải phối hợp người điều phối.
4. Mỗi migration mới một file/version riêng, không sửa migration đã áp dụng;
   FK xuyên module được review cùng chủ bảng.
5. Unit test mock service module khác; integration test kiểm chứng quyền sở
   hữu, FK và transaction trên MySQL. Test nằm cùng tên module dưới src/test.
6. Không tạo branch/commit/push tự động; theo CONTRIBUTING và quyền người dùng.

## Xác minh

Entity/repository đã mapping database nhưng chưa có endpoint/service nghiệp
vụ ngoài đăng nhập/đăng ký/hồ sơ Account. Local/prod cần schema v2 để Hibernate validate thành công. Theo quy ước
kiểm thử đã thống nhất, Maven do người dùng chạy:

```powershell
cd backend
.\mvnw.cmd test
```

Khi bổ sung nghiệp vụ cần chạy đầy đủ kiểm thử thích hợp; tạo thư mục không
đồng nghĩa tính năng đã hoàn thành.
