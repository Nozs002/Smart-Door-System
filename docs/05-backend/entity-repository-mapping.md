---
title: Mapping JPA và repository
type: reference
lang: vi
updated: 2026-10-13
---

# Mapping JPA và repository

Đã tạo 12 JPA entity và 12 Spring Data repository theo
[schema v2](../../infrastructure/mysql/init/init.sql). Mỗi bảng có một module
sở hữu; đã bổ sung service/controller đăng nhập/đăng ký/hồ sơ Account, các nghiệp vụ khác và Flyway
migration chưa triển khai.
Local/prod dùng Hibernate validate, cần schema v2 trước khi khởi động.

## Bảng và module sở hữu

| Bảng | Entity | Repository | Kiểu khóa |
| --- | --- | --- | --- |
| `accounts` | `identity/entity/Account` | `AccountRepository` | `Long` |
| `cardholders` | `credentials/entity/Cardholder` | `CardholderRepository` | `Long` |
| `doors` | `door/entity/Door` | `DoorRepository` | `Long` |
| `door_configs` | `settings/entity/DoorConfig` | `DoorConfigRepository` | `Long` |
| `system_pins` | `credentials/entity/SystemPin` | `SystemPinRepository` | `Long` |
| `devices` | `device/entity/Device` | `DeviceRepository` | `Long` |
| `door_activation_codes` | `door/entity/DoorActivationCode` | `DoorActivationCodeRepository` | `Long` |
| `device_lifecycle_events` | `device/entity/DeviceLifecycleEvent` | `DeviceLifecycleEventRepository` | `Long` |
| `rfid_credentials` | `credentials/entity/RfidCredential` | `RfidCredentialRepository` | `Long` |
| `access_logs` | `access/entity/AccessLog` | `AccessLogRepository` | `Long` |
| `lockout_statuses` | `access/entity/LockoutStatus` | `LockoutStatusRepository` | `LockoutStatusId` |
| `alerts` | `alert/entity/Alert` | `AlertRepository` | `Long` |

LockoutStatusId là Embeddable/Serializable, có equals/hashCode, gồm doorId và
Method (RFID/PIN). DoorConfig/SystemPin có khóa doorId do service cấp, không
auto-increment; các bảng còn lại dùng Long IDENTITY.

## Quy ước mapping

- Một JPA entity mỗi bảng; liên kết xuyên module dùng ID, không ManyToOne/Cascade.
  FK ghép và CHECK thực thi bởi schema MySQL, không tự sinh từ các trường ID.
- EnumType.STRING và columnDefinition khớp enum SQL. Enum đặt trong entity;
  DeviceLifecycleEvent dùng LifecycleStatus của Device trong cùng module.
- Getter/Setter/NoArgsConstructor dùng Lombok, không Data/ToString. Không log
  entity chứa hash hoặc trả entity trực tiếp ra REST; phải dùng DTO an toàn.
- Long/Integer chỉ biểu diễn phạm vi signed Java: service validate không âm
  và không vượt Long.MAX_VALUE/Integer.MAX_VALUE dù SQL là unsigned.
- LocalDateTime dùng UTC, DATETIME(3); PrePersist điền timestamp bắt buộc
  nếu chưa có, PreUpdate cập nhật updatedAt. AccessLog.occurredAt có thể NULL.
- Door/Device.version dùng Version optimistic locking. Config/PIN.version
  là phiên bản đồng bộ bắt đầu 1; service tăng và kiểm soát transaction.
- Device.activeNodeType do MySQL sinh: insertable=false/updatable=false,
  không setter. Muốn đọc giá trị mới sau thay lifecycle phải refresh hoặc
  load lại sau clear persistence context; không dùng thay kiểm tra lifecycle.
- appliedConfigVersion/appliedPinVersion chỉ cập nhật sau ACK đáng tin cậy.

## Repository

Các truy vấn có phạm vi Account/Door, phân trang lịch sử, lookup deviceCode,
eventId, UID đúng cửa. Chúng không thay thế kiểm tra Account từ JWT và ownership
trong service. JpaRepository có CRUD nhưng service không xóa dữ liệu lịch sử.

DoorRepository.findByIdForUpdate, DoorActivationCodeRepository.findByIdAndDoorIdForUpdate,
DeviceRepository.findByIdAndDoorIdForUpdate và LockoutStatusRepository.findByIdForUpdate
dùng PESSIMISTIC_WRITE. Phải gọi trong transaction; claim vẫn cần kiểm tra
hash, hạn mã và owner. Thay Node cần khóa Door/slot và retire bản cũ trước
activate bản mới. existsByEventId không thay thế UNIQUE khi message đồng thời.

## Kiểm chứng

[EntityRepositoryTests](../../backend/src/test/java/com/smartdoor/backend/persistence/EntityRepositoryTests.java)
kiểm tra persistence 12 bảng, enum, phạm vi truy vấn, khóa ghép, bộ đếm RFID/PIN
độc lập, query khóa hàng và optimistic version bằng H2 profile test. H2 không
chứng minh FK ghép, generated active slot hoặc cạnh tranh thực tế trên MySQL.
Maven do người dùng chạy theo quy ước đã thống nhất:

```powershell
cd backend
.\mvnw.cmd test
```

Đã biên dịch riêng entity/repository bằng javac --release 21 với dependency
có sẵn; chưa chạy Maven/test persistence. Xem [hướng dẫn database](../03-technical-reference/database-setup.md)
để kiểm chứng schema MySQL 8.4; cần bổ sung JPA validate trên MySQL trước tích hợp.

## Liên kết

- [Giải thích bảng và quan hệ](../03-technical-reference/database-erd.md)
- [Cấu trúc module](module-structure.md)
- [Kiến trúc Backend](architecture.md)
