#pragma once

#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <Preferences.h>
#include <time.h>
#include "../shared/DeviceProtocol.h"
#include "ControlState.h"

// Bounded journal: expired entries can be reused, live entries never evicted.
// Persist REJECTED before moving; a reset in the movement window must never
// replay the command or claim that the physical position is known.
class Node2Actuator {
 public:
  static constexpr int SIGNAL_PIN = 18;
  static constexpr int OPEN_ANGLE = 180;
  static constexpr int CLOSED_ANGLE = 90;
  static constexpr unsigned long SETTLE_MS = 700;
  static constexpr size_t CAPACITY = 32;

  bool begin(const String& identity) {
    identity_ = identity;
    ready_ = store_.begin("node2-unlock", false);
    if (!ready_) return false;
    String saved = store_.getString("journal", "");
    if (saved.isEmpty()) return true;
    JsonDocument doc;
    if (deserializeJson(doc, saved) || !doc["entries"].is<JsonArray>() ||
        doc["identity"].as<String>() != identity_) {
      // Do not silently erase an unknown journal and risk replaying a command.
      ready_ = false;
      return false;
    }
    for (JsonObject row : doc["entries"].as<JsonArray>()) {
      String id = row["id"].as<String>();
      String result = row["result"].as<String>();
      String action = row["action"] | "UNLOCK"; // pre-CLOSE journal compatibility
      if (count_ >= CAPACITY || !validId(id) ||
          (row.containsKey("action") && !row["action"].is<const char*>()) ||
          (result != "EXECUTED" && result != "REJECTED") ||
          (action != "UNLOCK" && action != "CLOSE") ||
          !row["expires"].is<long long>() || row["expires"].as<long long>() <= 0) {
        ready_ = false;
        return false;
      }
      for (size_t i = 0; i < count_; ++i) {
        if (entries_[i].id == id) { ready_ = false; return false; }
      }
      entries_[count_++] = {id, result, row["expires"].as<long long>(), true, action};
    }
    return true;
  }

  void receive(JsonDocument& command, bool retained, bool clockReady,
               bool configurationReady, const char* expectedAction, bool closeAllowed) {
    if (retained) {
      Serial.println("Tu choi lenh servo retained");
      return;
    }
    String id = command["commandId"].as<String>();
    if (!validId(id)) {
      Serial.println("commandId khong hop le");
      return;
    }
    bool shapeValid = command["schemaVersion"].is<int>() && command["schemaVersion"].as<int>() == 1 &&
      command["action"].is<const char*>() && String(command["action"].as<const char*>()) == expectedAction &&
      (String(expectedAction) == "UNLOCK" || String(expectedAction) == "CLOSE");
    // Duplicate commands only repeat the saved result, including after reset.
    for (size_t i = 0; i < count_; ++i) {
      if (entries_[i].id == id) {
        if (!shapeValid || entries_[i].action != expectedAction) {
          Serial.println("Trung ID nhung khac hanh dong; khong thuc thi"); return;
        }
        if (static_cast<int>(i) != moving_) entries_[i].ackPending = true;
        Serial.println("Lenh trung; khong quay servo lai");
        return;
      }
    }
    if (!ready_ || !clockReady) {
      Serial.println("Chua san sang bo nho/gio; khong quay servo");
      return;
    }
    long long expires = parseUtc(command["expiresAt"] | "");
    long long now = static_cast<long long>(time(nullptr));
    if (expires <= now) {
      Serial.println("Lenh het han hoac expiresAt sai; khong quay servo");
      return;
    }
    // Backend issues commands for 15 seconds. Fail closed on implausible expiry.
    bool valid = shapeValid && expires <= now + 60 && configurationReady && moving_ < 0 &&
      (String(expectedAction) != "CLOSE" || closeAllowed);

    int slot = count_ < CAPACITY ? static_cast<int>(count_++) : -1;
    if (slot < 0) {
      for (size_t i = 0; i < count_; ++i) {
        if (entries_[i].expires <= now && static_cast<int>(i) != moving_) {
          slot = static_cast<int>(i);
          break;
        }
      }
    }
    if (slot < 0) {
      Serial.println("Nhat ky day; khong quay servo");
      return;
    }
    entries_[slot] = {id, "REJECTED", expires, true, String(expectedAction)};
    if (!save()) {
      ready_ = false;
      entries_[slot].ackPending = false;
      Serial.println("Loi luu lenh; khong quay servo");
      return;
    }
    if (!valid) {
      Serial.println("Tu choi lenh servo: sai, node ban hoac dang bao nhiet");
      return;
    }
    if (!servo_.attached()) {
      servo_.setPeriodHertz(50);
      servo_.attach(SIGNAL_PIN, 500, 2400);
    }
    if (!servo_.attached()) {
      Serial.println("Khong khoi tao duoc servo");
      return;
    }
    moving_ = slot;
    autoClose_.cancel();
    entries_[slot].ackPending = false;
    started_ = millis();
    commandedOpen_ = entries_[slot].action == "UNLOCK";
    servo_.write(commandedOpen_ ? OPEN_ANGLE : CLOSED_ANGLE);
    Serial.println(commandedOpen_ ? "Servo: mo cua 180 do" : "Servo: dong cua 90 do");
  }

