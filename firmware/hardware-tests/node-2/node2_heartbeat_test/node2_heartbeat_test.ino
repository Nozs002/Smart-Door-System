#include <WiFi.h>
#include <ArduinoMqttClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <time.h>
#include <sys/time.h>
#include <esp_system.h>
#include <mbedtls/base64.h>
#include <mbedtls/md.h>
#include <esp_sntp.h>
#include "Node2Unlock.h"

// Điền Wi-Fi của bạn.
#include "heartbeat_config.local.h"

WiFiClient network;
MqttClient mqtt(network);
Node2Unlock unlockControl;
volatile bool clockSynchronized = false;
volatile unsigned long lastClockSync = 0;
String incomingTopic;
String incomingMessage;
bool incomingRetained = false;
bool incomingPending = false;
unsigned long lastCommandAckAttempt = 0;
constexpr int DOOR_SENSOR_PIN = 16;
constexpr unsigned long DOOR_DEBOUNCE_MS = 50;
constexpr unsigned long TELEMETRY_INTERVAL_MS = 20000;
int doorCandidate = HIGH;
int doorStable = HIGH;
bool doorStateKnown = false;
bool telemetryPending = true;
unsigned long doorChangedAt = 0;
unsigned long lastTelemetry = 0;
unsigned long lastTelemetryAttempt = 0;

void updateDoorSensor() {
  int reading = digitalRead(DOOR_SENSOR_PIN);
  if (reading != doorCandidate) {
    doorCandidate = reading;
    doorChangedAt = millis();
  }
  if (millis() - doorChangedAt >= DOOR_DEBOUNCE_MS &&
      (!doorStateKnown || doorStable != doorCandidate)) {
    doorStable = doorCandidate;
    doorStateKnown = true;
    telemetryPending = true;
    Serial.println(doorStable == LOW ? "MC38: cua DONG" : "MC38: cua MO");
  }
}

void onClockSync(struct timeval*) {
  lastClockSync = millis();
  clockSynchronized = true;
}

bool trustedClock() {
  return clockSynchronized && millis() - lastClockSync < 7200000UL;
}

unsigned char signingKey[128];
size_t signingKeyLength = 0;

unsigned long lastConnect = 0;
unsigned long lastHeartbeat = 0;
bool heartbeatPending = true;
bool timeWaitingPrinted = false;
Preferences preferences;
JsonDocument appliedConfig;
bool storageReady = false;
bool configAckPending = false;
String configEventId;
unsigned long lastAckAttempt = 0;

bool validConfiguration(JsonDocument& config) {
  if (config["schemaVersion"].as<int>() != 1 ||
      !config["version"].is<unsigned long>() ||
      config["version"].as<unsigned long>() < 1 ||
      !config["maxFailedAttempts"].is<int>() ||
      !config["lockoutSeconds"].is<int>() ||
      !config["temperatureThreshold"].is<float>() ||
      !(config["openTimeoutSeconds"].isNull() || config["openTimeoutSeconds"].is<int>()) ||
      !config["operatingMode"].is<const char*>() ||
      !config["fireAlarmEnabled"].is<bool>() ||
      !config["doorOpenAlertEnabled"].is<bool>()) return false;

  int attempts = config["maxFailedAttempts"];
  int lockout = config["lockoutSeconds"];
  float temperature = config["temperatureThreshold"];
  int timeout = config["openTimeoutSeconds"] | 0;
  String mode = config["operatingMode"].as<String>();
  return attempts >= 1 && attempts <= 10 && lockout >= 10 && lockout <= 3600 &&
    temperature >= 30 && temperature <= 80 &&
    (config["openTimeoutSeconds"].isNull() || (timeout >= 5 && timeout <= 3600)) &&
    (mode == "SECURITY" || mode == "AUTOMATIC");
}

String configurationIdentity() {
  return String(DOOR_ID) + ":" + DEVICE_CODE + ":" + CREDENTIAL_ID;
}

String encodeBase64(const String& input) {
  unsigned char output[512];
  size_t length = 0;

  int result = mbedtls_base64_encode(
    output, sizeof(output) - 1, &length,
    reinterpret_cast<const unsigned char*>(input.c_str()),
    input.length()
  );

  if (result != 0) return "";
  output[length] = '\0';
  return String(reinterpret_cast<char*>(output));
}

String createNonce() {
  char nonce[33];

  for (int i = 0; i < 4; i++) {
    snprintf(nonce + i * 8, 9, "%08lx",
             static_cast<unsigned long>(esp_random()));
  }

  return String(nonce);
}

