#include <Keypad.h>
#include "../../../node-auth/PinInput.h"

// Keypad 4x3, ESP32. Library: Keypad 3.1.1.
// Keep the repository layout: input policy is shared with the real Node 1.
// Input test only: no Wi-Fi, Backend request or door actuation.
char keys[4][3] = {
  {'1','2','3'}, {'4','5','6'}, {'7','8','9'}, {'*','0','#'}
};
byte rowPins[4] = {13, 14, 27, 26};
byte colPins[3] = {25, 33, 32};
Keypad keypad(makeKeymap(keys), rowPins, colPins, 4, 3);
PinInput::Gestures gestures;
char pin[9] = {};
size_t pinLength = 0;
unsigned long lastInputAt = 0;

void showMaskedPin() {
  Serial.print("PIN: ");
  for (size_t i = 0; i < pinLength; ++i) Serial.print('*');
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  keypad.setDebounceTime(30);
  keypad.setHoldTime(900);
  Serial.println("TEST NHAP PIN - khong gui Backend/khong mo cua");
  Serial.println("Bam ngan 1-9: nhap so. Giu 1: xoa; giu 2: so 0; giu 3: gui.");
  Serial.println("Giu khoang 0.9 giay, tha phim sau moi thao tac. PIN 4-8 so.");
  Serial.println("Phim * 0 # van dung duoc neu hang R4/GPIO26 hoat dong.");
}

void loop() {
  if (pinLength && millis() - lastInputAt >= 20000) {
    PinInput::clear(pin, pinLength);
    gestures.reset();
    Serial.println("Da xoa PIN do khong nhap trong 20 giay.");
  }
  if (!keypad.getKeys()) return;
  for (size_t i = 0; i < LIST_MAX; ++i) {
    if (!keypad.key[i].stateChanged) continue;
    char logical = 0;
    switch (keypad.key[i].kstate) {
      case PRESSED: logical = gestures.event(keypad.key[i].kchar, PinInput::KeyEvent::PRESSED); break;
      case HOLD: logical = gestures.event(keypad.key[i].kchar, PinInput::KeyEvent::HELD); break;
      case RELEASED: logical = gestures.event(keypad.key[i].kchar, PinInput::KeyEvent::RELEASED); break;
      default: break;
    }
    if (!logical) continue;
    lastInputAt = millis();
    switch (PinInput::press(logical, pin, pinLength)) {
      case PinInput::Result::READY:
        Serial.println("DA NHAN THAO TAC GUI PIN THU (khong gui Backend).");
        PinInput::clear(pin, pinLength);
        gestures.reset();
        return;
      case PinInput::Result::CLEARED: Serial.println("Da xoa PIN."); break;
      case PinInput::Result::TOO_SHORT: Serial.println("Can nhap it nhat 4 so truoc khi gui."); break;
      case PinInput::Result::TOO_LONG: Serial.println("Toi da 8 so; giu 1 de xoa, giu 3 de gui."); break;
      default: showMaskedPin(); break;
    }
  }
}
