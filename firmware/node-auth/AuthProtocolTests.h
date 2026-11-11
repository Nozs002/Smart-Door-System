#pragma once
#include "AuthProtocol.h"

// Optional on-device contract tests. No Wi-Fi, RFID, LCD or servo access.
// Build with -DNODE_AUTH_PROTOCOL_SELF_TEST and read Serial at 115200 baud.
inline bool runAuthProtocolTests() {
  setenv("TZ", "UTC0", 1);
  tzset();
  unsigned int passed = 0, failed = 0;
  auto check = [&](bool condition, const char* name) {
    if (condition) ++passed;
    else { ++failed; Serial.print("FAIL: "); Serial.println(name); }
  };
  check(AuthProtocol::uuid("b6f294a0-2934-4d48-9e7d-54a924e84848"), "canonical UUID");
  check(!AuthProtocol::uuid("b6f294a029344d489e7d54a924e84848"), "UUID needs separators");
  check(!AuthProtocol::uuid("B6f294a0-2934-4d48-9e7d-54a924e84848"), "UUID must be lowercase");
  check(AuthProtocol::topicCode("TEST-MAIN-N1"), "valid topic identity");
  check(!AuthProtocol::topicCode("door/+/node"), "reject topic injection");
  check(AuthProtocol::parseUtc("2026-01-01T00:00:00Z") == 1767225600000LL, "UTC epoch");
  check(AuthProtocol::parseUtc("2026-01-01T00:00:00.123456789Z") == 1767225600123LL, "fractional UTC");
  check(AuthProtocol::parseUtc("2026-02-30T00:00:00Z") == 0, "impossible date");
  check(AuthProtocol::parseUtc("2026-01-01T00:00:00+07:00") == 0, "non-UTC offset");
  check(AuthProtocol::parseUtc("2026-1-01T00:00:00Z") == 0, "non-canonical date");

  JsonDocument config;
  deserializeJson(config, "{\"schemaVersion\":1,\"version\":3,\"maxFailedAttempts\":3,"
    "\"lockoutSeconds\":60,\"temperatureThreshold\":50,\"openTimeoutSeconds\":40,"
    "\"operatingMode\":\"SECURITY\",\"fireAlarmEnabled\":true,\"doorOpenAlertEnabled\":true}");
  check(AuthProtocol::validConfig(config), "valid configuration");
  config["maxFailedAttempts"] = 0;
  check(!AuthProtocol::validConfig(config), "reject invalid attempts");
  config["maxFailedAttempts"] = 3; config["fireAlarmEnabled"] = "true";
  check(!AuthProtocol::validConfig(config), "boolean type required");
  config["fireAlarmEnabled"] = true; config["openTimeoutSeconds"] = nullptr;
  check(AuthProtocol::validConfig(config), "nullable timeout allowed by contract");
  config.remove("openTimeoutSeconds");
  check(!AuthProtocol::validConfig(config), "missing timeout is not explicit null");

  JsonDocument result;
  result["schemaVersion"] = 1;
  result["eventId"] = "b6f294a0-2934-4d48-9e7d-54a924e84848";
  result["result"] = "GRANTED"; result["reason"] = "QUEUED";
  result["expiresAt"] = "2026-01-01T00:00:15Z";
  String id = result["eventId"].as<String>();
  constexpr int64_t now = 1767225600000LL;
  check(AuthProtocol::resultMatches(result, id, false, now, now + 15000), "matching result");
  check(!AuthProtocol::resultMatches(result, id, true, now, now + 15000), "reject retained grant");
  check(!AuthProtocol::resultMatches(result, "other", false, now, now + 15000), "reject unrelated attempt");
  check(!AuthProtocol::resultMatches(result, id, false, now + 15000, now + 15000), "reject at deadline");
  result["expiresAt"] = "2026-01-01T00:00:16Z";
  check(!AuthProtocol::resultMatches(result, id, false, now, now + 15000), "reject extended deadline");
  result["expiresAt"] = "2026-01-01T00:00:15Z"; result["result"] = "SUCCESS";
  check(!AuthProtocol::resultMatches(result, id, false, now, now + 15000), "reject unknown result");
  result["result"] = "DENIED";
  check(AuthProtocol::resultMatches(result, id, false, now, now + 15000), "accept matching denial");

  AuthProtocol::Signer signer;
  check(!signer.begin(id.c_str(), "invalid-key"), "reject invalid signing key");
  Serial.print("Protocol tests passed: "); Serial.print(passed);
  Serial.print(", failed: "); Serial.println(failed);
  return failed == 0;
}
