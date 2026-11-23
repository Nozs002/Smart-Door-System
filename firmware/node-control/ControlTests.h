#pragma once
#include "ControlState.h"
#include "RelockOnDoorClose.h"
#include <limits.h>

inline bool runControlTests() {
  setenv("TZ", "UTC0", 1); tzset();
  unsigned int passed = 0, failed = 0;
  auto check = [&](bool condition, const char* name) {
    if (condition) ++passed;
    else { ++failed; Serial.print("FAIL: "); Serial.println(name); }
  };
  check(ControlPolicy::fire(true, true, 51, 50, false), "fire above threshold");
  check(!ControlPolicy::fire(true, true, 50, 50, false), "threshold must be exceeded");
  check(!ControlPolicy::fire(false, true, 80, 50, true), "fire switch off");
  check(ControlPolicy::fire(true, false, NAN, 50, true), "sensor failure cannot clear active fire");
  check(!ControlPolicy::fire(true, false, NAN, 50, false), "invalid sensor not a temperature alarm");
  check(!ControlPolicy::fire(true, true, 49, 50, true), "valid cool reading clears condition");
  check(ControlPolicy::door(true, true, true, 5000, 5), "door threshold reached");
  check(!ControlPolicy::door(true, true, true, 4999, 5), "door before threshold");
  check(!ControlPolicy::door(false, true, true, 5000, 5), "door alert switch off");
  check(!ControlPolicy::door(true, false, true, 5000, 5), "unknown door");
  check(!ControlPolicy::door(true, true, false, 5000, 5), "closed door");
  check(!ControlPolicy::door(true, true, true, 5000, 0), "missing timeout");
  check(!ControlPolicy::door(true, true, true, 9000000, 3601), "invalid timeout");
  unsigned long opened = ULONG_MAX - 6000UL, now = 50UL;
  check(ControlPolicy::door(true, true, true, now - opened, 5), "millis rollover");
  check(DeviceProtocol::parseUtc("2026-01-01T00:00:15.123456789Z") == 1767225615123LL, "fractional expiry");
  check(DeviceProtocol::parseUtc("2026-02-30T00:00:00Z") == 0, "invalid expiry date");
  check(!DeviceProtocol::topicCode("node/+/bad"), "topic injection");
  check(DeviceProtocol::uuid("b6f294a0-2934-4d48-9e7d-54a924e84848"), "full UUID");
  check(!DeviceProtocol::uuid("9e7d54a924e84848"), "truncated UUID rejected");
  ControlPolicy::RelockOnDoorClose relock;
  ControlPolicy::SecurityMode mode;
  check(mode.next(true, false) == ControlPolicy::ModeEffect::NONE, "no close on first secure boot");
  mode.applied(true);
  check(mode.next(false, false) == ControlPolicy::ModeEffect::HOLD_OPEN, "off holds open");
  mode.applied(false);
  check(mode.next(false, false) == ControlPolicy::ModeEffect::HOLD_OPEN, "restored off keeps holding open");
  check(mode.next(true, false) == ControlPolicy::ModeEffect::CLOSE, "on closes after hold open");
  check(mode.next(true, true) == ControlPolicy::ModeEffect::NONE, "fire blocks mode close");
  mode.applied(true);
  check(mode.next(true, false) == ControlPolicy::ModeEffect::NONE, "do not close automatically after fire clears");
  check(!relock.update(true, true), "no close on boot");
  relock.arm();
  check(!relock.update(true, true), "closed signal at unlock does not relock");
  check(!relock.update(true, false), "open door stays unlocked");
  check(!relock.update(false, true), "unstable sensor cannot relock");
  check(relock.update(true, true), "MC38 closing relocks after opening");
  relock.cancel();
  check(!relock.update(true, true), "cannot relock twice");
  Serial.print("Control tests passed: "); Serial.print(passed);
  Serial.print(", failed: "); Serial.println(failed);
  return failed == 0;
}
