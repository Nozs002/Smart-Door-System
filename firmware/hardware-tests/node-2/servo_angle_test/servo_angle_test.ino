#include <ESP32Servo.h>

// Node 2: SG90 signal = GPIO18. Nguon 5V rieng, noi chung GND ESP32.
// Thu khi thao tay servo khoi co cau chot. Servo ve 90 do khi khoi dong.
// Thu vien: ESP32Servo. Serial Monitor: 115200 baud, Newline.
// 180: mở cửa
// 90: đóng cửa
Servo servo;
String input;
bool invalidInput = false;

void applyAngle() {
  if (input.isEmpty() && !invalidInput) return;

  if (invalidInput) {
    Serial.println("Chi nhap so nguyen 0-180.");
  } else {
    int angle = input.toInt();
    if (angle > 180) {
      Serial.println("Goc phai nam trong 0-180.");
    } else {
      servo.write(angle);
      Serial.print("Da dat goc: ");
      Serial.println(angle);
    }
  }

  input = "";
  invalidInput = false;
}

void setup() {
  Serial.begin(115200);
  servo.setPeriodHertz(50);
  servo.attach(18, 500, 2400);
  servo.write(90);
  Serial.println("Vi tri ban dau: 90 do.");
  Serial.println("Nhap goc 0-180 roi nhan Enter (Newline).");
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      applyAngle();
    } else if (c < '0' || c > '9' || input.length() >= 3) {
      invalidInput = true;
    } else if (!invalidInput) {
      input += c;
    }
  }
}
