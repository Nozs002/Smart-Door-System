#include <WiFi.h>
#include <MQTT.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <DHT.h>
#include <Preferences.h>
#include <time.h>

#include "../shared/config.h"
#include "../shared/mqtt_contracts/mqtt_topics.h"
#include "../shared/security/envelope.h" 
#include "../shared/network/wifi_manager.h"

#define SERVO_PIN 18  
#define BUZZER_PIN 5  
#define LED_PIN 2     
#define MC38_PIN 16   
#define DHT_PIN 4     
DHT dht(DHT_PIN, DHT11);
Servo doorServo;

WiFiClient net;
MQTTClient mqtt(2048); 
Preferences nvs;

// --- BIẾN TRẠNG THÁI (NON-BLOCKING) ---
unsigned long servoOpenTime = 0;
bool isDoorUnlocked = false;

unsigned long alarmEndTime = 0;
bool isAlarming = false;

unsigned long lastTelemetryTime = 0;

// ĐÃ SỬA: Đưa trạng thái MC38 ra ngoài scope if để telemetry đọc được
int lastMc38State = HIGH;
int actualDoorState = HIGH; 
unsigned long lastDebounceTime = 0;
const unsigned long DEBOUNCE_DELAY = 100;
time_t doorOpenedAt = 0;
bool doorOpenAlertSent = false;

// --- CẤU HÌNH ĐỘNG TỪ BACKEND (Lưu NVS) ---
uint32_t config_version = 1;
bool config_fireAlarmEnabled = true;
bool config_doorOpenAlertEnabled = false;
float config_tempThreshold = 50.0;
uint32_t config_openTimeoutSeconds = 0;
int config_autoLockMs = 5000;

// --- HÀM TIỆN ÍCH NVS ---
String getNvsKey(String fullId) { return fullId.length() <= 15 ? fullId : fullId.substring(fullId.length() - 15); }
bool isCommandHandled(String cmdId) { nvs.begin("cmd", true); bool e = nvs.isKey(getNvsKey(cmdId).c_str()); nvs.end(); return e; }
void saveCommandState(String cmdId, String state) { nvs.begin("cmd", false); nvs.putString(getNvsKey(cmdId).c_str(), state); nvs.end(); }
String getCommandState(String cmdId) { nvs.begin("cmd", true); String s = nvs.getString(getNvsKey(cmdId).c_str(), ""); nvs.end(); return s; }

void loadDynamicConfig() {
  nvs.begin("config", true);
  config_version = nvs.getULong("version", 1);
  config_fireAlarmEnabled = nvs.getBool("fireAlarm", true);
  config_doorOpenAlertEnabled = nvs.getBool("doorAlert", false);
  config_tempThreshold = nvs.getFloat("tempThresh", 50.0);
  config_openTimeoutSeconds = nvs.getULong("openTimeout", 0);
  nvs.end();
}

void triggerServoOpen() {
  doorServo.write(90);
  digitalWrite(LED_PIN, HIGH);
  isDoorUnlocked = true;
  servoOpenTime = millis();
}

void triggerServoClose() {
  doorServo.write(0);
  digitalWrite(LED_PIN, LOW);
  isDoorUnlocked = false;
}

void triggerAlarmSiren(int durationMs) {
  digitalWrite(BUZZER_PIN, HIGH);
  isAlarming = true;
  alarmEndTime = millis() + durationMs;
}

void sendAck(String cmdId, String result) {
  StaticJsonDocument<256> doc;
  doc["schemaVersion"] = 1;
  doc["commandId"] = cmdId;
  doc["result"] = result;
  
  String topic = getCommandAckTopic(DOOR_ID, DEVICE_CODE_NODE_2);
  String raw; serializeJson(doc, raw);
  mqtt.publish(topic, buildSignedEnvelope(topic, CREDENTIAL_ID_NODE_2, raw, HMAC_SECRET_NODE_2), false, 1);
}

void sendConfigurationAck(uint32_t version) {
  StaticJsonDocument<256> doc;
  doc["eventId"] = generateUUID();
  doc["kind"] = "CONFIGURATION";
  doc["version"] = version;
  doc["appliedAt"] = getISO8601Time();

  String topic = "smart-door/v1/doors/" + DOOR_ID + "/devices/" + DEVICE_CODE_NODE_2 + "/events/sync-ack";
  String raw;
  serializeJson(doc, raw);
  mqtt.publish(topic, buildSignedEnvelope(topic, CREDENTIAL_ID_NODE_2, raw, HMAC_SECRET_NODE_2), false, 1);
}

String formatISO8601Time(time_t timestamp) {
  struct tm timeinfo;
  gmtime_r(&timestamp, &timeinfo);
  char buffer[30];
  strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
  return String(buffer);
}

void sendDoorOpenAlert() {
  StaticJsonDocument<256> alert;
  alert["schemaVersion"] = 1;
  alert["eventId"] = generateUUID();
  alert["type"] = "DOOR_OPEN_TOO_LONG";
  alert["occurredAt"] = formatISO8601Time(doorOpenedAt);

  String raw;
  serializeJson(alert, raw);
  String topic = getAlertTopic(DOOR_ID, DEVICE_CODE_NODE_2);
  mqtt.publish(topic, buildSignedEnvelope(topic, CREDENTIAL_ID_NODE_2, raw, HMAC_SECRET_NODE_2), false, 1);
}

