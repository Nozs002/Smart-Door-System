#pragma once
#include <WiFi.h>
#include <ArduinoMqttClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <Keypad.h>
#include <esp_sntp.h>
#include "AuthProtocol.h"
#include "CredentialSnapshot.h"
#include "PinInput.h"
#include <memory>
#if __has_include("node_auth_config.local.h")
#include "node_auth_config.local.h"
#else
#include "node_auth_config.h.example"
#endif

// Access remains online; persistent credential synchronization does not enable offline unlock.
#include "../shared/DeviceEnrollment.h"
#if __has_include("enrollment_config.local.h")
#include "enrollment_config.local.h"
#endif
DeviceEnrollment runtime;
WiFiClient network;
MqttClient mqtt(network);
AuthProtocol::Signer signer;
Preferences storage;
JsonDocument configuration;
JsonDocument credentials;
MFRC522 rfid(5, 15);
LiquidCrystal_I2C lcd(AuthConfig::LCD_ADDRESS, 16, 2);
char keys[4][3] = {{'1','2','3'}, {'4','5','6'}, {'7','8','9'}, {'*','0','#'}};
byte rowPins[4] = {13, 14, 27, 26};
byte colPins[3] = {25, 33, 32};
Keypad keypad(makeKeymap(keys), rowPins, colPins, 4, 3);
constexpr unsigned long REQUEST_TIMEOUT_MS = 15000, PIN_IDLE_MS = 20000;
constexpr size_t MAX_MESSAGE = CredentialSnapshot::MAX_BYTES, QUEUE_SIZE = 4;
struct Incoming { String topic; String payload; bool retained = false; };
Incoming messages[QUEUE_SIZE];
size_t queueHead = 0, queueCount = 0;
bool configured = false, storageReady = false, lcdReady = false, rfidReady = false;
bool linkReady = false, configAckPending = false, heartbeatPending = true;
bool credentialsAckPending = false;
volatile bool clockSynced = false;
volatile unsigned long lastClockSync = 0;
unsigned long lastWifiAttempt = 0, lastMqttAttempt = 0, lastHeartbeat = 0;
unsigned long lastHeartbeatAttempt = 0, lastAckAttempt = 0, lastRfidPoll = 0;
String prefix, identity, configEventId, credentialsEventId;
unsigned long lastCredentialsAckAttempt = 0;
char pinBuffer[PinInput::LENGTH + 1] = {};
size_t pinLength = 0;
unsigned long lastKeyAt = 0;
String pendingId, pendingMethod;
int64_t pendingDeadline = 0;
unsigned long pendingStartedAt = 0;
int64_t pinLockedUntil = 0, rfidLockedUntil = 0;
String noticeLine1, noticeLine2, screenLine1, screenLine2;
unsigned long noticeStarted = 0, noticeDuration = 0;

void erasePin() {
  PinInput::clear(pinBuffer, pinLength);
}
void showNotice(const String& first, const String& second, unsigned long duration = 2500) {
  noticeLine1 = first; noticeLine2 = second;
  noticeStarted = millis(); noticeDuration = duration;
}
void onClockSync(struct timeval*) { lastClockSync = millis(); clockSynced = true; }
bool trustedClock() { return clockSynced && millis() - lastClockSync < 7200000UL; }
bool hasConfiguration() { return storageReady && AuthProtocol::validConfig(configuration); }
bool holdingOpenMode() {
  return hasConfiguration() && configuration["operatingMode"].as<String>() == "AUTOMATIC";
}

bool persistLockouts() {
  JsonDocument doc;
  doc["identity"] = identity; doc["PIN"] = pinLockedUntil; doc["RFID"] = rfidLockedUntil;
  String record; serializeJson(doc, record);
  return storage.putString("lockouts", record) == record.length() && storage.getString("lockouts", "") == record;
}

