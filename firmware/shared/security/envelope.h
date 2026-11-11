#ifndef SECURITY_CORE_H
#define SECURITY_CORE_H

#include <ArduinoJson.h>
#include "mbedtls/md.h"
#include "mbedtls/base64.h"
#include <time.h>

// 1. Sinh UUID v4 chuẩn theo Contract backend
inline String generateUUID() {
  uint8_t bytes[16];
  esp_fill_random(bytes, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // Version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // Variant 1
  char uuidStr[37];
  sprintf(uuidStr, "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
    bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
    bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]);
  return String(uuidStr);
}

// 2. Lấy thời gian UTC chuẩn ISO-8601
inline String getISO8601Time() {
  time_t now; time(&now);
  struct tm timeinfo; gmtime_r(&now, &timeinfo);
  char buffer[30];
  strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
  return String(buffer);
}

// 3. Sinh Nonce đủ dài và không lặp (Timestamp + Random)
inline String generateNonce() {
  return String(time(nullptr)) + "-" + String(esp_random());
}

// 4. Mã hóa Base64 Payload
inline String base64Encode(const String& input) {
  size_t olen = 0;
  mbedtls_base64_encode(NULL, 0, &olen, (const unsigned char*)input.c_str(), input.length());
  unsigned char* out = (unsigned char*)malloc(olen + 1);
  mbedtls_base64_encode(out, olen, &olen, (const unsigned char*)input.c_str(), input.length());
  out[olen] = '\0';
  String encoded = String((char*)out);
  free(out);
  return encoded;
}

// 5. Ký HMAC-SHA256 với Secret Key đã giải mã Base64
inline String signHMAC(const String& canonical, const char* b64Secret) {
  unsigned char rawKey[64]; size_t keyLen = 0;
  mbedtls_base64_decode(rawKey, sizeof(rawKey), &keyLen, (const unsigned char*)b64Secret, strlen(b64Secret));

  byte hmacResult[32];
  mbedtls_md_context_t ctx;
  mbedtls_md_init(&ctx);
  mbedtls_md_setup(&ctx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
  mbedtls_md_hmac_starts(&ctx, rawKey, keyLen);
  mbedtls_md_hmac_update(&ctx, (const unsigned char*)canonical.c_str(), canonical.length());
  mbedtls_md_hmac_finish(&ctx, hmacResult);
  mbedtls_md_free(&ctx);

  String hash = "";
  for(int i=0; i<32; i++) {
    char str[3]; sprintf(str, "%02x", (int)hmacResult[i]); hash += str;
  }
  return hash;
}

// 6. Tạo Envelope chuẩn chỉnh theo SignedDeviceMessageService.java
inline String buildSignedEnvelope(String topic, const char* credId, String rawPayload, const char* b64Secret) {
  long sentAt = time(nullptr); // Backend cần số Unix cho sentAt
  String nonce = generateNonce();
  String b64Payload = base64Encode(rawPayload);
  
  // Canonical: topic\ncredentialId\nsentAt\nnonce\nbase64Payload
  String canonical = topic + "\n" + String(credId) + "\n" + String(sentAt) + "\n" + nonce + "\n" + b64Payload;
  String signature = signHMAC(canonical, b64Secret);
  
  StaticJsonDocument<512> doc;
  doc["credentialId"] = credId;
  doc["sentAt"] = sentAt; 
  doc["nonce"] = nonce;
  doc["payload"] = b64Payload;
  doc["signature"] = signature;
  
  String output; serializeJson(doc, output);
  return output;
}
#endif