bool sendSignedEvent(const char* eventType, const String& jsonPayload) {
  time_t now = time(nullptr);

  // Chờ đồng bộ giờ trước khi ký.
  if (!trustedClock() || now < 1700000000) {
    if (!timeWaitingPrinted) {
      Serial.println("Dang cho dong bo gio NTP...");
      timeWaitingPrinted = true;
    }
    return false;
  }

  String topic = "smart-door/v1/doors/";
  topic += String(DOOR_ID);
  topic += "/devices/";
  topic += DEVICE_CODE;
  topic += "/events/";
  topic += eventType;

  String payload = encodeBase64(jsonPayload);

  if (payload.isEmpty()) return false;

  String timestamp = String(static_cast<unsigned long>(now));
  String nonce = createNonce();

  // Không có ký tự xuống dòng ở cuối chuỗi.
  String canonical = topic + "\n"
    + CREDENTIAL_ID + "\n"
    + timestamp + "\n"
    + nonce + "\n"
    + payload;

  unsigned char digest[32];

  int result = mbedtls_md_hmac(
    mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),
    signingKey, signingKeyLength,
    reinterpret_cast<const unsigned char*>(canonical.c_str()),
    canonical.length(), digest
  );

  if (result != 0) {
    Serial.println("Loi tao chu ky");
    return false;
  }

  char signature[65];
  for (int i = 0; i < 32; i++) {
    snprintf(signature + i * 2, 3, "%02x", digest[i]);
  }

  JsonDocument envelope;
  envelope["credentialId"] = CREDENTIAL_ID;
  envelope["sentAt"] = static_cast<unsigned long>(now);
  envelope["nonce"] = nonce;
  envelope["payload"] = payload;
  envelope["signature"] = signature;

  String message;
  serializeJson(envelope, message);

  bool sent = false;
  if (mqtt.beginMessage(topic.c_str(), static_cast<unsigned long>(message.length()), false, 1)) {
    size_t written = mqtt.print(message);
    int result = mqtt.endMessage();
    sent = written == message.length() && result == 1;
  }

  Serial.print(eventType);
  Serial.println(sent ? ": da gui; can kiem tra backend" : ": gui that bai");

  return sent;
}

bool sendHeartbeat() {
  return sendSignedEvent("heartbeat",
    "{\"firmwareVersion\":\"node-control-mc38-test-0.4\"}");
}

bool sendDoorTelemetry() {
  if (!trustedClock() || !doorStateKnown) return false;
  String random = createNonce();
  random.setCharAt(12, '4');
  random.setCharAt(16, '8');
  String eventId = random.substring(0, 8) + "-" + random.substring(8, 12) + "-" +
    random.substring(12, 16) + "-" + random.substring(16, 20) + "-" + random.substring(20);

  struct timeval tv;
  gettimeofday(&tv, nullptr);
  struct tm utc;
  gmtime_r(&tv.tv_sec, &utc);
  char date[20];
  strftime(date, sizeof(date), "%Y-%m-%dT%H:%M:%S", &utc);
  char observedAt[25];
  snprintf(observedAt, sizeof(observedAt), "%s.%03ldZ", date, static_cast<long>(tv.tv_usec / 1000));

  JsonDocument sample;
  sample["schemaVersion"] = 1;
  sample["eventId"] = eventId;
  sample["observedAt"] = observedAt;
  sample["temperature"] = nullptr;
  sample["humidity"] = nullptr;
  sample["doorState"] = doorStable == LOW ? "CLOSED" : "OPEN";
  // MC38 measures the door leaf, not the lock bolt. Servo has no feedback.
  sample["lockState"] = "UNKNOWN";
  String payload;
  serializeJson(sample, payload);
  return sendSignedEvent("telemetry", payload);
}

