// Định nghĩa chân nối với cảm biến cửa MC38
#define DOOR_SENSOR_PIN 16 

void setup() {
  Serial.begin(115200);
  Serial.println("Bat dau test cam bien cua MC38...");
  
  // Thiết lập chân GPIO 16 là INPUT_PULLUP
  // Nếu cửa đóng (nối GND) -> Đọc giá trị LOW (0)
  // Nếu cửa mở (hở mạch) -> Điện trở nội bộ kéo lên HIGH (1)
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP);
}

void loop() {
  // Đọc trạng thái của cảm biến
  int doorState = digitalRead(DOOR_SENSOR_PIN);

  // Kiểm tra và in ra Serial Monitor
  if (doorState == HIGH) {
    Serial.println("Trang thai: Cua DONG (Nam cham dang o gan)");
  } else {
    Serial.println("Trang thai: Cua MO (Nam cham da bi tach ra) !!!");
  }
  
  // Đợi 0.5 giây trước khi đọc lần tiếp theo để tránh trôi màn hình quá nhanh
  delay(500); 
}