# Code test linh kiện Arduino

Đặt các sketch đã chạy thử vào thư mục tương ứng:

- `node-1/`: RC522, keypad, LCD hoặc sketch kết hợp Node 1.
- `node-2/`: SG90, DHT11, MC-38, buzzer, LED hoặc sketch kết hợp Node 2.

Mỗi sketch đặt trong một thư mục có tên trùng với file `.ino`, ví dụ:

```text
node-1/rc522_test/rc522_test.ino
node-2/servo_test/servo_test.ino
```

Giữ các file `.h` và `.cpp` đi kèm trong cùng thư mục sketch nếu có.
Ghi tên thư viện cần dùng và GPIO trong phần chú thích đầu sketch.
Không đưa mật khẩu Wi-Fi, mật khẩu MQTT hoặc khóa thiết bị vào code test.

## Keypad khi hỏng hàng `* 0 #`

`node-1/keypad_test/keypad_test.ino` dùng cùng logic nhập PIN với firmware Node 1:
bấm ngắn 1–9 nhập số; giữ 1 để xóa, giữ 2 để nhập 0, giữ 3 để gửi (0,9 giây).
Mở Serial Monitor 115200 để xem PIN được che bằng dấu sao và kết quả thao tác.
Giữ nguyên cấu trúc repo vì sketch dùng `node-auth/PinInput.h` qua đường dẫn tương đối.

Sketch test chỉ kiểm tra nhập liệu, không gửi Backend/mở cửa. Để demo thật,
nạp `firmware/node-auth/node-auth.ino` với file cấu hình local của đúng Node 1;
bản đó cũng có các phím dự phòng. Ba phím cuối chung R4/GPIO26, nên cần kiểm
tra lại dây/jack khi đã ngắt nguồn; phím dự phòng không sửa lỗi phần cứng.