void restoreState() {
  storageReady = storage.begin("node1-online", false);
  if (!storageReady) return;
  String record = storage.getString("snapshot", "");
  if (!record.isEmpty()) {
    JsonDocument doc;
    if (!deserializeJson(doc, record) && doc["identity"].as<String>() == identity) {
      configuration.set(doc["configuration"]);
      if (!AuthProtocol::validConfig(configuration)) configuration.clear();
    }
  }
  record = storage.getString("lockouts", "");
  if (!record.isEmpty()) {
    JsonDocument doc;
    if (deserializeJson(doc, record)) {
      storageReady = false;
    } else if (doc["identity"].as<String>() == identity) {
      if (!doc["PIN"].is<int64_t>() || !doc["RFID"].is<int64_t>() ||
          doc["PIN"].as<int64_t>() < 0 || doc["RFID"].as<int64_t>() < 0) storageReady = false;
      else { pinLockedUntil = doc["PIN"]; rfidLockedUntil = doc["RFID"]; }
    }
  }
  if (!storageReady) Serial.println("[NVS] Loi trang thai khoa tam; tu choi xac thuc");
  size_t bytes = storage.getBytesLength("credentials");
  if (bytes > MAX_MESSAGE + 512) { storageReady = false; return; }
  if (bytes) {
    std::unique_ptr<char[]> buffer(new (std::nothrow) char[bytes]);
    JsonDocument doc;
    if (!buffer || storage.getBytes("credentials", buffer.get(), bytes) != bytes ||
        deserializeJson(doc, buffer.get(), bytes)) { storageReady = false; return; }
    if (doc["identity"].as<String>() == identity) {
      credentials.set(doc["credentials"]);
      if (!CredentialSnapshot::valid(credentials)) { credentials.clear(); storageReady = false; }
    }
  }
}

bool publishSigned(const char* eventType, JsonDocument& payload) {
  if (!mqtt.connected() || !linkReady || !trustedClock()) return false;
  String message;
  if (!signer.envelope(prefix + "/events/" + eventType, payload, message)) return false;
  if (!mqtt.beginMessage(prefix + "/events/" + eventType, static_cast<unsigned long>(message.length()), false, 1)) return false;
  size_t written = mqtt.print(message);
  int result = mqtt.endMessage();
  // Never log the envelope: it may contain an access credential in Base64.
  return result == 1 && written == message.length();
}

void receiveMessage(int length) {
  if (length <= 0 || length > static_cast<int>(MAX_MESSAGE) || queueCount >= QUEUE_SIZE) {
    while (mqtt.available()) mqtt.read();
    Serial.println("[MQTT] Bo message qua gioi han"); return;
  }
  size_t slot = (queueHead + queueCount) % QUEUE_SIZE;
  messages[slot].topic = mqtt.messageTopic();
  messages[slot].retained = mqtt.messageRetain() != 0;
  std::unique_ptr<char[]> buffer(new (std::nothrow) char[length + 1]);
  if (!buffer) { while (mqtt.available()) mqtt.read(); return; }
  size_t received = mqtt.readBytes(buffer.get(), length);
  if (received != static_cast<size_t>(length) || memchr(buffer.get(), '\0', received)) return;
  buffer[received] = '\0'; messages[slot].payload = buffer.get();
  if (messages[slot].payload.length() == received) ++queueCount;
}

void applyCredentials(JsonDocument& doc) {
  if (!storageReady || !CredentialSnapshot::valid(doc)) {
    Serial.println("[CREDENTIALS] Snapshot khong hop le; khong ACK"); return;
  }
  JsonDocument next; CredentialSnapshot::normalize(next, doc);
  if (next.overflowed()) return;
  auto decision = CredentialSnapshot::updateDecision(next, credentials);
  if (decision == CredentialSnapshot::Update::REJECT) return;
  if (decision == CredentialSnapshot::Update::REPLACE) {
    JsonDocument stored; stored["identity"] = identity; stored["credentials"] = next.as<JsonVariantConst>();
    String record; serializeJson(stored, record);
    if (stored.overflowed() || record.length() != measureJson(stored)) return;
    std::unique_ptr<char[]> readback(new (std::nothrow) char[record.length()]);
    if (!readback) return;
    // One NVS blob: never partially apply cards or PIN. No plaintext PIN is stored.
    if (storage.putBytes("credentials", record.c_str(), record.length()) != record.length() ||
        storage.getBytes("credentials", readback.get(), record.length()) != record.length() ||
        memcmp(readback.get(), record.c_str(), record.length()) != 0) {
      storageReady = false; credentialsAckPending = false; erasePin(); return;
    }
    credentials = std::move(next);
  }
  credentialsEventId = AuthProtocol::newUuid(); credentialsAckPending = true;
}

