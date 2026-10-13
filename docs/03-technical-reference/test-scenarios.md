# Test Scenarios

Các kịch bản dưới đây là tiêu chí mục tiêu theo [quyết định sở hữu cửa và vòng
đời thiết bị](../01-requirements/ownership-device-lifecycle-decisions.md).
Chưa có kết quả chạy; chỉ đánh dấu đạt khi có bằng chứng kiểm thử.

| Mã | Kịch bản | Kết quả cần đạt |
| --- | --- | --- |
| OWN-01 | Account A sở hữu hai cửa, B sở hữu một cửa | Mỗi Account chỉ thấy danh sách cửa của mình; không hardcode ID. |
| OWN-02 | B dùng ID cửa A để đọc/sửa cấu hình, PIN, RFID, log, cảnh báo hoặc mở cửa | Backend từ chối trước truy cập dữ liệu và phát lệnh. |
| CARD-01 | A cấp thẻ cho Cardholder của B tại cửa A | Backend từ chối; Cardholder không có quyền đăng nhập App. |
| CARD-02 | Cùng UID được cấp tại hai cửa, thu hồi tại một cửa | Thu hồi không thay đổi quyền của bản đăng ký tại cửa còn lại. |
| CLAIM-01 | Hai Account đồng thời claim cùng bộ Door/Node | Chỉ một Account nhận sở hữu; không có dữ liệu liên kết một phần. |
| CLAIM-02 | Mã sai, hết hạn, đã dùng hoặc chỉ cung cấp deviceCode | Từ chối nhận sở hữu. |
| CLAIM-03 | Account gửi lại request claim đã thành công | Trả kết quả nhất quán, không tạo cửa mới, đổi chủ hoặc mở khóa. |
| LIFE-01 | Node 1 mất heartbeat, Node 2 vẫn gửi heartbeat | Node 1 offline, Node 2 online; lifecycle và ownership không thay đổi. |
| LIFE-02 | Node chưa từng gửi heartbeat | Kết nối UNKNOWN; không suy ra online hoặc cửa vật lý đóng/mở. |
| LIFE-03 | Node bị vô hiệu hóa/ngừng sử dụng vẫn gửi MQTT | Từ chối nghiệp vụ dù thiết bị đang online. |
| LIFE-04 | Thay Node của một Door | Giữ lịch sử và định danh Node cũ, thu hồi quyền cũ, không có hai Node hoạt động cùng vai trò. |
| ROUTE-01 | Node cửa A giả ID cửa B trong topic/payload | ACL/Backend từ chối theo danh tính và liên kết đã provision. |
| ROUTE-02 | Gửi lệnh tới cửa mô phỏng, phát cảnh báo tại cửa thật | Lệnh chỉ đến đúng Node 2; cảnh báo chỉ đến đúng chủ cửa. |
| SCOPE-01 | Rà soát thiết kế UI/API/schema mới | Không có module hoặc quyền truy cập vân tay, không có Admin nền tảng. |

Kiểm chứng nhiều cửa dùng một bộ phần cứng thật và bộ mô phỏng MQTT có định
danh riêng. Seed nhiều dòng Door trong database chưa chứng minh định tuyến
đồng thời nhiều cửa. Timeout heartbeat, transition lifecycle và contract chi
tiết cần được đặc tả trước khi hiện thực các kiểm thử tương ứng.
