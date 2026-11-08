#include <ESP32Servo.h>

// Định nghĩa chân nối với dây tín hiệu (màu Cam/Vàng) của Servo
#define SERVO_PIN 18 

// Tạo đối tượng servo
Servo myServo; 

void setup() {
  Serial.begin(115200);
  
  // Khuyến cáo của thư viện ESP32Servo để phân bổ timer PWM chính xác
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);
  
  // Khởi tạo Servo: thiết lập chân pin, độ rộng xung tối thiểu và tối đa (chuẩn thường là 500-2400 hoặc 1000-2000)
  myServo.setPeriodHertz(50); // Tần số chuẩn cho Servo là 50Hz
  myServo.attach(SERVO_PIN, 500, 2400); 
  
  Serial.println("Bat dau test Servo...");
}

void loop() {
  // Quay Servo từ 0 độ đến 180 độ
  for (int pos = 0; pos <= 180; pos += 1) { 
    myServo.write(pos);    // Xuất lệnh quay tới góc 'pos'
    delay(15);             // Đợi 15ms để servo đạt đến vị trí
  }
  
  delay(1000); // Dừng lại 1 giây ở 180 độ

  // Quay Servo từ 180 độ về lại 0 độ
  for (int pos = 180; pos >= 0; pos -= 1) { 
    myServo.write(pos);    // Xuất lệnh quay tới góc 'pos'
    delay(15);             // Đợi 15ms để servo đạt đến vị trí
  }
  
  delay(1000); // Dừng lại 1 giây ở 0 độ
}