void applyConfiguration(JsonDocument& doc) {
  if (!storageReady || !AuthProtocol::validConfig(doc)) {
    Serial.println("[CONFIG] Snapshot khong hop le hoac NVS khong san sang"); return;
  }
  uint32_t version = doc["version"], previous = configuration["version"] | 0UL;
  if (version < previous) return;
  if (version == previous) {
    if (doc.as<JsonVariantConst>() != configuration.as<JsonVariantConst>()) {
      Serial.println("[CONFIG] Cung version nhung khac noi dung; khong ACK"); return;
    }
  } else {
    JsonDocument stored;
    stored["identity"] = identity; stored["configuration"] = doc.as<JsonVariantConst>();
    String record; serializeJson(stored, record);
    if (storage.putString("snapshot", record) != record.length() || storage.getString("snapshot", "") != record) {
      storageReady = false; erasePin();
      Serial.println("[CONFIG] Luu that bai; dung nhan yeu cau"); return;
    }
    String previousMode = configuration["operatingMode"].as<String>();
    configuration.set(doc.as<JsonVariantConst>());
    if (previousMode != configuration["operatingMode"].as<String>()) {
      erasePin(); pendingId = ""; pendingMethod = ""; noticeDuration = 0;
    }
    Serial.print("[CONFIG] Da luu version "); Serial.println(version);
  }
  configEventId = AuthProtocol::newUuid(); configAckPending = true;
}

void finishAccessResult(JsonDocument& doc, bool retained) {
  if (!trustedClock() || !AuthProtocol::resultMatches(doc, pendingId, retained, AuthProtocol::nowMs(), pendingDeadline) ||
      millis() - pendingStartedAt >= REQUEST_TIMEOUT_MS) {
    Serial.println("[AUTH] Bo ket qua cu, sai luot hoac khong hop le"); return;
  }
  int64_t lockedUntil = 0;
  if (!doc["lockedUntil"].isNull()) {
    if (!doc["lockedUntil"].is<const char*>()) return;
    lockedUntil = AuthProtocol::parseUtc(doc["lockedUntil"].as<const char*>());
    if (lockedUntil <= 0 || lockedUntil > AuthProtocol::nowMs() + 3605000) return;
  }
  bool granted = doc["result"].as<String>() == "GRANTED";
  int64_t& localLockout = pendingMethod == "PIN" ? pinLockedUntil : rfidLockedUntil;
  int64_t nextLockout = granted ? 0 : lockedUntil;
  if (localLockout != nextLockout) {
    localLockout = nextLockout;
    if (!persistLockouts()) {
      storageReady = false; showNotice("LOI BO NHO", "Kiem tra Node 1", 5000);
      Serial.println("[NVS] Khong luu duoc khoa tam; dung nhan yeu cau");
      pendingId = ""; pendingMethod = ""; return;
    }
  }
  if (doc["reason"].as<String>() == "SECURITY_DISABLED") {
    showNotice("BAO MAT DA TAT", "Cho dong bo mode", 3500);
    Serial.println("[AUTH] Backend dang o che do giu mo");
  } else if (granted) {
    // GRANTED is not proof that Node 2 executed or the door physically opened.
    bool dispatched = doc["reason"].as<String>() == "QUEUED";
    showNotice("XAC THUC DUNG", dispatched ? "Da yeu cau mo" : "Kiem tra Node 2", 3500);
    Serial.println("[AUTH] GRANTED; xem ket qua mo chot tren App");
  } else if (lockedUntil > AuthProtocol::nowMs()) {
    showNotice("TAM KHOA", "Thu lai sau", 3500);
    Serial.println("[AUTH] DENIED; dang khoa tam");
  } else {
    showNotice("TU CHOI TRUY CAP", "Kiem tra the/PIN", 3000); Serial.println("[AUTH] DENIED");
  }
  pendingId = ""; pendingMethod = "";
}

void processMessages() {
  while (queueCount) {
    Incoming message = messages[queueHead];
    messages[queueHead].topic = ""; messages[queueHead].payload = "";
    queueHead = (queueHead + 1) % QUEUE_SIZE; --queueCount;
    JsonDocument doc;
    if (deserializeJson(doc, message.payload, DeserializationOption::NestingLimit(5)) || !doc.is<JsonObject>()) {
      Serial.println("[MQTT] JSON khong hop le"); continue;
    }
    if (message.topic == prefix + "/commands/configuration") applyConfiguration(doc);
    else if (message.topic == prefix + "/commands/credentials") applyCredentials(doc);
    else if (message.topic == prefix + "/commands/access-result") finishAccessResult(doc, message.retained);
  }
}

