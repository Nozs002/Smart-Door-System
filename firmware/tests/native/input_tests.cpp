#include <cassert>
#include <cstring>
#include <iostream>
#include "../../node-auth/PinInput.h"
extern "C" {
#include "../../node-auth/src/bcrypt/crypt_blowfish.h"
}

int main() {
  using namespace PinInput;
  Gestures gestures;
  char pin[9] = {}; size_t length = 0;
  auto shortPress = [&](char key) {
    char logical = gestures.event(key, KeyEvent::PRESSED);
    if (logical) press(logical, pin, length);
    logical = gestures.event(key, KeyEvent::RELEASED);
    return logical ? press(logical, pin, length) : Result::NONE;
  };
  auto hold = [&](char key) {
    assert(gestures.event(key, KeyEvent::PRESSED) == 0);
    char logical = gestures.event(key, KeyEvent::HELD);
    auto result = press(logical, pin, length);
    assert(gestures.event(key, KeyEvent::HELD) == 0);
    assert(gestures.event(key, KeyEvent::RELEASED) == 0);
    return result;
  };
  // Entire PIN 1203 is enterable without the physically broken */0/# row.
  shortPress('1'); shortPress('2'); hold('2'); shortPress('3');
  assert(std::strcmp(pin, "1203") == 0);
  assert(hold('3') == Result::READY);
  assert(std::strcmp(pin, "1203") == 0); // submit must not append a 3
  assert(hold('1') == Result::CLEARED);
  for (char c : pin) assert(c == 0);
  for (char c : "12345678") if (c) assert(shortPress(c) == Result::NONE);
  assert(length == 8 && std::strcmp(pin, "12345678") == 0);
  assert(press('9', pin, length) == Result::TOO_LONG);
  assert(length == 8 && pin[8] == 0);
  assert(hold('3') == Result::READY);
  hold('1'); shortPress('4');
  assert(hold('3') == Result::TOO_SHORT);
  gestures.event('3', KeyEvent::PRESSED); gestures.reset();
  assert(gestures.event('3', KeyEvent::HELD) == 0);
  assert(gestures.event('3', KeyEvent::RELEASED) == 0);
  assert(gestures.event('#', KeyEvent::PRESSED) == '#');
  assert(gestures.event('*', KeyEvent::PRESSED) == '*');
  assert(gestures.event('0', KeyEvent::PRESSED) == '0');

  const char* hash = "$2a$04$abcdefghijklmnopqrstuufUlS0EEB.H3ewsjUNPrWW5qETJaYIty";
  char output[64] = {};
  assert(_crypt_blowfish_rn("12345678", hash, output, sizeof(output)));
  assert(std::strcmp(output, hash) == 0);
  assert(_crypt_blowfish_rn("12345679", hash, output, sizeof(output)));
  assert(std::strcmp(output, hash) != 0);
  assert(!_crypt_blowfish_rn("12345678", "$2a$xx$invalid", output, sizeof(output)));
  std::cout << "PASS keypad short/hold/0/clear/submit/limits/reset and independent BCrypt vectors\n";
}
