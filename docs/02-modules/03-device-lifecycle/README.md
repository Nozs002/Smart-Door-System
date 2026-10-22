---
title: Vòng đời và kết nối thiết bị
type: module
version: 1.0
lang: vi
tags: [module, device, lifecycle, heartbeat]
updated: 2026-10-16
---

# Vòng đời và kết nối thiết bị

## 1. Mục tiêu

Quản lý từng Node của Door, quyền tham gia nghiệp vụ và lịch sử thay đổi; suy
ra trạng thái kết nối riêng từ heartbeat. Không đồng nhất ONLINE với ACTIVE
và không suy ra Node 2 offline từ việc Node 1 mất heartbeat.

Code backend cung cấp service, API và các đầu nối kiểm tra readiness/thu hồi
quyền. Adapter MQTT, ACK thật và firmware được tích hợp sau; cấu hình mặc định
từ chối kích hoạt khi chưa xác minh các điều kiện này.

## 2. Phạm vi

### Bao gồm

- Liệt kê/xem Device đúng Door, gồm cả Node cũ để truy vết.
- Activate, disable, reactivate, retire; thay Node cùng Door/vai trò.
- Lịch sử lifecycle ghi cùng transaction với thay đổi trạng thái.
- Service nhận heartbeat từ nguồn thiết bị đã xác thực; thời gian Backend nhận.
- Suy ra UNKNOWN/ONLINE/OFFLINE với timeout cấu hình được.
- API lịch sử phân trang, lọc theo Device.

### Không bao gồm

- Tự phát credential/ACL, subscribe topic, publish lệnh, ACK thật hoặc firmware.
- REST công khai nhận heartbeat/ghi ACK; client App không được tự báo thiết bị sẵn sàng.
- RFID/PIN authentication, điều khiển khóa, access log và alert của Người 3/4.
- Chuyển Device sang Door khác hoặc xóa Device có lịch sử.
- Dùng đổi trạng thái database để khẳng định ESP32 đã dừng hoạt động vật lý.

## 3. Thành phần liên quan

| Thành phần | Trách nhiệm |
| --- | --- |
| `device.service` | Lifecycle, readiness, replacement, heartbeat, connection status |
| `device.controller` | REST theo Door; lấy Account xác thực và kiểm tra sở hữu |
| `door.service` | Ownership; khóa Door để serialize thao tác cùng cửa |
| Database | Device/event, version, unique active slot và FK đúng Door |
| Người 4 | Cấu hình/PIN hiện hành và bằng chứng ACK áp dụng đúng phiên bản |
| MQTT/firmware | Danh tính đáng tin cậy, heartbeat và thực thi cấp/thu hồi quyền |
| Mobile | Màn hình Device/lifecycle/lịch sử, hiển thị lifecycle và kết nối riêng |

## 4. Actor và quyền hạn

| Actor | Quyền hoặc hành động |
| --- | --- |
| Chủ Door | Xem Node/lịch sử; yêu cầu activate, disable, reactivate, retire và thay bằng Node đã đăng ký |
| Quy trình kỹ thuật CLI | Đăng ký Node thay thế đúng cửa/vai trò; không tự chiếm slot ACTIVE |
| Adapter thiết bị đáng tin cậy | Chuyển heartbeat đã xác thực vào service; chưa có HTTP công khai |
| Account khác | Không đọc/ghi Node/lịch sử; 404 |

## 5. Luồng chính

### 5.1. Transition lifecycle

| Trạng thái trước | Hành động | Trạng thái sau | Điều kiện |
| --- | --- | --- | --- |
| PROVISIONED | ACTIVATE | ACTIVE | Door đã claim, đúng slot/vai trò, readiness hợp lệ |
| ACTIVE | DISABLE | DISABLED | Chủ cửa, lý do hợp lệ, ghi event |
| DISABLED | REACTIVATE | ACTIVE | Kiểm tra lại slot và readiness như activate |
| ACTIVE/DISABLED | RETIRE | RETIRED | Chủ cửa, lý do hợp lệ, giữ dữ liệu lịch sử |
| RETIRED | Bất kỳ chuyển trạng thái | Từ chối | Trạng thái kết thúc |

Các cạnh khác không tự được chấp nhận. ACTIVATE và REACTIVATE là hai hành động
riêng, không dùng một endpoint sửa enum tùy ý.