bool sendConfigurationAck() {
  time_t now = time(nullptr);
  if (now < 1700000000) return false;
  struct tm utc;
  gmtime_r(&now, &utc);
  char appliedAt[21];
  strftime(appliedAt, sizeof(appliedAt), "%Y-%m-%dT%H:%M:%SZ", &utc);
  JsonDocument ack;
  ack["schemaVersion"] = 1;
  ack["eventId"] = configEventId;
  ack["kind"] = "CONFIGURATION";
  ack["version"] = appliedConfig["version"].as<unsigned long>();
  ack["appliedAt"] = appliedAt;
  String payload;
  serializeJson(ack, payload);
  return sendSignedEvent("sync-ack", payload);
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  String unlockTopic = "smart-door/v1/doors/";
  unlockTopic += String(DOOR_ID);
  unlockTopic += "/devices/";
  unlockTopic += DEVICE_CODE;
  unlockTopic += "/commands/unlock";
  if (unlockTopic == topic) {
    JsonDocument command;
    if (deserializeJson(command, payload, length) || !command.is<JsonObject>()) {
      Serial.println("Lenh unlock JSON khong hop le");
      return;
    }
    unlockControl.receive(command, incomingRetained, trustedClock(),
      storageReady && validConfiguration(appliedConfig));
    return;
  }
  String expectedTopic = "smart-door/v1/doors/";
  expectedTopic += String(DOOR_ID);
  expectedTopic += "/devices/";
  expectedTopic += DEVICE_CODE;
  expectedTopic += "/commands/configuration";
  if (expectedTopic != topic) return;

  Serial.print("Nhan topic: ");
  Serial.println(topic);

  JsonDocument config;
  DeserializationError error = deserializeJson(config, payload, length);

  if (error || !config.is<JsonObject>()) {
    Serial.println("Cau hinh JSON khong hop le");
    return;
  }

  if (!validConfiguration(config)) {
    Serial.println("Cau hinh thieu truong hoac sai kieu du lieu");
    return;
  }

  if (!storageReady) {
    Serial.println("Bo nho khong san sang; khong ACK");
    return;
  }
  unsigned long version = config["version"];
  unsigned long previous = appliedConfig["version"] | 0UL;
  if (version < previous) {
    Serial.println("Tu choi cau hinh cu; khong ACK");
    return;
  }
  if (version == previous) {
    // So sanh noi dung JSON, khong phu thuoc thu tu truong.
    if (config.as<JsonVariantConst>() != appliedConfig.as<JsonVariantConst>()) {
      Serial.println("Cung version nhung khac noi dung; khong ACK");
      return;
    }
  } else {
    JsonDocument record;
    record["identity"] = configurationIdentity();
    record["configuration"] = config.as<JsonVariantConst>();
    String snapshot;
    serializeJson(record, snapshot);
    if (preferences.putString("snapshot", snapshot) != snapshot.length() ||
        preferences.getString("snapshot", "") != snapshot) {
      Serial.println("Luu cau hinh that bai; khong ACK");
      return;
    }
    appliedConfig.set(config.as<JsonVariantConst>());
    Serial.println("Da luu va nap cau hinh vao bo nho");
  }
  configEventId = createNonce();
  configAckPending = true;

  Serial.print("Phien ban: ");
  Serial.println(config["version"].as<unsigned long>());

  Serial.print("Nguong nhiet do: ");
  Serial.println(config["temperatureThreshold"].as<float>());

  Serial.print("Thoi gian cua mo: ");
  Serial.println(config["openTimeoutSeconds"].as<int>());
}

// Copy one bounded message; process outside the MQTT callback. In particular,
// publishing QoS 1 from inside a callback would nest the client's poll loop.
void receiveMqttMessage(int length) {
  if (length <= 0 || length > 2048 || incomingPending) {
    while (mqtt.available()) mqtt.read();
    Serial.println("Bo qua message qua lon hoac hang doi ban");
    return;
  }
  incomingTopic = mqtt.messageTopic();
  incomingRetained = mqtt.messageRetain() != 0;
  char buffer[2049];
  size_t received = mqtt.readBytes(buffer, length);
  if (received != static_cast<size_t>(length)) {
    Serial.println("Message MQTT khong day du");
    return;
  }
  buffer[received] = '\0';
  incomingMessage = buffer;
  incomingPending = true;
}

