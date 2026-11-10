// Smart Door Node 2: control, sensors and local alerts.
// Setup and verification: README.md in this folder.
#include "ControlTests.h"
// Uncomment only to test policy without GPIO, Wi-Fi or NVS writes.
// #define NODE_CONTROL_SELF_TEST
#ifdef NODE_CONTROL_SELF_TEST
void setup() { Serial.begin(115200); delay(1500); runControlTests(); }
void loop() { delay(100); }
#else
#include "NodeControlApp.h"
#endif
