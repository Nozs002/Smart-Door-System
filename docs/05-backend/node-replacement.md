# Bàn giao luồng thay Node và phục hồi vị trí đã retire — V14

Luồng mới giữ nguyên Door ID, chủ sở hữu, thẻ/PIN và lịch sử. Kỹ thuật viên
gắn một bo chưa dùng từ kho vào đúng vai trò được chủ cửa ủy quyền. Chủ cửa
cài Wi-Fi, đồng bộ và xác nhận chuyển sang bo mới. Không phải tạo/claim cửa mới.

## Những phần đã thêm hoặc thay đổi

| Phần | Thay đổi |
| --- | --- |
| Backend onboarding | ReplacementService/Controller quản lý mã ủy quyền, gắn bo, hủy và hoàn tất; mã giới hạn theo cửa/vai trò và có hạn 24 giờ |
| Lifecycle | REPLACE nhận cả nguồn RETIRED, giữ retired_at cũ; RETIRE bắt đầu thu hồi credential; chặn ACTIVATE trực tiếp để bỏ qua quy trình thay |
| Broker | Manifest v2 có danh sách thu hồi; supervisor restart Broker để ngắt phiên cũ và thử CONNECT bằng credential cũ; chỉ auth refusal mới là bằng chứng |
| Xác nhận thu hồi | Broker ghi proof vào volume riêng; Backend chỉ đọc và kiểm tra đúng device/credential/epoch. Không có API cho App tự ghi broker_revoked_at |
| Flutter chủ cửa | Thay thế thiết bị / Bổ sung Node thay thế, sao chép mã ủy quyền, cài Wi-Fi, gửi đồng bộ và theo dõi chuyển đổi |
| Flutter kỹ thuật viên | Trang chính có Thay Node cho cửa đã bàn giao; nhập mã ủy quyền và mã bo lẻ, xem các lần đã gắn |
| DB V14 | Thêm replacement_requests; không sửa V1–V13. DB có 22 bảng nghiệp vụ, 40 FK |

Source chính nằm ở
[ReplacementService](../../backend/src/main/java/com/smartdoor/backend/onboarding/ReplacementService.java),
[DeviceLifecycleService](../../backend/src/main/java/com/smartdoor/backend/device/service/DeviceLifecycleService.java),
[Broker proof reader](../../backend/src/main/java/com/smartdoor/backend/onboarding/BrokerRevocationProofReader.java),
[supervisor](../../infrastructure/mosquitto/config/supervise.py) và
[màn hình thay Node](../../mobile/lib/features/onboarding/replacement_screen.dart).

## Quy trình trên App

1. **Kỹ thuật viên chuẩn bị bo mới** trong Kho ESP32. Chọn đúng NODE_1/NODE_2,
   xuất header riêng và nạp firmware trước khi gắn vào cửa đã có chủ. Bo mới
   cần danh tính mới; không dùng lại mã/credential của bo hỏng.
2. **Chủ cửa** mở chi tiết thiết bị cũ, chọn **Thay thế thiết bị**. Nếu thiết bị
   đã RETIRED, chọn **Bổ sung Node thay thế**. Nhập lý do, bấm **Cấp mã ủy quyền
   thay Node**, sao chép mã và đưa cho kỹ thuật viên.
3. **Kỹ thuật viên** mở **Thay Node cho cửa đã bàn giao**, nhập hai
   mã: mã ủy quyền từ chủ cửa và mã bo ESP32 chưa gắn trong kho của mình.
   Bấm **Gắn bo vào cửa được ủy quyền**. API kiểm tra creator, vai trò, cửa,
   hạn mã và trạng thái; retry cùng mã/bo trả kết quả cũ, không tạo thêm Node.
4. **Chủ cửa** quay lại yêu cầu, bấm **Làm mới**, rồi **Cài Wi-Fi cho bo thay
   thế**. Kết nối SoftAP, gửi Wi-Fi nhà như luồng onboarding. Đưa điện thoại
   về mạng nhà, bấm **Đồng bộ cấu hình/PIN hiện tại**, chờ bo mới ACK.
5. Bấm **Xác nhận thay Node**. Trước bước này bo cũ chưa bị thay đổi bởi yêu
   cầu. Backend chỉ bắt đầu thu hồi sau khi bo mới ready. Khi bắt đầu, bo cũ
   ACTIVE được DISABLE, khóa ký cũ bị thu hồi; App hiện **Đang thu hồi kết nối
   bo cũ** và kiểm tra tiếp mỗi 3 giây, tối đa một phút mỗi lượt.
6. Supervisor cập nhật user/ACL, restart Broker và xác minh khóa cũ bị từ
   chối. Backend nhập proof, rồi yêu cầu hoàn tất mới được retire bo cũ và
   activate bo mới trong cùng transaction. App hiện **Đã thay thiết bị**.
   Nếu chưa xong, kiểm tra thiết bị/Broker rồi bấm **Tiếp tục hoàn tất**;
   đóng App không khôi phục khóa cũ. Không báo thành công khi còn thiếu proof/ACK.

Việc restart Broker có thể làm các thiết bị khác kết nối lại trong ít giây.
Đổi Wi-Fi không phải nạp lại chương trình; chuẩn bị một danh tính bo mới mới
cần header/firmware phù hợp. Không dùng phpMyAdmin để tự xác nhận thu hồi.

## Trạng thái yêu cầu và hủy