1. Xác định Account và kiểm tra ownership.
2. Khóa Door/Device trong transaction; kiểm tra lại trạng thái và vai trò.
3. Với chuyển sang ACTIVE, kiểm tra danh tính thiết bị, cấu hình/PIN và active slot.
4. Thực hiện transition, mốc thời gian, event với actor/lý do/trước/sau.
5. Commit nguyên tử; lỗi trước commit không để lại event nửa chừng.

Không có ACK/readiness đáng tin cậy thì trả lỗi; không coi heartbeat hay các
trường version do App gửi là bằng chứng sẵn sàng.

### 5.2. Thay Node

1. Kỹ thuật viên đăng ký Node mới qua CLI, cùng Door và cùng `node_type` với Node cũ.
2. Node mới ở PROVISIONED, có sự kiện provision; Node cũ chưa tự bị retire.
3. Chủ cửa gửi `replacementDeviceId` và lý do thay.
4. Kiểm tra ownership, Device khác nhau, cùng Door/vai trò, Node cũ có trạng
   thái cho phép thay và Node mới PROVISIONED.
5. Kiểm tra readiness của Node mới và điều kiện thu hồi quyền của Node cũ qua adapter.
6. Trong transaction, retire Node cũ, ghi event REPLACED liên kết Node mới,
   activate Node mới và ghi event tương ứng; giữ nguyên Door và bản ghi cũ.

Adapter mặc định chưa xác minh được readiness/thu hồi MQTT nên từ chối chuyển
ACTIVE/thay Node. Không tự mở đường bypass để minh họa; kiểm thử dùng adapter giả.
Tích hợp thật cần xử lý lỗi/retry giữa database và Broker: transaction SQL
không thể tự đảm bảo tác động bên ngoài cũng rollback.

### 5.3. Heartbeat và kết nối

Adapter MQTT tương lai phải đối chiếu credential Broker với Device/Door trước
khi gọi service. Service dùng giờ nhận từ Clock của Backend, không dùng timestamp
ESP32 để kéo dài ONLINE và không nhận version cấu hình/PIN như ACK mặc định.

| Điều kiện | Trạng thái kết nối |
| --- | --- |
| `lastHeartbeatAt` chưa có | UNKNOWN |
| Thời gian từ heartbeat gần nhất chưa quá timeout | ONLINE |
| Đã quá timeout | OFFLINE |

Mốc thử nghiệm: Node gửi mỗi 30 giây, timeout 90 giây, cần chỉnh theo firmware.
Kết nối được suy ra khi query; thời gian trôi qua đủ làm Node thành OFFLINE,
không cần một job sửa lifecycle trong database. Heartbeat không tự activate,
khôi phục Node DISABLED hoặc mở khóa Door.

## 6. Luồng lỗi và ngoại lệ

| Trường hợp | Cách xử lý |
| --- | --- |
| Account không sở hữu Door hoặc Device không thuộc Door trong URL | 404 |
| Transition không cho phép/Node đã RETIRED | 409, giữ nguyên dữ liệu |
| Thiếu readiness/ACK/danh tính | 409, không chuyển ACTIVE |
| Đã có Node ACTIVE cùng vai trò | Từ chối; khóa Door và unique active slot bảo vệ đồng thời |
| Replacement khác vai trò/cửa, trùng Device cũ hoặc không PROVISIONED | Từ chối, rollback |
| Không có bằng chứng thu hồi quyền Node cũ | Không báo thay Node thành công |
| Chưa nhận heartbeat | UNKNOWN, không phải lỗi và không tự DISABLED |
| Node DISABLED/RETIRED vẫn liên lạc | Không cấp lại quyền nghiệp vụ chỉ vì kết nối còn sống |

## 7. Quy tắc nghiệp vụ

- Lifecycle là quyền sử dụng, connection là tình trạng liên lạc; DTO trả hai trường riêng.
- Tối đa một ACTIVE cho mỗi `(door_id, node_type)`; lịch sử Node cũ giữ nguyên.
- Door chỉ được coi vận hành khi bộ Node và điều kiện cần thiết đã đạt; chưa
  đủ bộ sau claim/thay thiết bị không tự báo sẵn sàng.
- `device_code`, `door_id`, `node_type` ổn định sau đăng ký.
- Event có ID duy nhất, Device/Door, actor (NULL cho CLI), type, from/to,
  replacement nếu có, lý do và giờ Backend UTC.
