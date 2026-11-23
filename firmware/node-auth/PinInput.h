#pragma once
#include <stddef.h>

namespace PinInput {
constexpr size_t LENGTH = 4;
enum class Result { NONE, READY };

inline void clear(char (&buffer)[LENGTH + 1], size_t& length) {
  volatile char* bytes = buffer;
  for (size_t i = 0; i < sizeof(buffer); ++i) bytes[i] = 0;
  length = 0;
}
inline Result press(char key, char (&buffer)[LENGTH + 1], size_t& length) {
  if (key == '*') {
    clear(buffer, length);
    return Result::NONE;
  }
  if (key < '0' || key > '9' || length >= LENGTH) return Result::NONE;
  buffer[length++] = key;
  buffer[length] = '\0';
  return length == LENGTH ? Result::READY : Result::NONE;
}
}
