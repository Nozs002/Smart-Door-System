# Smart Access Control - Firmware

## Kiến trúc Thư mục (Arduino IDE)
- `shared/`: Các file header/cấu hình dùng chung (Config, Network, MQTT Topics).
- `node-auth/`: Sketch cho Node 1 (Xác thực đầu vào). Mở file `node-auth.ino` bằng Arduino IDE để nạp.
- `node-control/`: Sketch cho Node 2 (Cảm biến, Servo). Mở file `node-control.ino` bằng Arduino IDE để nạp.

## Yêu cầu Thư viện (Cài qua Library Manager)
1. `MQTT` (Joël Gähwiler)
2. `ArduinoJson` (Benoit Blanchon) - **Mới bổ sung để đóng gói Payload**
3. `NTPClient`
4. `MFRC522`, `Keypad`, `LiquidCrystal I2C`, `ESP32Servo`, `DHT sensor library`

## Setup ban đầu
1. Copy `shared/config.h.example` thành `shared/config.h`.
2. Điền IP Server và Username/Password MQTT sinh ra từ file `setup-mqtt.py`.