- RETIRE và replacement yêu cầu lý do để truy vết, tối đa 500 ký tự;
  ACTIVATE/DISABLE/REACTIVATE cho phép lý do tùy chọn.
- applied config/PIN version là dữ liệu ACK đáng tin cậy; heartbeat thông thường
  không phải lối ghi ACK. Không log bí mật hoặc chép PIN vào reason.
- Mất mạng không tự sửa lifecycle. Disable/retire có hiệu lực kiểm tra quyền
  backend ngay; việc ESP32 dừng hoạt động cục bộ còn phụ thuộc tích hợp thiết bị.
- Module xác thực/cấu hình dùng `DeviceOperationalService.requireActive(identity)`
  để kiểm tra Node đúng danh tính và ACTIVE trước nghiệp vụ. Đây là kiểm tra
  tại thời điểm gọi, không khóa thao tác Broker tương lai.

## 8. Dữ liệu và tích hợp

Entity chính: `Device`, `DeviceLifecycleEvent`; đọc Door, DoorConfig, SystemPin
để kiểm tra điều kiện. Không thêm cột ONLINE/OFFLINE vào enum lifecycle.

| REST API | Mục đích |
| --- | --- |
| `GET /api/v1/doors/{doorId}/devices` | Danh sách Node, gồm Node đã thay |
| `GET /api/v1/doors/{doorId}/devices/{deviceId}` | Chi tiết Node đúng Door |
| `POST /api/v1/doors/{doorId}/devices/{deviceId}/lifecycle` | action + reason |
| `POST /api/v1/doors/{doorId}/devices/{deviceId}/replace` | replacementDeviceId + reason |
| `GET /api/v1/doors/{doorId}/lifecycle-events?page=0&size=20&deviceId=...` | Lịch sử mới nhất trước, có thể lọc Device |

Phân trang mặc định 20, tối đa 100. [OpenAPI](../../../contracts/api/door-device.openapi.yaml)
định nghĩa DTO/response/error. Không có endpoint public provision, heartbeat hoặc
"set readiness=true".

## 9. Kiểm thử

Checklist nghiệm thu; kết quả thực thi xem [bàn giao](../../05-backend/door-device-handoff.md).

- [ ] UNKNOWN trước heartbeat; ONLINE sau nhận; OFFLINE quá timeout bằng Clock kiểm soát được.
- [ ] Node 1/2 độc lập; mất heartbeat không sửa lifecycle.
- [ ] Transition đúng, cạnh sai bị từ chối, RETIRED không phục hồi.
- [ ] Không readiness thì activate/reactivate/replace thất bại.
- [ ] Không thu hồi được quyền Node cũ thì replacement không hoàn tất.
- [ ] Replacement cùng cửa/vai trò, Node cũ giữ lịch sử, mỗi vai trò chỉ một ACTIVE.
- [ ] Lỗi giữa workflow rollback Device/event, không có lịch sử thành công giả.
- [ ] Event ghi đúng Account/lý do/trạng thái; lịch sử chỉ chủ Door được xem.
- [ ] Kiểm tra ràng buộc active slot trên MySQL thật, không chỉ H2.

## 10. Vấn đề cần làm rõ

- [ ] Người 4 và firmware hoàn thiện nguồn ACK đúng Node/version và quy tắc
  readiness cho từng vai trò; chưa mặc định Node 2 có yêu cầu PIN giống Node 1.
- [ ] Nhóm triển khai adapter danh tính và thu hồi credential/ACL cùng cơ chế retry.
- [ ] Đặc tả MQTT topic, QoS/retained, payload, chống replay và xác minh nguồn.
- [ ] Kiểm thử timeout 30/90 giây với mạng/firmware thật trước khi vận hành.
- [ ] Chưa đặt tự động xóa lịch sử; thay đổi thời hạn phải là quyết định riêng.
- [ ] Ghép JWT/token provider và nghiệm thu màn hình Flutter với backend thật.

## 11. Tài liệu liên quan

- [Quản lý cửa và thiết bị](../02-device-management/README.md)
- [Quyết định sở hữu và lifecycle](../../01-requirements/ownership-device-lifecycle-decisions.md)
- [Database ERD](../../03-technical-reference/database-erd.md)
- [Bàn giao backend Người 2](../../05-backend/door-device-handoff.md)