void submitAccess(const char* method, const String& value) {
  if (!pendingId.isEmpty()) return;
  if (holdingOpenMode()) { showNotice("BAO MAT DA TAT", "Che do giu mo"); return; }
  if (!configured || WiFi.status() != WL_CONNECTED || !mqtt.connected() || !linkReady) {
    showNotice("MAT KET NOI", "Khong mo offline"); return;
  }
  if (!trustedClock() || !hasConfiguration()) { showNotice("CHUA SAN SANG", "Cho gio/cau hinh"); return; }
  int64_t until = strcmp(method, "PIN") == 0 ? pinLockedUntil : rfidLockedUntil;
  if (until > AuthProtocol::nowMs()) { showNotice("TAM KHOA", "Thu lai sau"); return; }
  pendingId = AuthProtocol::newUuid(); pendingMethod = method; pendingStartedAt = millis();
  String occurredAt = AuthProtocol::utcNow();
  pendingDeadline = AuthProtocol::parseUtc(occurredAt.c_str()) + REQUEST_TIMEOUT_MS;
  JsonDocument request;
  request["schemaVersion"] = 1; request["eventId"] = pendingId;
  request["method"] = method; request["value"] = value; request["occurredAt"] = occurredAt;
  noticeDuration = 0;
  bool sent = publishSigned("access-request", request);
  request.clear();
  Serial.println(sent ? "[AUTH] Da gui yeu cau; cho ket qua" :
    "[AUTH] Chua chac da gui; cho ket qua, khong tu gui lai");
  // A timeout may occur after Backend receipt. Never retry with a new eventId,
  // persist a plaintext credential, or replay an access attempt on reconnect.
}

void handlePinKey(char key) {
  if (key == '*') {
    erasePin();
    noticeDuration = 0;
    return;
  }
  if (key < '0' || key > '9') return;
  if (!configured || !linkReady || !trustedClock() || !hasConfiguration()) {
    erasePin();
    showNotice("CHUA SAN SANG", "Kiem tra ket noi");
    return;
  }
  if (pinLockedUntil > AuthProtocol::nowMs()) {
    erasePin();
    showNotice("PIN TAM KHOA", "Thu lai sau");
    return;
  }
  lastKeyAt = millis();
  noticeDuration = 0;
  if (PinInput::press(key, pinBuffer, pinLength) == PinInput::Result::READY) {
    String value(pinBuffer);
    erasePin();
    submitAccess("PIN", value);
    value = "";
  }
}

void pollInputs() {
  if (pinLength && millis() - lastKeyAt >= PIN_IDLE_MS) erasePin();
  bool holdingOpen = holdingOpenMode();
  if (holdingOpen) erasePin();
  if (keypad.getKeys() && !holdingOpen) {
    for (size_t i = 0; i < LIST_MAX && pendingId.isEmpty(); ++i) {
      if (keypad.key[i].stateChanged && keypad.key[i].kstate == PRESSED) {
        handlePinKey(keypad.key[i].kchar);
      }
    }
  }
  if (!rfidReady || millis() - lastRfidPoll < 50) return;
  lastRfidPoll = millis();
  if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) return;
  String uid;
  bool valid = rfid.uid.size == 4 || rfid.uid.size == 7 || rfid.uid.size == 10;
  if (valid) {
    char hex[3];
    for (byte i = 0; i < rfid.uid.size; ++i) { snprintf(hex, sizeof(hex), "%02X", rfid.uid.uidByte[i]); uid += hex; }
  }
  rfid.PICC_HaltA(); rfid.PCD_StopCrypto1(); erasePin();
  if (valid && pendingId.isEmpty() && !holdingOpen) submitAccess("RFID", uid);
}