// --- XỬ LÝ LỆNH TỪ BACKEND ---
void onMqttMessage(String &topic, String &payload) {
  StaticJsonDocument<512> cmdDoc;
  if (deserializeJson(cmdDoc, payload)) {
    Serial.println("[MQTT] Bỏ qua lệnh có JSON không hợp lệ");
    return;
  }
  if (cmdDoc["schemaVersion"] != 1) {
    Serial.println("[MQTT] Bỏ qua lệnh có schemaVersion không hỗ trợ");
    if (topic.endsWith("/unlock")) {
      String cmdId = cmdDoc["commandId"] | "";
      if (cmdId.length() > 0) {
        saveCommandState(cmdId, "REJECTED");
        sendAck(cmdId, "REJECTED");
      }
    }
    return;
  }

  if (topic.endsWith("/unlock")) {
    String cmdId = cmdDoc["commandId"] | "";
    String expiresAt = cmdDoc["expiresAt"] | "";
    String action = cmdDoc["action"] | "";
    if (cmdId.length() == 0) return;
    if (isCommandHandled(cmdId)) { sendAck(cmdId, getCommandState(cmdId)); return; }
    if (action != "UNLOCK" || expiresAt.length() == 0 || expiresAt <= getISO8601Time()) {
      saveCommandState(cmdId, "REJECTED");
      sendAck(cmdId, "REJECTED");
      return;
    }

    triggerServoOpen();
    saveCommandState(cmdId, "EXECUTED"); sendAck(cmdId, "EXECUTED");
  }
  else if (topic.endsWith("/alarm")) {
    String eventId = cmdDoc["eventId"] | "";
    String expiresAt = cmdDoc["expiresAt"] | "";
    if (eventId.length() == 0 || expiresAt.length() == 0) return;
    if (isCommandHandled(eventId)) return;
    if (expiresAt <= getISO8601Time()) {
      saveCommandState(eventId, "REJECTED");
      return;
    }

    triggerAlarmSiren(5000);
    saveCommandState(eventId, "EXECUTED");
  }
  else if (topic.endsWith("/configuration")) {
    if (!cmdDoc["version"].is<uint32_t>() ||
        !cmdDoc["fireAlarmEnabled"].is<bool>() ||
        !cmdDoc["doorOpenAlertEnabled"].is<bool>() ||
        !cmdDoc["temperatureThreshold"].is<float>()) {
      Serial.println("[CONFIG] Snapshot không hợp lệ");
      return;
    }

    uint32_t incomingVersion = cmdDoc["version"].as<uint32_t>();
    float temperatureThreshold = cmdDoc["temperatureThreshold"].as<float>();
    bool fireAlarmEnabled = cmdDoc["fireAlarmEnabled"].as<bool>();
    bool doorOpenAlertEnabled = cmdDoc["doorOpenAlertEnabled"].as<bool>();
    JsonVariantConst timeoutValue = cmdDoc["openTimeoutSeconds"];
    uint32_t openTimeoutSeconds = 0;

    if (incomingVersion == 0 || temperatureThreshold < 30.0 || temperatureThreshold > 80.0 ||
        (doorOpenAlertEnabled &&
         (!timeoutValue.is<uint32_t>() || timeoutValue.as<uint32_t>() < 5 || timeoutValue.as<uint32_t>() > 3600)) ||
        (!timeoutValue.isNull() && (!timeoutValue.is<uint32_t>() ||
         timeoutValue.as<uint32_t>() < 5 || timeoutValue.as<uint32_t>() > 3600))) {
      Serial.println("[CONFIG] Giá trị snapshot ngoài phạm vi");
      return;
    }
    if (timeoutValue.is<uint32_t>()) openTimeoutSeconds = timeoutValue.as<uint32_t>();

    if (incomingVersion > config_version) {
      nvs.begin("config", false);
      bool saved = nvs.putBool("fireAlarm", fireAlarmEnabled) > 0;
      saved = nvs.putBool("doorAlert", doorOpenAlertEnabled) && saved;
      saved = nvs.putFloat("tempThresh", temperatureThreshold) && saved;
      saved = nvs.putULong("openTimeout", openTimeoutSeconds) > 0 && saved;
      if (saved) saved = nvs.putULong("version", incomingVersion) > 0;
      nvs.end();
      if (!saved) {
        Serial.println("[CONFIG] Không lưu được snapshot vào NVS; chưa ACK");
        return;
      }

      config_version = incomingVersion;
      config_fireAlarmEnabled = fireAlarmEnabled;
      config_doorOpenAlertEnabled = doorOpenAlertEnabled;
      config_tempThreshold = temperatureThreshold;
      config_openTimeoutSeconds = openTimeoutSeconds;
      Serial.println("[CONFIG] Đã áp dụng version " + String(config_version));
    }

    sendConfigurationAck(config_version);
  }
  else {
    Serial.println("[MQTT] Bỏ qua lệnh không hỗ trợ: " + topic);
  }
}

