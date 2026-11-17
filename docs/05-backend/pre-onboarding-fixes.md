# Sửa lỗi trước khi làm onboarding kỹ thuật viên

Cập nhật 12/11/2026. Phạm vi: sửa các điểm lệch sau khi tích hợp `dev`, chuẩn bị
nền tảng cho giao diện kỹ thuật viên/QR/Wi-Fi. Chưa triển khai các màn hình mới.

## Những gì đã đổi

| Vấn đề trước đây | Bản sửa | Nơi đọc code |
| --- | --- | --- |
| Merge dở, hướng dẫn mở cửa ghi nhầm V7 | Hoàn tất merge local `7503a8d`, giữ thay đổi từ dev và sửa hướng dẫn V11 | `docs/06-frontend/door-unlock.md` |
| App cho PIN 4–8 số, keypad tự gửi ở số thứ tư | Nhập 4–8 số, gửi bằng #; không gửi một đoạn PIN trước khi người dùng xác nhận | `firmware/node-auth/PinInput.h`, `NodeAuthApp.h` |
| Hàng phím `* 0 #` hỏng, không demo được | Giữ 1: xóa; giữ 2: nhập 0; giữ 3: gửi. Bấm ngắn vẫn nhập số; không lặp khi giữ/thả | `PinInput::Gestures`, `AuthProtocolTests.h` |
| Node 1 mới không ACK PIN/thẻ nên không kích hoạt được | Nhận snapshot, kiểm tra định dạng, lưu NVS theo danh tính, ACK đúng version sau đọc lại; hỗ trợ BCrypt, không giả ACK | `CredentialSnapshot.h`, `NodeAuthApp.h`, `src/bcrypt/` |
| Thay Node luôn bị chặn vì chưa có bằng chứng thu hồi Broker | Thêm quy trình kỹ thuật revoke → cập nhật users/ACL → restart Broker → kiểm tra từ chối khóa cũ → xác nhận → cho phép replace | `scripts/setup-mqtt.py`, `DeviceTransportCredentialService`, `SettingsDeviceReadinessPolicy` |
| Tài liệu nói firmware chưa có/offline bị bỏ | Ghi đúng hai sketch tích hợp và giới hạn nghiệm thu; offline còn trong phạm vi nhưng chưa có luồng mở offline | README firmware, tài liệu yêu cầu/demo/thiết kế |

## Database và hợp đồng

- V11 từ dev thêm `control_commands.action`, lệnh cũ mặc định `UNLOCK`, hỗ trợ `CLOSE`.
- **V12 mới** thêm `device_transport_credentials.broker_revoked_at DATETIME(3) NULL`.
  Không đổi PK/FK, không xóa bảng/cột/dữ liệu; vẫn 19 bảng nghiệp vụ, 31 FK.
  CHECK buộc có `revoked_at` và thời điểm xác nhận không trước thời điểm thu hồi.
  Dữ liệu cũ có xác nhận NULL, không tự coi đã thu hồi Broker.
- Node 1 nhận snapshot CREDENTIALS tối đa 16384 byte; Backend không cắt danh
  sách để vừa giới hạn. Quá giới hạn là lỗi giao, không phải đã đồng bộ.
- PIN trên API vẫn 4–8 chữ số. Không đổi định dạng JWT hay yêu cầu quyền chủ cửa.
- CLI thu hồi là đặc quyền kỹ thuật local; App không được tự ghi bằng chứng.
  `revoke` vĩnh viễn cho danh tính Node cũ; `DISABLE` vẫn là tạm ngừng nghiệp vụ.

## Thử keypad không cần hàng cuối

Upload lại Node 1 trước. Ví dụ PIN `1203`: bấm ngắn 1, bấm ngắn 2, **giữ 2**,
bấm ngắn 3, **giữ 3** gửi. Giữ khoảng 0,9 giây rồi thả; giữ 1 để xóa toàn bộ.
Test PIN 4/6/8 số, PIN chứa 0, nhập thiếu rồi gửi, nhập số thứ chín, xóa,
giữ/thả không gửi trùng. NVS/Serial không được lưu/in PIN rõ.
Đây là phím dự phòng; ba phím cuối chung R4/GPIO26, vẫn cần sửa dây/phần cứng.

## Thử kích hoạt bộ cửa mới, không sửa DB

1. Provision bộ Door + hai Node bằng công cụ kỹ thuật hiện có; chủ cửa claim trên App.
2. Cấp danh tính MQTT riêng từng Node, chạy `setup-mqtt.py configure`, nạp firmware
   đúng vai trò với file cấu hình local riêng. Không dùng credential của Node khác.
3. Trên App đặt PIN, thêm thẻ nếu cần; gửi cấu hình và đồng bộ PIN/thẻ.
4. Đợi heartbeat và ACK. Node 1 cần `applied_config_version` khớp config.version,
   `applied_pin_version` khớp credentials_version và PIN ACTIVE. Node 2 cần ACK cấu hình.