void refreshDisplay() {
  String first, second;
  if (!configured) {
    first = runtime.factoryMode() ? "CAI WIFI TREN APP" : "CAU HINH THIEU";
    second = runtime.factoryMode() ? "GIU BOOT 3 GIAY" : "Xem README";
  }
  else if (!storageReady) { first = "LOI BO NHO"; second = "Kiem tra Node 1"; }
  else if (holdingOpenMode()) { first = "BAO MAT DA TAT"; second = "Che do giu mo"; }
  else if (!pendingId.isEmpty()) { first = "DANG XAC THUC"; second = "Cho backend..."; }
  else if (noticeDuration && millis() - noticeStarted < noticeDuration) { first = noticeLine1; second = noticeLine2; }
  else if (WiFi.status() != WL_CONNECTED) { first = "MAT WI-FI"; second = "Khong mo offline"; }
  else if (!linkReady) { first = "MAT MQTT"; second = "Dang ket noi..."; }
  else if (!trustedClock()) { first = "DONG BO GIO..."; second = "Cho NTP"; }
  else if (!hasConfiguration()) { first = "CHO CAU HINH"; second = "Gui lai tu App"; }
  else if (pinLength) { first = "NHAP PIN 4 SO"; for (size_t i = 0; i < pinLength; ++i) second += '*'; }
  else { first = "QUET THE / PIN"; second = "Nhap PIN 4 so"; }
  first = first.substring(0, 16); second = second.substring(0, 16);
  if (first == screenLine1 && second == screenLine2) return;
  screenLine1 = first; screenLine2 = second;
  if (lcdReady) {
    while (first.length() < 16) first += ' ';
    while (second.length() < 16) second += ' ';
    lcd.setCursor(0, 0); lcd.print(first); lcd.setCursor(0, 1); lcd.print(second);
  }
}

void maintainNetwork() {
  if (WiFi.status() != WL_CONNECTED) {
    if (linkReady) { mqtt.stop(); erasePin(); Serial.println("[NET] Mat Wi-Fi"); }
    linkReady = false;
    if (!runtime.factoryMode() && millis() - lastWifiAttempt >= 15000) {
      lastWifiAttempt = millis(); WiFi.reconnect();
    }
    return;
  }
  if (!mqtt.connected()) {
    if (linkReady) erasePin();
    linkReady = false;
    if (millis() - lastMqttAttempt < 5000) return;
    lastMqttAttempt = millis();
    if (!mqtt.connect(runtime.mqttHost.c_str(), runtime.mqttPort)) {
      Serial.print("[MQTT] Loi ket noi: "); Serial.println(mqtt.connectError()); return;
    }
    bool subscribed = mqtt.subscribe(prefix + "/commands/configuration", 1) && mqtt.subscribeQoS() == 1 &&
      mqtt.subscribe(prefix + "/commands/credentials", 1) && mqtt.subscribeQoS() == 1 &&
      mqtt.subscribe(prefix + "/commands/access-result", 1) && mqtt.subscribeQoS() == 1;
    if (!subscribed) { mqtt.stop(); Serial.println("[MQTT] Dang ky topic bi tu choi"); return; }
    linkReady = true; heartbeatPending = true;
    Serial.println("[MQTT] Node 1 ket noi thanh cong");
  }
  mqtt.poll();
}

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22); Wire.setTimeOut(50);
  Wire.beginTransmission(AuthConfig::LCD_ADDRESS); lcdReady = Wire.endTransmission() == 0;
  if (lcdReady) { lcd.init(); lcd.backlight(); }
  else Serial.println("[HW] Khong thay LCD I2C; tiep tuc qua Serial");
  SPI.begin(18, 19, 23, 5); rfid.PCD_Init();
  byte version = rfid.PCD_ReadRegister(MFRC522::VersionReg);
  rfidReady = version != 0 && version != 0xff;
  if (!rfidReady) Serial.println("[HW] RC522 chua san sang; van co the dung keypad");
  keypad.setDebounceTime(30);
  setenv("TZ", "UTC0", 1); tzset();
  runtime.legacy(AuthConfig::WIFI_SSID,AuthConfig::WIFI_PASSWORD,AuthConfig::MQTT_HOST,AuthConfig::MQTT_PORT,AuthConfig::DOOR_ID,AuthConfig::DEVICE_CODE,AuthConfig::CREDENTIAL_ID,AuthConfig::MQTT_USERNAME,AuthConfig::MQTT_PASSWORD,AuthConfig::SIGNING_KEY_BASE64);
#if __has_include("enrollment_config.local.h")
  runtime.factory(EnrollmentConfig::UNIT_CODE,EnrollmentConfig::BOOTSTRAP_SECRET,EnrollmentConfig::SETUP_CODE,EnrollmentConfig::BOOTSTRAP_URL,EnrollmentConfig::CA_CERT,"NODE_1");