void setup() {
  Serial.begin(115200);
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP);
  doorCandidate = digitalRead(DOOR_SENSOR_PIN);
  doorChangedAt = millis();
  storageReady = preferences.begin("node2-config", false);
  if (storageReady) {
    JsonDocument record;
    String saved = preferences.getString("snapshot", "");
    if (!deserializeJson(record, saved) &&
        record["identity"].as<String>() == configurationIdentity()) {
      appliedConfig.set(record["configuration"]);
      if (validConfiguration(appliedConfig)) {
        Serial.println("Da khoi phuc cau hinh sau khoi dong");
      } else {
        appliedConfig.clear();
      }
    }
  }

  int result = mbedtls_base64_decode(
    signingKey, sizeof(signingKey), &signingKeyLength,
    reinterpret_cast<const unsigned char*>(SIGNING_KEY_BASE64),
    strlen(SIGNING_KEY_BASE64)
  );

  if (result != 0 || signingKeyLength < 32) {
    Serial.println("signingKey khong hop le. Kiem tra file credential.");
    while (true) delay(1000);
  }

  // No servo movement on boot/reconnect. Test CLOSE manually over Serial.
  if (!unlockControl.begin(configurationIdentity())) {
    Serial.println("Nhat ky unlock khong san sang; lenh mo se bi tu choi");
  }
  Serial.println("Servo GPIO18: mo=180, dong=90. Nhap CLOSE + Newline de dong thu.");

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  // Dùng giờ UTC.
  setenv("TZ", "UTC0", 1);
  tzset();
  sntp_set_time_sync_notification_cb(onClockSync);
  configTime(0, 0, "pool.ntp.org", "time.google.com");

  mqtt.onMessage(receiveMqttMessage);
  mqtt.setUsernamePassword(MQTT_USERNAME, MQTT_PASSWORD);
  mqtt.setCleanSession(true);
  mqtt.setConnectionTimeout(2000);
  mqtt.setTimeout(1000);

  Serial.println("Dang ket noi Wi-Fi...");
}

void loop() {
  unlockControl.tick();
  unlockControl.pollSerial();
  updateDoorSensor();
  if (WiFi.status() != WL_CONNECTED) {
    delay(10);
    return;
  }

  if (!mqtt.connected()) {
    if (millis() - lastConnect >= 5000) {
      lastConnect = millis();

      String clientId = "esp32-node2-";
      clientId += WiFi.macAddress();

      mqtt.setId(clientId);
      if (mqtt.connect(MQTT_HOST, MQTT_PORT)) {
        Serial.println("MQTT KET NOI THANH CONG");
        String configTopic = "smart-door/v1/doors/";
        configTopic += String(DOOR_ID);
        configTopic += "/devices/";
        configTopic += DEVICE_CODE;
        configTopic += "/commands/configuration";

        String unlockTopic = configTopic;
        unlockTopic.replace("/commands/configuration", "/commands/unlock");
        if (mqtt.subscribe(configTopic.c_str(), 1) && mqtt.subscribeQoS() == 1 &&
            mqtt.subscribe(unlockTopic.c_str(), 1) && mqtt.subscribeQoS() == 1) {
          Serial.println("Da gui yeu cau dang ky topic cau hinh");
        } else {
          Serial.println("Dang ky topic that bai; se ket noi lai");
          mqtt.stop();
        }
        heartbeatPending = true;
        telemetryPending = true;
      } else {
        Serial.print("MQTT loi, ma: ");
        Serial.println(mqtt.connectError());
      }
    }

    delay(10);
    return;
  }

  mqtt.poll();
  if (incomingPending) {
    onMqttMessage(const_cast<char*>(incomingTopic.c_str()),
      reinterpret_cast<byte*>(const_cast<char*>(incomingMessage.c_str())),
      incomingMessage.length());
    incomingPending = false;
  }

  if (trustedClock() && millis() - lastCommandAckAttempt >= 1000) {
    lastCommandAckAttempt = millis();
    String payload;
    int slot = unlockControl.pendingAck(payload);
    if (slot >= 0 && sendSignedEvent("command-ack", payload)) {
      unlockControl.ackSent(slot);
    }
  }

  // ACK ngoai callback, thu lai neu chua co gio hoac gui that bai.
  if (configAckPending && millis() - lastAckAttempt >= 5000) {
    lastAckAttempt = millis();
    if (sendConfigurationAck()) configAckPending = false;
  }

  if (heartbeatPending || millis() - lastHeartbeat >= 20000) {
    // Giới hạn thử lại khi chưa đồng bộ được giờ.
    static unsigned long lastTry = 0;

    if (millis() - lastTry >= 1000) {
      lastTry = millis();

      if (sendHeartbeat()) {
        heartbeatPending = false;
        lastHeartbeat = millis();
      }
    }
  }

  // Send a fresh observation after changes/reconnect and every 20 seconds.
  // No offline backlog: old door states must not look like current readings.
  if (trustedClock() && doorStateKnown &&
      (telemetryPending || millis() - lastTelemetry >= TELEMETRY_INTERVAL_MS) &&
      millis() - lastTelemetryAttempt >= 1000) {
    lastTelemetryAttempt = millis();
    if (sendDoorTelemetry()) {
      lastTelemetry = millis();
      telemetryPending = false;
    }
  }

  delay(10);
}
