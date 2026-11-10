#include <Wire.h>
#include <LiquidCrystal_I2C.h>

LiquidCrystal_I2C lcd(0x27, 16, 2);  

void setup() {
  // Khởi tạo màn hình LCD
  lcd.init();       
  
  // Bật đèn nền LCD
  lcd.backlight();  
  
  // In dòng chữ đầu tiên ở hàng 1 (Cột 0, Hàng 0)
  lcd.setCursor(0, 0);
  lcd.print("Test LCD ESP32");
  
  // In dòng chữ thứ hai ở hàng 2 (Cột 0, Hàng 1)
  lcd.setCursor(0, 1);
  lcd.print("He thong san sang!");
  
  delay(2000); // Đợi 2 giây
  lcd.clear(); // Xóa màn hình
}

void loop() {
  // Hiển thị thời gian (giây) đếm ngược từ lúc bật máy
  lcd.setCursor(0, 0);
  lcd.print("Thoi gian chay:");
  
  lcd.setCursor(0, 1);
  lcd.print(millis() / 1000);
  lcd.print(" giay");
  
  delay(1000);
}