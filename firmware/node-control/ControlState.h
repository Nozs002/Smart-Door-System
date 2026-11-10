#pragma once
#include <ArduinoJson.h>
#include <Preferences.h>
#include <math.h>
#include "../shared/DeviceProtocol.h"

namespace ControlPolicy {
enum class ModeEffect { NONE, HOLD_OPEN, CLOSE };
class SecurityMode {
 public:
  ModeEffect next(bool security, bool fireActive) const {
    if (!security) return ModeEffect::HOLD_OPEN;
    if (initialized_ && !security_ && !fireActive) return ModeEffect::CLOSE;
    return ModeEffect::NONE;
  }
  void applied(bool security) { initialized_ = true; security_ = security; }
  bool ready(bool security) const { return initialized_ && security_ == security; }
 private:
  bool initialized_ = false;
  bool security_ = true;
};
class AutoClose {
 public:
  static constexpr unsigned long HOLD_MS = 10000;
  void opened(unsigned long now) { openedAt_ = now; armed_ = true; }
  void cancel() { armed_ = false; }
  bool due(unsigned long now, bool closeAllowed) const {
    return armed_ && closeAllowed && now - openedAt_ >= HOLD_MS;
  }
 private:
  unsigned long openedAt_ = 0;
  bool armed_ = false;
};
inline bool fire(bool enabled, bool validReading, float temperature, float threshold, bool active) {
  return enabled && (validReading ? temperature > threshold : active);
}
inline bool door(bool enabled, bool known, bool open, unsigned long elapsed, unsigned long seconds) {
  return enabled && known && open && seconds >= 5 && seconds <= 3600 && elapsed >= seconds * 1000UL;
}
}

// Durable, bounded outbox. Keep eventId/payload unchanged on retries/reset.
class ControlAlerts {
 public:
  bool begin(const String& identity) {
    identity_ = identity;
    ready_ = store_.begin("node2-alerts", false);
    if (!ready_) return false;
    String saved = store_.getString("outbox", "");
    if (saved.isEmpty()) { state_["identity"] = identity; state_["events"].to<JsonArray>(); return true; }
    if (deserializeJson(state_, saved) || state_["identity"].as<String>() != identity ||
        !state_["events"].is<JsonArray>() || state_["events"].size() > 8) return ready_ = false;
    for (JsonObject event : state_["events"].as<JsonArray>()) {
      String type = event["type"].as<String>();
      if (!DeviceProtocol::uuid(event["eventId"].as<String>()) ||
          !event["schemaVersion"].is<int>() || event["schemaVersion"].as<int>() != 1 ||
          !event["occurredAt"].is<const char*>() ||
          DeviceProtocol::parseUtc(event["occurredAt"].as<const char*>()) <= 0 ||
          (type != "HIGH_TEMPERATURE" && type != "DOOR_OPEN_TOO_LONG")) return ready_ = false;
      if (type == "HIGH_TEMPERATURE" &&
          (!event["observedTemperature"].is<float>() ||
           !isfinite(event["observedTemperature"].as<float>()))) return ready_ = false;
    }
    return true;
  }

  bool enqueue(const char* type, float temperature = NAN) {
    if (!ready_) return false;
    JsonArray events = state_["events"].as<JsonArray>();
    for (JsonObject event : events) if (event["type"].as<String>() == type) return true;
    if (events.size() >= 8) { Serial.println("[ALERT] Outbox day; van xu ly tai cho"); return false; }
    JsonObject event = events.add<JsonObject>();
    event["schemaVersion"] = 1; event["eventId"] = DeviceProtocol::newUuid();
    event["type"] = type; event["occurredAt"] = DeviceProtocol::utcNow();
    if (!isnan(temperature)) event["observedTemperature"] = temperature;
    if (!save()) { events.remove(events.size() - 1); return false; }
    return true;
  }

  bool peek(JsonDocument& event) {
    if (!ready_ || state_["events"].size() == 0) return false;
    event.set(state_["events"][0]); return true;
  }

  void delivered() {
    JsonDocument old; old.set(state_.as<JsonVariantConst>());
    state_["events"].as<JsonArray>().remove(0);
    if (!save()) state_.set(old.as<JsonVariantConst>());
  }

 private:
  Preferences store_;
  JsonDocument state_;
  String identity_;
  bool ready_ = false;
  bool save() {
    String value; serializeJson(state_, value);
    return store_.putString("outbox", value) == value.length() && store_.getString("outbox", "") == value;
  }
};

// Store full UUIDs before sounding the remote alarm. Never truncate NVS keys
// or replay an old siren after reconnect/reset.
class ControlAlarmHistory {
 public:
  bool begin(const String& identity) {
    identity_ = identity;
    ready_ = store_.begin("node2-siren", false);
    if (!ready_) return false;
    String saved = store_.getString("history", "");
    if (saved.isEmpty()) { state_["identity"] = identity; state_["events"].to<JsonArray>(); return true; }
    if (deserializeJson(state_, saved) || state_["identity"].as<String>() != identity ||
        !state_["events"].is<JsonArray>() || state_["events"].size() > 32) return ready_ = false;
    for (JsonObject event : state_["events"].as<JsonArray>()) {
      if (!DeviceProtocol::uuid(event["id"].as<String>()) || !event["expires"].is<int64_t>()) return ready_ = false;
    }
    return true;
  }

  bool accept(JsonDocument& command, bool retained, bool clockReady, bool configReady) {
    if (!ready_ || retained || !clockReady || !configReady ||
        !command["schemaVersion"].is<int>() || command["schemaVersion"].as<int>() != 1 ||
        !command["expiresAt"].is<const char*>() || !DeviceProtocol::uuid(command["eventId"].as<String>())) return false;
    int64_t expires = DeviceProtocol::parseUtc(command["expiresAt"].as<const char*>()), now = DeviceProtocol::nowMs();
    if (expires <= now || expires > now + 60000) return false;
    String id = command["eventId"].as<String>();
    JsonArray events = state_["events"].as<JsonArray>();
    for (JsonObject event : events) if (event["id"].as<String>() == id) return false;
    for (int i = static_cast<int>(events.size()) - 1; i >= 0; --i) {
      if (events[i]["expires"].as<int64_t>() <= now) events.remove(i);
    }
    if (events.size() >= 32) return false;
    JsonObject event = events.add<JsonObject>(); event["id"] = id; event["expires"] = expires;
    String value; serializeJson(state_, value);
    if (store_.putString("history", value) != value.length() || store_.getString("history", "") != value) {
      ready_ = false; return false;
    }
    return true;
  }
 private:
  Preferences store_;
  JsonDocument state_;
  String identity_;
  bool ready_ = false;
};
