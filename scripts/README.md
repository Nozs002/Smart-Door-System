# Scripts

Chạy các lệnh dưới đây từ root repository. Docker Desktop phải hoạt động khi
script cần MySQL/MQTT. Các file secret, backup và báo cáo test nằm trong `.local/`
đã được `.gitignore` loại khỏi Git.

| Script | Mục đích | Lệnh |
| --- | --- | --- |
| `db-check.py` | Audit chỉ đọc; tùy chọn ghi backup trước khi nâng cấp | `python scripts/db-check.py --backup` |
| `db-check.py` | Xác nhận migration và quan hệ sau nâng cấp | `python scripts/db-check.py --expect-version 10` |
| `db-fresh-check.py` | Tạo schema riêng, chạy V1–V9, tái hiện lỗi loại thông điệp rồi kiểm tra V10 giữ dữ liệu/ràng buộc | `python scripts/db-fresh-check.py` |
| `setup-mqtt.py init` | Tạo master key còn thiếu, chuẩn bị Broker; giữ cấu hình đã có | `python scripts/setup-mqtt.py init` |
| `setup-mqtt.py issue` | Cấp/rotate credential riêng của một Node đã provision | `python scripts/setup-mqtt.py issue <deviceId>` |
| `setup-mqtt.py configure` | Xuất danh tính hiện tại, cập nhật password/ACL, restart Broker | `python scripts/setup-mqtt.py configure` |
| `person14-smoke.py` | Tạo bộ test riêng, kiểm thử API Người 1/4 | `python scripts/person14-smoke.py` |
| `person14-smoke.py --mqtt` | Thêm hai Node giả lập, ACK, cảnh báo và inbox | Xem môi trường Python bên dưới |
| `device-simulator.py` | Giữ một Node giả lập để thử thao tác từ App | `python scripts/device-simulator.py <file-credential.local.json> --duration 600` |

Môi trường Python riêng cho simulator:

```powershell
python -m venv .local/tools
.\.local\tools\Scripts\python.exe -m pip install -r scripts/simulator-requirements.txt
.\.local\tools\Scripts\python.exe scripts/person14-smoke.py --mqtt
```

Smoke test chỉ thêm Account/cửa/Node/credentials mang tên test; không xóa dữ
liệu và không đổi mật khẩu tài khoản đã có. Nó giữ fixture để mở trên Flutter;
username/password cuối cùng và đường dẫn file từng Node nằm trong
`.local/smoke/<lần-chạy>/report.local.json`. Không chia sẻ báo cáo chứa password
hoặc private device export lên Git/nhóm chat. Chỉ chạy trên môi trường local.

Simulator không điều khiển khóa. Node 1 nhận cấu hình, PIN verifier/RFID và ký
ACK; Node 2 nhận cấu hình, ký ACK và có thể phát cảnh báo thử bằng
`--temperature 80`. Cảnh báo chỉ được Backend chấp nhận sau khi Node ACTIVE.
Có thể dùng `--event-id <id>` để kiểm tra retry không tạo cảnh báo trùng.

`issue` rotate danh tính nên xóa bằng chứng ACK của danh tính cũ; sau đó phải
chạy `configure`, nạp file credential mới vào simulator/thiết bị và đồng bộ lại.
`configure` restart Broker để chấm dứt session trước đó và áp dụng ACL mới.
Không chạy `init` để rotate master key đã có; script giữ key/ACL hiện tại.

Hướng dẫn đầy đủ: [bàn giao Người 1, Người 4](../docs/05-backend/person1-person4-completion.md).

MQTT Docker từ Windows dùng cổng mặc định **1884**, cổng nội bộ Broker vẫn 1883.
Nếu đổi `MQTT_HOST_PORT`, truyền cùng giá trị bằng `--port` của simulator hoặc
`--mqtt-port` của smoke test. Có thể chẩn đoán một cửa bằng
`python scripts/db-check.py --expect-version 10 --door-id <id>`; script không in
payload chứa credentials.

Sau khi sửa migration, build image trước bằng `docker compose build backend`,
rồi chạy `python scripts/db-fresh-check.py` khi MySQL đã sẵn sàng. Script kiểm
tra trên MySQL thật: bốn loại thông điệp hợp lệ được chấp nhận; loại lạ, lease,
timestamp, số lần thử và FK sai vẫn bị chặn. Hai bản ghi hàng đợi từ V9 phải
giữ nguyên sau V10. Schema test được giữ để đối chiếu, không sửa DB ứng dụng.

Luồng RFID/PIN, lịch sử ra vào và telemetry: xem
[hướng dẫn thử trên App](../docs/06-frontend/access-monitoring.md).
`person14-smoke.py --mqtt` kiểm tra thêm mở khóa bằng PIN/RFID, ACK thực thi,
log theo correlationId, chống trùng yêu cầu, khóa tạm/còi mô phỏng và quyền đọc
sensor/history. `device-simulator.py --access-method PIN` hỏi giá trị qua input
ẩn; `--retry-access` thử gửi trùng, `--attempts` tạo các lần thử độc lập.
Không truyền thông tin xác thực vào dòng lệnh hoặc công khai `.local/`.
