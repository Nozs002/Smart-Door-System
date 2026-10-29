# Cardholder, RFID và PIN theo cửa

Contract bổ sung ngày 28/10/2026. Tất cả endpoint yêu cầu JWT Account ACTIVE.
Response dùng envelope ApiResponse và lỗi Problem Details như các API khác.
Sai chủ/tài nguyên không tồn tại trả 404; Account ID không lấy từ body.

## Endpoint và DTO

| Method | Path | Body / kết quả |
| --- | --- | --- |
| GET | `/api/v1/cardholders?page=0&size=20` | PageResponse CardholderView của Account hiện tại |
| POST | `/api/v1/cardholders` | `{displayName}` → 201 CardholderView |
| PATCH | `/api/v1/cardholders/{id}` | `{displayName,status}` → 200 CardholderView |
| GET | `/api/v1/doors/{doorId}/rfid-credentials?page=0&size=20` | PageResponse RfidView đúng cửa |
| POST | `/api/v1/doors/{doorId}/rfid-credentials` | `{cardholderId,uid,expiresAt?}` → 201 RfidView |
| POST | `/api/v1/doors/{doorId}/rfid-credentials/{id}/revoke` | Không body → 200 RfidView; retry không tăng phiên bản lần nữa |
| GET | `/api/v1/doors/{doorId}/pin` | PinStatus; không trả PIN/hash |
| PUT | `/api/v1/doors/{doorId}/pin` | `{pin}` → 200 PinStatus |

- `CardholderView`: `id`, `displayName`, `status` (`ACTIVE`/`DISABLED`), `createdAt`.
- `RfidView`: `id`, `doorId`, `cardholderId`, `cardholderName`, `uid`, `status`,
  `issuedAt`, `revokedAt`, `expiresAt`. Thời gian UTC ISO-8601; nullable khi chưa có.
- `PinStatus`: `doorId`, `configured`, `status`, `version`, `changedAt`.
  Khi chưa có PIN: `configured=false`, `status=NOT_CONFIGURED`, `version=0`.
- List trả `{items,page,size,totalElements,totalPages}`, size từ 1 đến 100.

## Quy tắc nghiệp vụ

- Cardholder thuộc đúng một Account; không đăng nhập App. Tên có nội dung,
  tối đa 100 ký tự. PATCH nhận cả displayName và status.
- Cardholder phải ACTIVE và thuộc cùng Account sở hữu Door trước khi cấp RFID.
- UID RC522 được chuẩn hóa uppercase hex, bỏ khoảng trắng/dấu `:`/`-`; nhận
  UID 4/7/10 byte (8/14/20 ký tự hex). Ví dụ `aa:bb:cc:dd` → `AABBCCDD`.
- Cùng UID được cấp tại nhiều cửa. Tại một cửa, UID gắn với một Cardholder;
  không đổi người giữ thẻ trên bản ghi đã có để giữ ý nghĩa lịch sử. Có thể cấp
  lại cho chính Cardholder đó sau thu hồi/hết hạn; cấp trùng còn hiệu lực trả 409.
- expiresAt nếu gửi phải ở tương lai. API hiển thị EXPIRED khi quá hạn dù chưa
  có tác vụ đổi enum lưu trong DB. Backend/firmware phải kiểm tra thời hạn khi dùng.
- Vô hiệu hóa Cardholder thu hồi các RFID của họ cùng transaction và đưa
  họ ra khỏi snapshot thiết bị; tăng credentialsVersion tại từng cửa liên quan.
  Kích hoạt lại Cardholder không khôi phục thẻ; chủ cửa phải cấp lại rõ ràng.
- PIN chung của Door gồm 4–8 chữ số; chỉ lưu BCrypt, không phát PIN rõ. Đổi PIN
  tăng phiên bản PIN và credentialsVersion; không ảnh hưởng mật khẩu Account.
- PIN/RFID/Cardholder-status thay đổi gọi `requestCredentialsSync` cùng
  transaction. Đồng bộ chưa có ACK phải được hiển thị là đang chờ/chưa xác nhận,
  không coi lưu MySQL là thiết bị đã áp dụng.
- Writer lấy khóa Account, sau đó các Door theo ID tăng dần, rồi mới Cardholder/
  credential. Khóa Account bảo vệ disable Cardholder đồng thời với cấp thẻ mới.

## Đầu nối nội bộ

`CredentialVerificationService.verifyRfid(doorId,uid)` / `verifyPin(doorId,pin)`
trả `VerificationResult(allowed,reason,credentialId,cardholderId)` mà không lộ
hash/credential đầu vào. Service kiểm tra Door ACTIVE, phạm vi Account, trạng
thái/thời hạn RFID và Cardholder hoặc PIN tương ứng. Đây chỉ là hàm kiểm tra;
luồng nhận yêu cầu, lockout, mở khóa và access log thuộc module người 3.

`CredentialSnapshotService.snapshot(doorId)` là cổng **nội bộ nhạy cảm** cho
adapter đồng bộ Node 1: `{doorId,version,pinVerifier,cards}`. pinVerifier là
BCrypt của PIN riêng cửa, tuyệt đối không phải password_hash của Account;
cards chỉ gồm thẻ ACTIVE/chưa hết hạn có Cardholder ACTIVE. Snapshot không có
endpoint Account REST, không được log, chỉ gửi qua transport thiết bị đã xác
thực/ACL và bảo vệ phù hợp. Bảo mật bộ nhớ/cache tại ESP32 thuộc firmware.

UID là định danh thẻ, không tự chống clone; không coi helper này là bằng chứng
đã hoàn thành xác thực RFID/PIN offline hoặc phần cứng của người 3.

## Database

Sử dụng các bảng cardholders/rfid_credentials/system_pins hiện có; không sửa
V1–V3. Migration V4 chỉ thêm `accounts.auth_version` và `revoked_access_tokens`
cho chính sách phiên. Các FK cùng Account/Door và UNIQUE UID theo cửa giữ nguyên.
