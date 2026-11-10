#include <SPI.h>
#include <MFRC522.h>

// Định nghĩa các chân RST và SDA(SS) theo kết nối của bạn
#define RST_PIN 15
#define SS_PIN  5

// Tạo đối tượng MFRC522
MFRC522 mfrc522(SS_PIN, RST_PIN); 

void setup() {
  // Khởi tạo giao tiếp Serial với baudrate 115200
  Serial.begin(115200); 
  
  // Khởi tạo chuẩn giao tiếp SPI
  SPI.begin(); 
  
  // Khởi tạo module RFID RC522
  mfrc522.PCD_Init(); 
  
  Serial.println("He thong da san sang.");
  Serial.println("Vui long dua the tu (RFID) vao gan module de doc...");
  Serial.println();
}

void loop() {
  // Kiểm tra xem có thẻ mới nào được đưa vào không
  if ( ! mfrc522.PICC_IsNewCardPresent()) {
    return;
  }

  // Đọc dữ liệu của thẻ
  if ( ! mfrc522.PICC_ReadCardSerial()) {
    return;
  }

  // In ra Serial Monitor UID (Mã ID) của thẻ
  Serial.print("UID cua the la: ");
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    // In thêm số 0 ở trước nếu giá trị HEX nhỏ hơn 0x10 để định dạng đẹp hơn
    Serial.print(mfrc522.uid.uidByte[i] < 0x10 ? " 0" : " ");
    Serial.print(mfrc522.uid.uidByte[i], HEX);
  }
  Serial.println();

  // Dừng đọc thẻ hiện tại (để tránh việc in liên tục 1 mã thẻ nếu bạn giữ nguyên thẻ ở đó)
  mfrc522.PICC_HaltA();
}