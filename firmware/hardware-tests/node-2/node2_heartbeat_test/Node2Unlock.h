#pragma once

#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <Preferences.h>
#include <time.h>

// Bounded journal: expired entries can be reused, live entries never evicted.
// Persist REJECTED before moving; a reset in the movement window must never
// replay the command or claim that the physical position is known.
class Node2Unlock {
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
      if (count_ >= CAPACITY || !validId(id) ||
          (result != "EXECUTED" && result != "REJECTED") ||
          !row["expires"].is<long long>() || row["expires"].as<long long>() <= 0) {
        ready_ = false;
        return false;
      }
      for (size_t i = 0; i < count_; ++i) {
        if (entries_[i].id == id) { ready_ = false; return false; }
      }
      entries_[count_++] = {id, result, row["expires"].as<long long>(), true};
    }
    return true;
  }

  void receive(JsonDocument& command, bool retained, bool clockReady,
               bool configurationReady) {
    if (retained) {
      Serial.println("Tu choi unlock retained");
      return;
    }
    String id = command["commandId"].as<String>();
    if (!validId(id)) {
      Serial.println("commandId khong hop le");
      return;
    }
    // Duplicate commands only repeat the saved result, including after reset.
    for (size_t i = 0; i < count_; ++i) {
      if (entries_[i].id == id) {
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
    bool valid = command["schemaVersion"].is<int>() &&
      command["schemaVersion"].as<int>() == 1 &&
      command["action"].is<const char*>() &&
      String(command["action"].as<const char*>()) == "UNLOCK" &&
      expires <= now + 60 && configurationReady && moving_ < 0;

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
    entries_[slot] = {id, "REJECTED", expires, true};
    if (!save()) {
      ready_ = false;
      entries_[slot].ackPending = false;
      Serial.println("Loi luu lenh; khong quay servo");
      return;
    }
    if (!valid) {
      Serial.println("Tu choi lenh unlock sai hoac node dang ban");
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
    entries_[slot].ackPending = false;
    started_ = millis();
    servo_.write(OPEN_ANGLE);
    Serial.println("Servo: mo chot 180 do");
  }

  // Call before checking network so movement completion works while offline.
  void tick() {
    if (moving_ < 0 || millis() - started_ < SETTLE_MS) return;
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
    Serial.println("Da luu ket qua EXECUTED; cho gui ACK");
  }

  // Local test only: type CLOSE + Newline. No automatic relock is assumed.
  void pollSerial() {
    while (Serial.available()) {
      char c = Serial.read();
      if (c == '\r' || c == '\n') {
        if (serialLine_ == "CLOSE" && moving_ < 0) {
          if (!servo_.attached()) {
            servo_.setPeriodHertz(50);
            servo_.attach(SIGNAL_PIN, 500, 2400);
          }
          if (servo_.attached()) {
            servo_.write(CLOSED_ANGLE);
            Serial.println("Servo: dong chot 90 do (lenh Serial)");
          }
        }
        serialLine_ = "";
      } else if (serialLine_.length() < 16) {
        serialLine_ += c;
      }
    }
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
  };
  Entry entries_[CAPACITY];
  size_t count_ = 0;
  int moving_ = -1;
  unsigned long started_ = 0;
  bool ready_ = false;
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
      row["expires"] = entries_[i].expires;
    }
    String snapshot;
    serializeJson(doc, snapshot);
    return store_.putString("journal", snapshot) == snapshot.length() &&
      store_.getString("journal", "") == snapshot;
  }

  static bool validId(const String& id) {
    if (id.length() != 36) return false;
    for (size_t i = 0; i < 36; ++i) {
      if (i == 8 || i == 13 || i == 18 || i == 23) {
        if (id[i] != '-') return false;
      } else if (!isxdigit(static_cast<unsigned char>(id[i]))) return false;
    }
    return true;
  }

  static long long parseUtc(const char* text) {
    struct tm utc = {};
    char* tail = strptime(text, "%Y-%m-%dT%H:%M:%S", &utc);
    if (!tail) return 0;
    if (*tail == '.') {
      ++tail;
      int digits = 0;
      while (*tail >= '0' && *tail <= '9') { ++digits; ++tail; }
      if (digits < 1 || digits > 9) return 0;
    }
    if (strcmp(tail, "Z") != 0) return 0;
    char roundtrip[20];
    time_t epoch = mktime(&utc); // setup configures TZ=UTC0.
    struct tm normalized;
    gmtime_r(&epoch, &normalized);
    strftime(roundtrip, sizeof(roundtrip), "%Y-%m-%dT%H:%M:%S", &normalized);
    if (strncmp(text, roundtrip, 19) != 0) return 0;
    return static_cast<long long>(epoch);
  }
};
