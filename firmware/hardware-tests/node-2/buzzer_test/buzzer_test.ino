// Định nghĩa chân tín hiệu của Buzzer
#define BUZZER_PIN 5 

void setup() {
  Serial.begin(115200);
  Serial.println("Bat dau test Còi (Buzzer)...");
  
  // Thiết lập chân GPIO 5 là chân xuất tín hiệu (OUTPUT)
  pinMode(BUZZER_PIN, OUTPUT);
  
  // Đảm bảo còi tắt khi vừa khởi động
  digitalWrite(BUZZER_PIN, LOW); 
}

void loop() {
  // Bật còi
  Serial.println("Buzzer: ON");
  digitalWrite(BUZZER_PIN, HIGH); 
  delay(1000); // Kêu trong 1 giây (1000 ms)

  // Tắt còi
  Serial.println("Buzzer: OFF");
  digitalWrite(BUZZER_PIN, LOW);  
  delay(1000); // Im lặng trong 1 giây
}