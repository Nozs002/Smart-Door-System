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
