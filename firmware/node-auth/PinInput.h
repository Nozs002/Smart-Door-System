#pragma once
#include <stddef.h>

// Shared by the keypad loop and tests. Never submit a prefix automatically.
namespace PinInput {
enum class KeyEvent { PRESSED, HELD, RELEASED };

// Fallback for a broken bottom row: short 1/2/3 remain digits, hold = */0/#.
// Defer these digits until release so a long press never appends a stray digit.
class Gestures {
 public:
  void reset() { for (size_t i = 0; i < 3; ++i) { down[i] = false; consumed[i] = false; } }
  char event(char key, KeyEvent state) {
    if (key < '1' || key > '3') return state == KeyEvent::PRESSED ? key : 0;
    size_t index = key - '1';
    if (state == KeyEvent::PRESSED) { down[index] = true; consumed[index] = false; return 0; }
    if (!down[index]) return 0;
    if (state == KeyEvent::HELD) {
      if (consumed[index]) return 0;
      consumed[index] = true;
      return index == 0 ? '*' : index == 1 ? '0' : '#';
    }
    down[index] = false;
    return consumed[index] ? 0 : key;
  }
 private:
  bool down[3] = {}, consumed[3] = {};
};
enum class Result { NONE, READY, TOO_SHORT, TOO_LONG, CLEARED };
inline void clear(char (&buffer)[9], size_t& length) {
  volatile char* bytes = buffer;
  for (size_t i = 0; i < sizeof(buffer); ++i) bytes[i] = 0;
  length = 0;
}
inline Result press(char key, char (&buffer)[9], size_t& length) {
  if (key == '*') { clear(buffer, length); return Result::CLEARED; }
  if (key == '#') return length >= 4 && length <= 8 ? Result::READY : Result::TOO_SHORT;
  if (key < '0' || key > '9') return Result::NONE;
  if (length >= 8) return Result::TOO_LONG;
  buffer[length++] = key; buffer[length] = '\0';
  return Result::NONE;
}
}
