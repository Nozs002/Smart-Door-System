# Backend

Spring Boot REST API, authentication, device lifecycle, access logs, alerts và
MQTT integration. Source đã có nền tảng cấu hình, response/error và security.

Khung tám module nghiệp vụ đặt dưới `src/main/java/com/smartdoor/backend/`:
`identity`, `credentials`, `door`, `device`, `access`, `control`, `settings`,
`alert`. Mỗi module có `controller/service/entity/repository/dto`; các module
nhận MQTT có thêm `mqtt`. Đã có 12 JPA entity/repository theo schema v2;
service/controller nghiệp vụ chưa triển khai. Xem [mapping JPA và repository](../docs/05-backend/entity-repository-mapping.md).

Xem [cấu trúc module và cách làm song song](../docs/05-backend/module-structure.md)
và [kiến trúc Backend](../docs/05-backend/architecture.md) trước khi thêm chức năng.
