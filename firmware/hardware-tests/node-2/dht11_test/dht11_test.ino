#include "DHT.h"

// Định nghĩa chân DATA của DHT11 nối với GPIO 4 của ESP32
#define DHTPIN 4     

// Khai báo loại cảm biến đang dùng là DHT11
#define DHTTYPE DHT11   

// Khởi tạo đối tượng dht
DHT dht(DHTPIN, DHTTYPE);

void setup() {
  Serial.begin(115200);
  Serial.println("Bat dau test cam bien DHT11...");
  
  // Khởi động cảm biến
  dht.begin();
}

void loop() {
  // Chờ 2 giây giữa các lần đọc (DHT11 là cảm biến chậm, tốc độ đọc tối đa là 1 lần mỗi giây)
  delay(2000);

  // Đọc độ ẩm (phần trăm)
  float h = dht.readHumidity();
  // Đọc nhiệt độ (độ C)
  float t = dht.readTemperature();
  // Đọc nhiệt độ (độ F) nếu cần
  float f = dht.readTemperature(true);

  // Kiểm tra xem quá trình đọc có bị lỗi không (isnan = is not a number)
  if (isnan(h) || isnan(t) || isnan(f)) {
    Serial.println("Loi: Khong the doc du lieu tu DHT11!");
    return;
  }

  // Tính toán chỉ số cảm nhận nhiệt (Heat Index) theo độ C
  float hic = dht.computeHeatIndex(t, h, false);

  // In kết quả ra Serial Monitor
  Serial.print("Do am: ");
  Serial.print(h);
  Serial.print(" %\t"); // \t là khoảng trắng tab
  
  Serial.print("Nhiet do: ");
  Serial.print(t);
  Serial.print(" *C\t");
  
  Serial.print("Nhiet do cam nhan: ");
  Serial.print(hic);
  Serial.println(" *C");
}