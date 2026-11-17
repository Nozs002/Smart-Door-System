#pragma once
#include "AuthProtocol.h"
extern "C" {
#include "src/bcrypt/crypt_blowfish.h"
}

namespace CredentialSnapshot {
constexpr size_t MAX_BYTES = 16384;
inline bool validVerifier(const char* hash) {
  if (!hash || strlen(hash) != 60 || hash[0] != '$' || hash[1] != '2' ||
      (hash[2] != 'a' && hash[2] != 'b' && hash[2] != 'y') || hash[3] != '$' || hash[6] != '$') return false;
  if (hash[4] < '0' || hash[4] > '9' || hash[5] < '0' || hash[5] > '9') return false;
  int cost = (hash[4] - '0') * 10 + hash[5] - '0';
  if (cost < 4 || cost > 12) return false; // Bound work on ESP32; Backend currently uses cost 10.
  for (size_t i = 7; i < 60; ++i) {
    char c = hash[i];
    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') || c == '.' || c == '/')) return false;
  }
  const char* alphabet = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  if ((strchr(alphabet, hash[28]) - alphabet) % 16 != 0 ||
      (strchr(alphabet, hash[59]) - alphabet) % 4 != 0) return false;
  return true;
}
inline bool verifyPin(const char* pin, const char* verifier) {
  if (!pin || !validVerifier(verifier)) return false;
  size_t length = strlen(pin);
  if (length < 4 || length > 8) return false;
  for (size_t i = 0; i < length; ++i) if (pin[i] < '0' || pin[i] > '9') return false;
  char output[64] = {};
  bool computed = _crypt_blowfish_rn(pin, verifier, output, sizeof(output)) != nullptr;
  unsigned char difference = 0;
  for (size_t i = 0; i < 60; ++i) difference |= output[i] ^ verifier[i];
  volatile char* wipe = output;
  for (size_t i = 0; i < sizeof(output); ++i) wipe[i] = 0;
  return computed && difference == 0;
}
inline bool verifierSelfTest() {
  // Public test vector generated independently with Spring Security BCrypt.
  const char* hash = "$2a$04$abcdefghijklmnopqrstuufUlS0EEB.H3ewsjUNPrWW5qETJaYIty";
  return verifyPin("12345678", hash) && !verifyPin("12345679", hash);
}
inline bool validUid(const char* uid) {
  if (!uid) return false;
  size_t length = strlen(uid);
  if (length != 8 && length != 14 && length != 20) return false;
  for (size_t i = 0; i < length; ++i)
    if (!((uid[i] >= '0' && uid[i] <= '9') || (uid[i] >= 'A' && uid[i] <= 'F'))) return false;
  return true;
}
inline bool valid(JsonDocument& doc) {
  if (!doc["schemaVersion"].is<int>() || doc["schemaVersion"].as<int>() != 1 ||
      !doc["version"].is<uint32_t>() || doc["version"].as<uint32_t>() < 1 ||
      doc["pinVerifier"].isUnbound() ||
      !doc["cards"].is<JsonArray>()) return false;
  if (!doc["pinVerifier"].isNull() &&
      (!doc["pinVerifier"].is<const char*>() || !validVerifier(doc["pinVerifier"].as<const char*>()))) return false;
  JsonArrayConst cards = doc["cards"].as<JsonArrayConst>();
  for (size_t i = 0; i < cards.size(); ++i) {
    JsonObjectConst card = cards[i].as<JsonObjectConst>();
    if (card.isNull() || !card["credentialId"].is<int64_t>() || card["credentialId"].as<int64_t>() < 1 ||
        !card["cardholderId"].is<int64_t>() || card["cardholderId"].as<int64_t>() < 1 ||
        !validUid(card["uid"].as<const char*>()) || card["expiresAt"].isUnbound()) return false;
    if (!card["expiresAt"].isNull() && (!card["expiresAt"].is<const char*>() ||
        AuthProtocol::parseUtc(card["expiresAt"].as<const char*>()) <= 0)) return false;
    for (size_t j = 0; j < i; ++j)
      if (card["credentialId"] == cards[j]["credentialId"] || card["uid"] == cards[j]["uid"]) return false;
  }
  return measureJson(doc) <= MAX_BYTES;
}
// Snapshot updates are complete replacements; reason is transport metadata, not applied state.
inline void normalize(JsonDocument& target, JsonDocument& source) {
  target["schemaVersion"] = source["schemaVersion"];
  target["version"] = source["version"];
  target["pinVerifier"] = source["pinVerifier"];
  target["cards"] = source["cards"];
}
enum class Update { REJECT, REPLAY, REPLACE };
inline Update updateDecision(JsonDocument& next, JsonDocument& current) {
  uint32_t version = next["version"], previous = current["version"] | 0UL;
  if (version < previous) return Update::REJECT;
  if (version == previous) return next.as<JsonVariantConst>() == current.as<JsonVariantConst>()
    ? Update::REPLAY : Update::REJECT;
  return Update::REPLACE;
}
}
