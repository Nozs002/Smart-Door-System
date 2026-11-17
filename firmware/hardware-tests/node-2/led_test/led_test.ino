// Định nghĩa chân nối với LED
#define LED_PIN 2

void setup() {
  Serial.begin(115200);
  Serial.println("Bat dau test LED...");

  // Thiết lập chân GPIO 2 làm chân xuất tín hiệu (OUTPUT)
  pinMode(LED_PIN, OUTPUT);
}

void loop() {
  // Bật đèn LED (Cấp điện áp 3.3V)
  Serial.println("LED: Sang");
  digitalWrite(LED_PIN, HIGH);
  delay(1000); // Sáng trong 1 giây (1000 ms)

  // Tắt đèn LED (Kéo điện áp về 0V)
  Serial.println("LED: Tat");
  digitalWrite(LED_PIN, LOW);
  delay(1000); // Tắt trong 1 giây
}