#endif
  if (!runtime.begin()) { Serial.println("[SETUP] Configure household Wi-Fi through App. Hold BOOT 3s to reopen setup."); return; }
  prefix = "smart-door/v1/doors/" + String(runtime.doorId) + "/devices/" + runtime.deviceCode.c_str();
  identity = String(runtime.doorId) + ":" + runtime.deviceCode.c_str() + ":" + runtime.credentialId.c_str();
  configured = strlen(runtime.wifiSsid.c_str()) > 0 && strlen(runtime.mqttHost.c_str()) > 0 &&
    runtime.doorId > 0 && AuthProtocol::topicCode(runtime.deviceCode.c_str()) &&
    strlen(runtime.username.c_str()) > 0 && strlen(runtime.password.c_str()) > 0 &&
    signer.begin(runtime.credentialId.c_str(), runtime.signingKey.c_str());
  if (!configured) {
    Serial.println("[SETUP] Dien node_auth_config.local.h bang credential NODE_1, khong dung NODE_2");
    refreshDisplay(); return;
  }
  restoreState();
  if (!CredentialSnapshot::verifierSelfTest()) {
    storageReady = false;
    Serial.println("[CREDENTIALS] BCrypt self-test failed; refuse setup ACK");
  }
  sntp_set_time_sync_notification_cb(onClockSync);
  if (!runtime.factoryMode()) {
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(runtime.wifiSsid.c_str(), runtime.wifiPassword.c_str());
  }
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  mqtt.setId(String("esp32-auth-") + runtime.deviceCode.c_str() + "-" + WiFi.macAddress());
  mqtt.setUsernamePassword(runtime.username.c_str(), runtime.password.c_str());
  mqtt.setCleanSession(true); mqtt.setConnectionTimeout(2000); mqtt.setTimeout(1000);
  mqtt.onMessage(receiveMessage);
  Serial.println("[SETUP] Node 1 online-only; cho Wi-Fi, MQTT va NTP");
}

void loop() {
  runtime.poll(configured);
  if (!configured) { refreshDisplay(); delay(10); return; }
  maintainNetwork(); processMessages();
  if (!pendingId.isEmpty() && (millis() - pendingStartedAt >= REQUEST_TIMEOUT_MS ||
      (trustedClock() && AuthProtocol::nowMs() >= pendingDeadline))) {
    pendingId = ""; pendingMethod = ""; erasePin();
    showNotice("HET GIO XAC THUC", "Kiem tra tren App", 4000);
    Serial.println("[AUTH] Het han; khong tu gui lai");
  }
  if (linkReady && trustedClock()) {
    if ((heartbeatPending || millis() - lastHeartbeat >= 20000) && millis() - lastHeartbeatAttempt >= 3000) {
      lastHeartbeatAttempt = millis();
      JsonDocument heartbeat; heartbeat["firmwareVersion"] = "node-auth-online-1.2";
      if (publishSigned("heartbeat", heartbeat)) { lastHeartbeat = millis(); heartbeatPending = false; }
      else heartbeatPending = true;
    }
    if (configAckPending && millis() - lastAckAttempt >= 5000 && hasConfiguration()) {
      lastAckAttempt = millis();
      JsonDocument ack;
      ack["schemaVersion"] = 1; ack["eventId"] = configEventId; ack["kind"] = "CONFIGURATION";
      ack["version"] = configuration["version"].as<uint32_t>(); ack["appliedAt"] = AuthProtocol::utcNow();
      if (publishSigned("sync-ack", ack)) { configAckPending = false; Serial.println("[CONFIG] Da gui ACK; kiem tra Backend"); }
    }
    if (credentialsAckPending && storageReady && millis() - lastCredentialsAckAttempt >= 5000) {
      lastCredentialsAckAttempt = millis();
      JsonDocument ack;
      ack["schemaVersion"] = 1; ack["eventId"] = credentialsEventId; ack["kind"] = "CREDENTIALS";
      ack["version"] = credentials["version"].as<uint32_t>(); ack["appliedAt"] = AuthProtocol::utcNow();
      if (publishSigned("sync-ack", ack)) {
        credentialsAckPending = false; Serial.println("[CREDENTIALS] Da gui ACK snapshot da luu");
      }
    }
  }
  pollInputs(); refreshDisplay(); delay(2);
}
