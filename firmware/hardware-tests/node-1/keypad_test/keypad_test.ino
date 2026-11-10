#include <Keypad.h>

// Định nghĩa số hàng và số cột của keypad
const byte ROWS = 4; 
const byte COLS = 3; 

// Khai báo sơ đồ vị trí các phím trên Keypad 4x3
char keys[ROWS][COLS] = {
  {'1','2','3'},
  {'4','5','6'},
  {'7','8','9'},
  {'*','0','#'}
};

// Khai báo các chân GPIO nối với các Hàng (R1, R2, R3, R4)
byte rowPins[ROWS] = {13, 14, 27, 26}; 

// Khai báo các chân GPIO nối với các Cột (C1, C2, C3)
byte colPins[COLS] = {25, 33, 32}; 

// Khởi tạo đối tượng keypad
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

void setup() {
  // Khởi tạo Serial Monitor với baudrate 115200
  Serial.begin(115200);
  Serial.println("He thong san sang!");
  Serial.println("Moi ban nhap thu ma PIN tren Keypad...");
  Serial.println();
}

void loop() {
  // Đọc trạng thái của phím được nhấn
  char key = keypad.getKey();

  // Nếu có phím được nhấn, in ra màn hình Serial
  if (key) {
    Serial.print("Phim duoc nhan: ");
    Serial.println(key);
  }
}