| Trạng thái | Ý nghĩa |
| --- | --- |
| OPEN | Chủ cửa đã cấp mã; chưa có bo thay thế; mã dùng để gắn trong 24 giờ |
| BOUND | Kỹ thuật viên đã gắn một bo PROVISIONED; chủ cửa đang thiết lập |
| REVOKING | Chủ cửa đã xác nhận; chờ proof Broker và ACK hiện hành trước chuyển đổi |
| COMPLETED | Bo cũ RETIRED, bo mới ACTIVE; retry không tạo lịch sử/lệnh lần hai |
| CANCELLED | Chủ cửa hủy trước REVOKING; bo đã staging nếu có bị retire/thu hồi danh tính |
| EXPIRED | Mã OPEN hết hạn; cấp yêu cầu mới để tiếp tục |

Chỉ hủy OPEN/BOUND. Hủy BOUND không tác động bo cũ; danh tính bo mới đã bị
thu hồi không được dùng lại. Muốn tái sử dụng phần cứng đó cần chuẩn bị danh
tính mới. REVOKING không có nút hủy/khôi phục khóa cũ; quay lại tiếp tục hoàn tất.

## Trường hợp Node cũ đã RETIRED

Không đưa nó về ACTIVE. Backend kiểm tra vị trí cùng vai trò chưa có Node
khác, tạo bo thay thế PROVISIONED, thu hồi/xác minh credential cũ nếu chưa
đủ bằng chứng, rồi kích hoạt bo mới. Bản ghi, retired_at và lịch sử cũ được
giữ; có thêm sự kiện liên kết thay thế. API ACTIVATE và complete-setup thông
thường không được dùng để bỏ qua proof của một bo thuộc yêu cầu thay thế.

Ngừng dùng trong Backend không xóa NVS/firmware hoặc điều khiển cơ khí tức
thời trên bo đang offline. Kỹ thuật viên phải tháo/ngắt bo cũ khi lắp bo mới.
Phản ứng nhiệt cục bộ vẫn theo firmware; không khẳng định RETIRE xóa dữ liệu
phần cứng hoặc dừng được mọi hành vi cục bộ.

## Database và triển khai

[V14](../../backend/src/main/resources/db/migration/V14__owner_authorized_node_replacement.sql)
thêm một bảng, 5 FK và unique generated slot theo Door/vai trò khi yêu cầu
còn OPEN/BOUND/REVOKING. Hash mã được lưu; mã rõ được dẫn xuất riêng theo
requestId để chủ cửa có thể xem lại trước khi gắn. Không lưu mật khẩu MQTT
hoặc bootstrap secret trong bảng yêu cầu. Thông tin yêu cầu trả Cache-Control no-store.

Compose thêm volume `broker-revocation`: Broker được ghi, Backend chỉ đọc.
Manifest vẫn nằm trên volume riêng hiện có, không chứa signingKey. CLI thu
hồi V12 vẫn dùng được; không phải điều kiện bắt buộc cho thao tác App mới.

Để chạy bản mới, từ root repo:

```powershell
python scripts/db-check.py --backup
docker compose up -d --build
docker compose logs --tail 80 backend
python scripts/db-check.py --expect-version 14
powershell -NoProfile -ExecutionPolicy Bypass -File .\mobile\tool\run-usb.ps1
```

Giữ nguyên volume DB; không chạy down -v và không sửa migration cũ. Bản
triển khai phải có cả Backend lẫn supervisor/Compose mới thì proof mới tự
động hoàn tất. Thiếu Broker proof, hệ thống giữ chờ và không tự nhận thành công.

## Kiểm thử và giới hạn

Kết quả kiểm tra ngày 18/11/2026: **204 test Backend, 81 test Flutter và
22 test Python đều đạt**; `flutter analyze` không có vấn đề. Backend đóng
gói thành công. Migration MySQL và Broker được kiểm tra riêng trong đợt đó.

- Backend: kiểm tra quyền cửa/role, sai vai trò hoặc kho, mã hết hạn/dùng lại,
  retry đồng thời, hủy, nguồn RETIRED, chờ proof và giữ lịch sử/chủ cửa.
- Flutter: kiểm tra nhập mã, màn khôi phục từ RETIRED và chỉ báo hoàn
  tất sau khi API trả COMPLETED. Analyze và test toàn bộ được chạy.
- Python: chỉ CONNACK auth refusal được nhận là proof; lỗi mạng hoặc CONNECT
  thành công không được coi là đã thu hồi.
- MySQL V1–V14 được chạy trong schema riêng, giữ DB ứng dụng; kiểm tra FK,
  CHECK và unique generated slot thật. Có 22 bảng nghiệp vụ, 40 FK.
- Broker thật trong container riêng: phiên cũ đã kết nối bị ngắt, đăng nhập
  cũ bị từ chối, danh tính mới vẫn đăng nhập được và file proof khớp được tạo.

Nhóm đã thử thay Node 2 trên hai ESP32 thật và mở chốt bằng PIN hiện hành
sau khi thay. Luồng RFID/PIN offline giữa hai bo chưa được bổ sung.
Xem [ERD cơ sở dữ liệu](../03-technical-reference/database-erd.md) và migration
V14 để đối chiếu bảng `replacement_requests` cùng các ràng buộc.

## Ảnh giao diện để đối chiếu

Ảnh được dựng từ widget thật bằng dữ liệu kiểm thử.

- [Chủ cửa: thiết lập và xác nhận bo thay thế](../06-frontend/images/replacement-owner-v14.png).
- [Kỹ thuật viên: nhập hai mã để gắn bo lẻ](../06-frontend/images/replacement-technician-v14.png).

[API thay Node](../../contracts/api/node-replacement.md) ·
[Onboarding](device-onboarding.md).
