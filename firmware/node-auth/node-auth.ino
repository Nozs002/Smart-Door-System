// Smart Door Node 1: online RFID/PIN authentication.
// Configuration and upload instructions: README.md in this folder.
#include "AuthProtocolTests.h"

// Uncomment only to run protocol tests without network or peripherals.
// #define NODE_AUTH_PROTOCOL_SELF_TEST
#ifdef NODE_AUTH_PROTOCOL_SELF_TEST
void setup() {
  Serial.begin(115200);
  delay(1500);
  runAuthProtocolTests();
}
void loop() { delay(100); }
#else
#include "NodeAuthApp.h"
#endif