5. Kích hoạt từng Node trên App; đủ hai vai trò thì Door ACTIVE. Test RFID/PIN và mở/đóng.
6. Đổi PIN/thu hồi thẻ rồi đồng bộ; reset Node 1, thử snapshot trùng và version cũ.
   Trùng hợp lệ ACK lại; version cũ/cùng version khác nội dung không ACK.
7. Không dùng simulator ACK thay cho Node thật để chứng minh bộ cửa đã sẵn sàng.

## Thử thay Node

Chuẩn bị Node thay thế cùng Door/vai trò ở PROVISIONED, cấp credential và đồng
bộ đầy đủ trước. Chạy từ root bằng Python đã cài `scripts/requirements.txt`:

```powershell
python scripts/setup-mqtt.py revoke <old-device-id>
```

Script thu hồi khóa Backend trước, loại khỏi Broker, restart để ngắt phiên cũ,
kiểm tra Backend vẫn đăng nhập được và khóa cũ bị từ chối. Chỉ sau đó ghi xác
nhận. Nếu gián đoạn, chạy lại cùng ID; không sửa DB. Tiếp theo chủ cửa chọn
Node thay thế trên App. Kiểm tra Node cũ RETIRED, mới ACTIVE, lịch sử giữ đủ.
Không test revoke trên Node đang cần demo nếu chưa chuẩn bị Node thay thế.

## Cập nhật môi trường local

Nạp Node 2 hỗ trợ CLOSE trước, Node 1 bản mới, sau đó nâng Backend/App. Giữ volume:

```powershell
docker compose up -d --build backend
docker compose logs --tail 80 backend
powershell -NoProfile -ExecutionPolicy Bypass -File mobile/tool/run-usb.ps1
```

Mở `http://localhost:8080/actuator/health`; không chạy `down -v` để xử lý migration.
Kiểm thử migration của đợt sửa dùng schema riêng, chưa tự nâng DB local đang dùng.

## Kiểm chứng và giới hạn

Kết quả chạy 11–12/11/2026:

| Kiểm tra | Kết quả / giới hạn |
| --- | --- |
| Backend `mvnw verify` | 190 test đạt; sau bổ sung nhánh CLI/check-schema, chạy thêm nhóm 31 test liên quan, đều đạt (có 3 ca mới) |
| Flutter | Analyze không có vấn đề; 69 test đạt. Đợt sửa này không đổi UI Flutter |
| Python unittest | 12 test đạt: simulator và fail-closed của thu hồi Broker |
| MySQL thật, schema riêng | V9→V10→V11→V12 đạt; dữ liệu cũ giữ nguyên; CHECK/FK và Hibernate validate đạt |
| Mosquitto riêng | Xác nhận phiên cũ bị ngắt, khóa cũ bị từ chối, backend user vẫn đăng nhập được |
| C++ native trong container | Logic phím ngắn/giữ, số 0/xóa/gửi/giới hạn/reset và vector BCrypt độc lập đạt |
| ESP32 Node 1 | Build ESP32 core 3.3.11 với cấu hình mẫu và cấu hình giả đầy đủ đạt; bản đầy đủ 77% flash, 16% RAM tĩnh |
| ESP32 Node 1 self-test | Build chế độ self-test đạt; chưa chạy suite này trên board thật |
| ESP32 Node 2 | Build cấu hình giả đầy đủ đạt; 76% flash, 15% RAM tĩnh |

LiquidCrystal I2C có cảnh báo metadata khai báo AVR; compiler vẫn build ESP32
thành công. Chưa nghiệm thu GPIO, NVS khi mất nguồn, hoạt động servo/MC-38 hoặc
luồng kích hoạt bộ cửa mới bằng **hai ESP32 thật**. Không upload các cấu hình
giả trong `.local/firmware-build-src/` lên thiết bị.

Log chính: `backend/target/pre-onboarding-verify.log`,
`backend/target/pre-onboarding-focused.log`, `.local/pre-onboarding-migrations.log`,
`.local/broker-revocation-check.log`, `.local/node1-full-build.log`,
`.local/node1-selftest-build.log`, `.local/node2-full-build.log`.
Các log riêng không đưa lên Git. Bản này là checkpoint trên nhánh
`feature/VM-feature` trước khi triển khai luồng onboarding mới; merge local đã hoàn tất.

Firmware tích hợp hiện xác thực online. Snapshot lưu được và BCrypt hoạt động
không đồng nghĩa có luồng mở offline: còn thiếu kênh Node 1 → Node 2, chính sách
quyền/thu hồi khi mất mạng và đối soát. NVS demo chưa mã hóa flash, MQTT local
chưa TLS; chưa chứng nhận cho triển khai thực tế. Chưa có phân quyền kỹ thuật
viên trên App, scan QR, cấp danh tính tự động hoặc màn hình cấu hình Wi-Fi.
