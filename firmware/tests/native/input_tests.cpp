#include <cassert>
#include <cstring>
#include <iostream>
#include "../../node-auth/PinInput.h"
#include "../../node-control/RelockOnDoorClose.h"
extern "C" {
#include "../../node-auth/src/bcrypt/crypt_blowfish.h"
}

int main() {
  using namespace PinInput;
  char pin[LENGTH + 1] = {};
  size_t length = 0;
  assert(press('0', pin, length) == Result::NONE);
  assert(press('1', pin, length) == Result::NONE);
  assert(press('#', pin, length) == Result::NONE && length == 2);
  assert(press('2', pin, length) == Result::NONE);
  assert(press('3', pin, length) == Result::READY);
  assert(std::strcmp(pin, "0123") == 0);
  assert(press('9', pin, length) == Result::NONE && length == LENGTH && pin[LENGTH] == 0);
  press('*', pin, length);
  assert(length == 0);
  for (char c : pin) assert(c == 0);
  assert(press('4', pin, length) == Result::NONE && length == 1);

  ControlPolicy::RelockOnDoorClose relock;
  assert(!relock.update(true, true));
  relock.arm();
  assert(!relock.update(true, true));
  assert(!relock.update(false, false)); // ignore a candidate that has not settled
  assert(!relock.update(true, true));
  for (int sample = 0; sample < 1000; ++sample) assert(!relock.update(true, false));
  assert(!relock.update(false, true)); // closing has not settled yet
  assert(relock.update(true, true));
  relock.cancel();
  assert(!relock.update(true, true));
  relock.arm();
  assert(!relock.update(true, false));
  relock.cancel(); // fire or hold-open mode cancels the armed unlock
  assert(!relock.update(true, true));
  relock.arm();
  assert(!relock.update(true, true)); // new unlock requires its own opening

  const char* hash = "$2a$04$abcdefghijklmnopqrstuufUlS0EEB.H3ewsjUNPrWW5qETJaYIty";
  char output[64] = {};
  assert(_crypt_blowfish_rn("12345678", hash, output, sizeof(output)));
  assert(std::strcmp(output, hash) == 0);
  assert(_crypt_blowfish_rn("12345679", hash, output, sizeof(output)));
  assert(std::strcmp(output, hash) != 0);
  assert(!_crypt_blowfish_rn("12345678", "$2a$xx$invalid", output, sizeof(output)));
  std::cout << "PASS four-digit keypad submission, MC38 relock, and independent BCrypt vectors\n";
}