void setup() {
  Serial.begin(115200);
  doorServo.attach(SERVO_PIN); triggerServoClose();
  pinMode(BUZZER_PIN, OUTPUT); pinMode(LED_PIN, OUTPUT); pinMode(MC38_PIN, INPUT_PULLUP);
  actualDoorState = digitalRead(MC38_PIN);
  lastMc38State = actualDoorState;
  dht.begin();
  
  loadDynamicConfig(); // Nạp cấu hình backend lưu tại ROM

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }
  configTime(0, 0, "pool.ntp.org");
  while (time(nullptr) < 100000) { delay(500); }
  if (actualDoorState == HIGH) doorOpenedAt = time(nullptr);

  mqtt.begin(MQTT_HOST, MQTT_PORT, net);
  mqtt.onMessage(onMqttMessage);
}

void loop() {
  mqtt.loop();
  if (!mqtt.connected()) {
    if (mqtt.connect(MQTT_CLIENT_ID_NODE_2, MQTT_USER_NODE_2, MQTT_PASSWORD_NODE_2)) {
      mqtt.subscribe(getCommandTopic(DOOR_ID, DEVICE_CODE_NODE_2), 1);
    }
  }

  unsigned long currentMillis = millis();

  // 1. TỰ ĐÓNG CHỐT (Non-blocking)
  if (isDoorUnlocked && (currentMillis - servoOpenTime >= config_autoLockMs)) {
    triggerServoClose();
  }

  // 2. TỰ TẮT CÒI (Non-blocking)
  if (isAlarming && currentMillis > alarmEndTime) {
    digitalWrite(BUZZER_PIN, LOW);
    isAlarming = false;
  }

  // 3. LỌC RUNG CẢM BIẾN CỬA MC38
  int reading = digitalRead(MC38_PIN);
  if (reading != lastMc38State) { lastDebounceTime = currentMillis; }
  if ((currentMillis - lastDebounceTime) > DEBOUNCE_DELAY) {
    if (reading != actualDoorState) {
      actualDoorState = reading;
      if (actualDoorState == HIGH) {
        doorOpenedAt = time(nullptr);
        doorOpenAlertSent = false;
      } else {
        doorOpenedAt = 0;
        doorOpenAlertSent = false;
      }
      lastTelemetryTime = 0; // Ép gửi Telemetry tức thời
    }
  }
  lastMc38State = reading;

  if (mqtt.connected() && config_doorOpenAlertEnabled && config_openTimeoutSeconds > 0 &&
      actualDoorState == HIGH && doorOpenedAt > 0 && !doorOpenAlertSent &&
      time(nullptr) - doorOpenedAt >= config_openTimeoutSeconds) {
    sendDoorOpenAlert();
    doorOpenAlertSent = true;
  }

  // 4. TELEMETRY & FIRE ALARM
  if (currentMillis - lastTelemetryTime > 10000 || lastTelemetryTime == 0) {
    if (mqtt.connected()) {
      float t = dht.readTemperature(); float h = dht.readHumidity();
      
      StaticJsonDocument<256> doc;
      doc["schemaVersion"] = 1;
      doc["eventId"] = generateUUID();
      doc["observedAt"] = getISO8601Time();
      if (isnan(t)) doc["temperature"] = nullptr;
      else doc["temperature"] = t;
      if (isnan(h)) doc["humidity"] = nullptr;
      else doc["humidity"] = h;
      doc["doorState"] = (actualDoorState == HIGH) ? "OPEN" : "CLOSED";
      doc["lockState"] = isDoorUnlocked ? "UNLOCKED" : "LOCKED";

      String raw; serializeJson(doc, raw);
      String topic = getTelemetryTopic(DOOR_ID, DEVICE_CODE_NODE_2);
      mqtt.publish(topic, buildSignedEnvelope(topic, CREDENTIAL_ID_NODE_2, raw, HMAC_SECRET_NODE_2), false, 1);
      
      // BÁO CHÁY DỰA TRÊN CẤU HÌNH ĐỘNG
      if (config_fireAlarmEnabled && !isnan(t) && t > config_tempThreshold) {
        StaticJsonDocument<256> alert;
        alert["schemaVersion"] = 1; 
        alert["eventId"] = generateUUID();
        alert["type"] = "HIGH_TEMPERATURE";
        alert["observedTemperature"] = t; 
        alert["occurredAt"] = getISO8601Time();
        
        String aRaw; serializeJson(alert, aRaw);
        String aTopic = getAlertTopic(DOOR_ID, DEVICE_CODE_NODE_2);
        mqtt.publish(aTopic, buildSignedEnvelope(aTopic, CREDENTIAL_ID_NODE_2, aRaw, HMAC_SECRET_NODE_2), false, 1);
        
        triggerAlarmSiren(3000); // Kích hoạt còi tại chỗ 3 giây mỗi vòng lặp nếu còn cháy
      }
    }
    lastTelemetryTime = currentMillis;
  }
}