  // Call before checking network so movement completion works while offline.
  void tick(bool closeAllowed) {
    if (moving_ < 0) {
      if (autoClose_.due(millis(), closeAllowed)) {
        autoClose_.cancel();
        if (servo_.attached()) {
          servo_.write(CLOSED_ANGLE); commandedOpen_ = false;
          Serial.println("[AUTO] Dong cua 90 do sau 10 giay giu mo");
        }
      }
      return;
    }
    if (entries_[moving_].action == "CLOSE" && !closeAllowed) { emergencyOpen(); return; }
    if (millis() - started_ < SETTLE_MS) return;
    int slot = moving_;
    moving_ = -1;
    entries_[slot].result = "EXECUTED";
    if (!save()) {
      ready_ = false;
      entries_[slot].result = "REJECTED";
      Serial.println("Servo da nhan lenh nhung khong luu duoc ket qua; trang thai khong chac chan");
      return;
    }
    entries_[slot].ackPending = true;
    if (entries_[slot].action == "UNLOCK" && closeAllowed) autoClose_.opened(millis());
    Serial.println("Da luu ket qua EXECUTED; cho gui ACK");
  }

  // Local test: type CLOSE + Newline; same fire interlock as remote close.
  bool pollSerial(bool closeAllowed) {
    bool changed = false;
    while (Serial.available()) {
      char c = Serial.read();
      if (c == '\r' || c == '\n') {
        if (serialLine_ == "CLOSE" && !closeAllowed) {
          Serial.println("Khong cho phep dong: bao nhiet, giu mo hoac chua kich hoat");
        } else if (serialLine_ == "CLOSE" && moving_ < 0) {
          if (!servo_.attached()) {
            servo_.setPeriodHertz(50);
            servo_.attach(SIGNAL_PIN, 500, 2400);
          }
          if (servo_.attached()) {
            servo_.write(CLOSED_ANGLE);
            commandedOpen_ = false;
            autoClose_.cancel();
            changed = true;
            Serial.println("Servo: dong chot 90 do (lenh Serial)");
          }
        }
        serialLine_ = "";
      } else if (serialLine_.length() < 16) {
        serialLine_ += c;
      }
    }
    return changed;
  }

  bool commandedOpen() const { return commandedOpen_; }
  bool moving() const { return moving_ >= 0; }

  bool closeForSecurityMode() {
    if (!servo_.attached()) {
      servo_.setPeriodHertz(50); servo_.attach(SIGNAL_PIN, 500, 2400);
    }
    if (!servo_.attached()) return false;
    // A mode change supersedes an unfinished command, whose durable reservation
    // is still REJECTED. Do not later ACK that interrupted movement as executed.
    if (moving_ >= 0) { entries_[moving_].ackPending = true; moving_ = -1; }
    autoClose_.cancel(); servo_.write(CLOSED_ANGLE); commandedOpen_ = false;
    Serial.println("[MODE] Bat bao mat: dong cua 90 do");
    return true;
  }

  // Local emergency response is independent of MQTT, NTP and command journal.
  bool emergencyOpen() {
    return keepOpen("[LOCAL] Nhiet do vuot nguong: mo cua 180 do");
  }

  bool keepOpen(const char* message) {
    autoClose_.cancel();
    if (moving_ >= 0 && entries_[moving_].action == "CLOSE") {
      // REJECTED was durably reserved before movement. Never ACK an interrupted
      // close as EXECUTED when the emergency response reverses that movement.
      entries_[moving_].ackPending = true; moving_ = -1;
    }
    if (!servo_.attached()) {
      servo_.setPeriodHertz(50);
      servo_.attach(SIGNAL_PIN, 500, 2400);
    }
    if (!servo_.attached()) return false;
    if (!commandedOpen_) {
      servo_.write(OPEN_ANGLE);
      commandedOpen_ = true;
      Serial.println(message);
    }
    return true;
  }

  int pendingAck(String& payload) {
    long long now = static_cast<long long>(time(nullptr));
    for (size_t i = 0; i < count_; ++i) {
      if (!entries_[i].ackPending || static_cast<int>(i) == moving_) continue;
      if (entries_[i].expires <= now) { entries_[i].ackPending = false; continue; }
      JsonDocument ack;
      ack["schemaVersion"] = 1;
      ack["commandId"] = entries_[i].id;
      ack["result"] = entries_[i].result;
      serializeJson(ack, payload);
      return static_cast<int>(i);
    }
    return -1;
  }

  void ackSent(int slot) { entries_[slot].ackPending = false; }

 private:
  struct Entry {
    String id;
    String result;
    long long expires;
    bool ackPending;
    String action;
  };
  Entry entries_[CAPACITY];
  size_t count_ = 0;
  int moving_ = -1;
  unsigned long started_ = 0;
  bool ready_ = false;
  bool commandedOpen_ = false;
  ControlPolicy::AutoClose autoClose_;
  Preferences store_;
  Servo servo_;
  String identity_;
  String serialLine_;

  bool save() {
    JsonDocument doc;
    doc["identity"] = identity_;
    JsonArray rows = doc["entries"].to<JsonArray>();
    for (size_t i = 0; i < count_; ++i) {
      JsonObject row = rows.add<JsonObject>();
      row["id"] = entries_[i].id;
      row["result"] = entries_[i].result;
      row["action"] = entries_[i].action;
      row["expires"] = entries_[i].expires;
    }
    String snapshot;
    serializeJson(doc, snapshot);
    return store_.putString("journal", snapshot) == snapshot.length() &&
      store_.getString("journal", "") == snapshot;
  }

  static bool validId(const String& id) {
    return DeviceProtocol::uuid(id);
  }

  static long long parseUtc(const char* text) {
    return DeviceProtocol::parseUtc(text) / 1000;
  }
};
