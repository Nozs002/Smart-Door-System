# Smart Access Control - Firmware

Hai sketch hiện tại dùng ArduinoMqttClient và file cấu hình local riêng:
[Node 1 online](node-auth/README.md), [Node 2 điều khiển/cảm biến](node-control/README.md).

## Kiến trúc Thư mục (Arduino IDE)
- `shared/DeviceProtocol.h`: UUID, UTC, kiểm tra cấu hình và signed envelope dùng chung.
- `node-auth/`: Sketch cho Node 1 (Xác thực đầu vào). Mở file `node-auth.ino` bằng Arduino IDE để nạp.
- `node-control/`: Sketch cho Node 2 (Cảm biến, Servo). Mở file `node-control.ino` bằng Arduino IDE để nạp.

## Yêu cầu Thư viện (Cài qua Library Manager)
1. `ArduinoMqttClient` và `ArduinoJson` phiên bản 7.
2. Node 1: `MFRC522`, `Keypad`, `LiquidCrystal I2C`.
3. Node 2: `ESP32Servo`, `DHT sensor library`, `Adafruit Unified Sensor`.
4. NTP, WiFi, Preferences và mbedTLS có trong ESP32 Arduino core.

## Setup ban đầu
1. Copy file `*_config.h.example` trong từng module thành `*_config.local.h`.
2. Điền IP Server và danh tính MQTT riêng từ file cấp khóa của đúng node.
3. Không commit secret. File local được ignore; các helper/config legacy còn
   trong shared không được hai sketch hiện tại dùng để kết nối.

## Cấu hình kiểm thử Wi-Fi/MQTT

Với `shared/network/wifi_manager.cpp`, sao chép
`shared/network/wifi_config.h.example` thành `shared/network/wifi_config.local.h`
rồi điền cấu hình mạng và MQTT. File `.local.h` này được Git bỏ qua.
Không đưa thông tin đăng nhập thật vào file mẫu.
