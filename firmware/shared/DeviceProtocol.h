#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <esp_system.h>
#include <mbedtls/base64.h>
#include <mbedtls/md.h>
#include <time.h>
#include <sys/time.h>

namespace DeviceProtocol {
inline bool topicCode(const char* code) {
  size_t length = strlen(code);
  if (!length || length > 100) return false;
  for (size_t i = 0; i < length; ++i) {
    char c = code[i];
    bool alphaNumeric = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    if (!alphaNumeric && (i == 0 || (c != '.' && c != '_' && c != '-'))) return false;
  }
  return true;
}

inline bool uuid(const String& value) {
  if (value.length() != 36) return false;
  for (size_t i = 0; i < 36; ++i) {
    if (i == 8 || i == 13 || i == 18 || i == 23) {
      if (value[i] != '-') return false;
    } else if (!((value[i] >= '0' && value[i] <= '9') ||
                 (value[i] >= 'a' && value[i] <= 'f'))) return false;
  }
  return true;
}

inline String newUuid() {
  unsigned char bytes[16];
  esp_fill_random(bytes, sizeof(bytes));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  char text[37];
  snprintf(text, sizeof(text),
    "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
    bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
    bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
  return text;
}

// Epoch milliseconds. Accept UTC Z with optional 1..9 fractional digits;
// reject impossible dates rather than letting mktime normalize them silently.
inline int64_t parseUtc(const char* text) {
  if (!text || strlen(text) < 20 || strlen(text) > 30) return 0;
  for (size_t i = 0; i < 19; ++i) {
    char separator = i == 4 || i == 7 ? '-' : i == 10 ? 'T' :
      i == 13 || i == 16 ? ':' : '\0';
    if (separator ? text[i] != separator : (text[i] < '0' || text[i] > '9')) return 0;
  }
  struct tm utc = {};
  char* tail = strptime(text, "%Y-%m-%dT%H:%M:%S", &utc);
  if (!tail || utc.tm_year < 120) return 0;
  int milliseconds = 0;
  if (*tail == '.') {
    ++tail;
    int digits = 0;
    while (*tail >= '0' && *tail <= '9') {
      if (digits < 3) milliseconds = milliseconds * 10 + (*tail - '0');
      ++digits;
      ++tail;
    }
    if (digits < 1 || digits > 9) return 0;
    for (int i = digits; i < 3; ++i) milliseconds *= 10;
  }
  if (strcmp(tail, "Z") != 0) return 0;
  time_t epoch = mktime(&utc); // setup sets TZ=UTC0.
  struct tm normalized;
  gmtime_r(&epoch, &normalized);
  char date[20];
  strftime(date, sizeof(date), "%Y-%m-%dT%H:%M:%S", &normalized);
  if (strncmp(text, date, 19) != 0) return 0;
  return static_cast<int64_t>(epoch) * 1000 + milliseconds;
}

inline int64_t nowMs() {
  struct timeval tv;
  gettimeofday(&tv, nullptr);
  return static_cast<int64_t>(tv.tv_sec) * 1000 + tv.tv_usec / 1000;
}

inline String utcNow() {
  struct timeval tv;
  gettimeofday(&tv, nullptr);
  struct tm utc;
  gmtime_r(&tv.tv_sec, &utc);
  char date[20], text[25];
  strftime(date, sizeof(date), "%Y-%m-%dT%H:%M:%S", &utc);
  snprintf(text, sizeof(text), "%s.%03ldZ", date, static_cast<long>(tv.tv_usec / 1000));
  return text;
}

inline bool validConfig(JsonDocument& doc) {
  if (!doc.is<JsonObject>() || !doc["schemaVersion"].is<int>() ||
      doc["schemaVersion"].as<int>() != 1 || !doc["version"].is<uint32_t>() ||
      doc["version"].as<uint32_t>() < 1 || !doc["maxFailedAttempts"].is<int>() ||
      !doc["lockoutSeconds"].is<int>() || !doc["temperatureThreshold"].is<float>() ||
      !doc["operatingMode"].is<const char*>() || !doc["fireAlarmEnabled"].is<bool>() ||
      !doc["doorOpenAlertEnabled"].is<bool>() ||
      !doc.as<JsonObjectConst>().containsKey("openTimeoutSeconds")) return false;
  int attempts = doc["maxFailedAttempts"];
  int lockout = doc["lockoutSeconds"];
  float temperature = doc["temperatureThreshold"];
  JsonVariantConst timeout = doc["openTimeoutSeconds"];
  String mode = doc["operatingMode"].as<String>();
  return attempts >= 1 && attempts <= 10 && lockout >= 10 && lockout <= 3600 &&
    temperature >= 30 && temperature <= 80 &&
    (timeout.isNull() || (timeout.is<int>() && timeout.as<int>() >= 5 && timeout.as<int>() <= 3600)) &&
    (mode == "AUTOMATIC" || mode == "SECURITY");
}

inline bool resultMatches(JsonDocument& doc, const String& pendingId,
                          bool retained, int64_t now, int64_t deadline) {
  if (retained || pendingId.isEmpty() || now >= deadline ||
      !doc["schemaVersion"].is<int>() || doc["schemaVersion"].as<int>() != 1 ||
      !doc["eventId"].is<const char*>() || doc["eventId"].as<String>() != pendingId ||
      !doc["result"].is<const char*>() || !doc["reason"].is<const char*>() ||
      !doc["expiresAt"].is<const char*>()) return false;
  String result = doc["result"].as<String>();
  int64_t expires = parseUtc(doc["expiresAt"].as<const char*>());
  return (result == "GRANTED" || result == "DENIED") &&
    expires > now && expires <= deadline;
}

class Signer {
 public:
  bool begin(const char* credentialId, const char* base64Key) {
    credentialId_ = credentialId;
    ready_ = uuid(credentialId_) && mbedtls_base64_decode(
      key_, sizeof(key_), &keyLength_, reinterpret_cast<const unsigned char*>(base64Key),
      strlen(base64Key)) == 0 && keyLength_ >= 32;
    return ready_;
  }

  bool envelope(const String& topic, JsonDocument& payload, String& message) {
    if (!ready_) return false;
    String raw;
    serializeJson(payload, raw);
    if (raw.length() > 512) return false;
    unsigned char encoded[685];
    size_t length = 0;
    if (mbedtls_base64_encode(encoded, sizeof(encoded) - 1, &length,
        reinterpret_cast<const unsigned char*>(raw.c_str()), raw.length()) != 0) return false;
    encoded[length] = '\0';
    String base64(reinterpret_cast<char*>(encoded));
    unsigned long timestamp = static_cast<unsigned long>(time(nullptr));
    String nonce = newUuid();
    String canonical = topic + "\n" + credentialId_ + "\n" + String(timestamp) + "\n" + nonce + "\n" + base64;
    unsigned char digest[32];
    if (mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), key_, keyLength_,
        reinterpret_cast<const unsigned char*>(canonical.c_str()), canonical.length(), digest) != 0) return false;
    char signature[65];
    for (size_t i = 0; i < 32; ++i) snprintf(signature + i * 2, 3, "%02x", digest[i]);
    JsonDocument envelope;
    envelope["credentialId"] = credentialId_;
    envelope["sentAt"] = timestamp;
    envelope["nonce"] = nonce;
    envelope["payload"] = base64;
    envelope["signature"] = signature;
    serializeJson(envelope, message);
    return true;
  }

 private:
  unsigned char key_[64] = {};
  size_t keyLength_ = 0;
  String credentialId_;
  bool ready_ = false;
};
}
