#pragma once
#include <WiFi.h>
#include <ArduinoMqttClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <DHT.h>
#include <esp_sntp.h>
#include "../shared/DeviceProtocol.h"
#include "Node2Actuator.h"
#include "ControlState.h"
#if __has_include("node_control_config.local.h")
#include "node_control_config.local.h"
#else
#include "node_control_config.h.example"
#endif

constexpr int DHT_PIN = 4, MC38_PIN = 16, BUZZER_PIN = 5, LED_PIN = 2;
// Calibrated against the user's installed door: HIGH=closed, LOW=open.
constexpr int MC38_CLOSED_LEVEL = HIGH;
WiFiClient network;
MqttClient mqtt(network);
DeviceProtocol::Signer signer;
Preferences storage;
JsonDocument configuration;
bool controlEnabled = false;
Node2Actuator actuator;
ControlPolicy::SecurityMode securityMode;
unsigned long modeAppliedAt = 0;
ControlAlerts alerts;
ControlAlarmHistory alarmHistory;
DHT dht(DHT_PIN, DHT11);
String prefix, identity, configEventId;
bool configured = false, storageReady = false, linkReady = false;
bool configAckPending = false, heartbeatPending = true, telemetryPending = true;
volatile bool clockSynced = false;
volatile unsigned long lastClockSync = 0;
unsigned long lastWifiAttempt = 0, lastMqttAttempt = 0, lastHeartbeat = 0;
unsigned long lastHeartbeatAttempt = 0, lastAckAttempt = 0, lastCommandAck = 0;
unsigned long lastTelemetry = 0, lastTelemetryAttempt = 0, lastAlertAttempt = 0;
unsigned long lastAlertQueueAttempt = 0;
unsigned long lastDhtRead = 0, doorChangedAt = 0, doorOpenedAt = 0, remoteAlarmAt = 0;
int doorCandidate = HIGH, doorStable = HIGH;
bool doorKnown = false, remoteAlarm = false, fireActive = false, doorActive = false;
bool fireEventQueued = false, doorEventQueued = false;
float temperature = NAN, humidity = NAN;
struct Incoming { String topic; String payload; bool retained = false; };
constexpr size_t QUEUE_SIZE = 4, MAX_MESSAGE = 1536;
Incoming messages[QUEUE_SIZE];
size_t queueHead = 0, queueCount = 0;

void onClockSync(struct timeval*) { lastClockSync = millis(); clockSynced = true; }
bool trustedClock() { return clockSynced && millis() - lastClockSync < 7200000UL; }
bool validConfiguration(JsonDocument& doc) {
  return DeviceProtocol::validConfig(doc) &&
    (!doc["doorOpenAlertEnabled"].as<bool>() || doc["openTimeoutSeconds"].is<int>());
}
bool hasConfiguration() { return storageReady && validConfiguration(configuration); }
bool securityEnabled() {
  return !validConfiguration(configuration) || configuration["operatingMode"].as<String>() == "SECURITY";
}

void updateLocalHardware() {
  int raw = digitalRead(MC38_PIN);
  if (raw != doorCandidate) { doorCandidate = raw; doorChangedAt = millis(); }
  if (millis() - doorChangedAt >= 50 && (!doorKnown || doorStable != doorCandidate)) {
    doorStable = doorCandidate; doorKnown = true; doorOpenedAt = millis();
    telemetryPending = true;
    Serial.println(doorStable == MC38_CLOSED_LEVEL ? "[MC38] CLOSED" : "[MC38] OPEN");
  }
  if (millis() - lastDhtRead >= 2000) {
    lastDhtRead = millis();
    float t = dht.readTemperature(), h = dht.readHumidity();
    temperature = isfinite(t) && t >= -40 && t <= 125 ? t : NAN;
    humidity = isfinite(h) && h >= 0 && h <= 100 ? h : NAN;
    if (isnan(temperature) || isnan(humidity)) Serial.println("[DHT11] Mau loi; gui null cho so do loi");
  }
  // Defaults before the first snapshot match the documented local fire policy.
  bool localConfigValid = validConfiguration(configuration);
  bool fireEnabled = localConfigValid ? configuration["fireAlarmEnabled"].as<bool>() : true;
  float threshold = localConfigValid ? configuration["temperatureThreshold"].as<float>() : 50.0f;
  bool nextFire = ControlPolicy::fire(fireEnabled, !isnan(temperature), temperature, threshold, fireActive);
  if (nextFire != fireActive) {
    fireActive = nextFire; fireEventQueued = false; telemetryPending = true;
    Serial.println(fireActive ? "[LOCAL] Canh bao nhiet do" : "[LOCAL] Dieu kien bao nhiet da het; khong tu dong chot");
  }
  if (fireActive) actuator.emergencyOpen();
  bool security = securityEnabled();
  if (localConfigValid && controlEnabled) {
    auto effect = securityMode.next(security, fireActive);
    bool wasReady = securityMode.ready(security);
    bool applied = effect == ControlPolicy::ModeEffect::NONE;
    if (effect == ControlPolicy::ModeEffect::HOLD_OPEN) {
      applied = actuator.keepOpen("[MODE] Tat bao mat: giu cua mo 180 do");
    } else if (effect == ControlPolicy::ModeEffect::CLOSE) {
      applied = actuator.closeForSecurityMode();
    }
    if (applied) {
      securityMode.applied(security);
      if (!wasReady) { modeAppliedAt = millis(); telemetryPending = true; }
    }
    if (!security) remoteAlarm = false;
  }
  bool previousTargetOpen = actuator.commandedOpen();
  actuator.tick(!fireActive && security && controlEnabled);
  if (previousTargetOpen != actuator.commandedOpen()) telemetryPending = true;
  bool doorEnabled = localConfigValid && configuration["doorOpenAlertEnabled"].as<bool>();
  unsigned long timeout = localConfigValid ? configuration["openTimeoutSeconds"] | 0UL : 0;
  bool nextDoor = ControlPolicy::door(doorEnabled, doorKnown, doorStable != MC38_CLOSED_LEVEL, millis() - doorOpenedAt, timeout);
  if (nextDoor != doorActive) { doorActive = nextDoor; doorEventQueued = false; }
  if (remoteAlarm && millis() - remoteAlarmAt >= 5000) remoteAlarm = false;
  digitalWrite(BUZZER_PIN, fireActive || doorActive || remoteAlarm ? HIGH : LOW);
  if (actuator.pollSerial(!fireActive && security && controlEnabled)) telemetryPending = true;
  digitalWrite(LED_PIN, actuator.commandedOpen() ? HIGH : LOW);
}

bool publishSigned(const char* eventType, JsonDocument& payload) {
  if (!linkReady || !mqtt.connected() || !trustedClock()) return false;
  String message;
  if (!signer.envelope(prefix + "/events/" + eventType, payload, message)) return false;
  if (!mqtt.beginMessage(prefix + "/events/" + eventType, static_cast<unsigned long>(message.length()), false, 1)) return false;
  size_t written = mqtt.print(message);
  int result = mqtt.endMessage();
  bool sent = written == message.length() && result == 1;
  Serial.print("[MQTT] "); Serial.print(eventType); Serial.println(sent ? " sent; kiem tra Backend" : " failed");
  return sent;
}

void restoreConfiguration() {
  storageReady = storage.begin("node2-config", false);
  if (!storageReady) return;
  String saved = storage.getString("snapshot", "");
  if (saved.isEmpty()) return;
  JsonDocument doc;
  if (!deserializeJson(doc, saved) && doc["identity"].as<String>() == identity) {
    configuration.set(doc["configuration"]);
    controlEnabled = doc["controlEnabled"] | false;
    if (!validConfiguration(configuration)) configuration.clear();
  }
}

void applyConfiguration(JsonDocument& doc) {
  if (!storageReady || !validConfiguration(doc)) { Serial.println("[CONFIG] Snapshot khong hop le"); return; }
  bool authorized = doc["controlEnabled"].is<bool>() && doc["controlEnabled"].as<bool>();
  doc.remove("controlEnabled"); // Transport authorization can change at the same settings version.
  uint32_t version = doc["version"], previous = configuration["version"] | 0UL;
  if (version < previous) return;
  if (version == previous && doc.as<JsonVariantConst>() != configuration.as<JsonVariantConst>()) {
    Serial.println("[CONFIG] Cung version nhung khac noi dung; khong ACK"); return;
  }
  JsonDocument record;
  record["identity"] = identity; record["configuration"] = doc.as<JsonVariantConst>();
  record["controlEnabled"] = authorized;
  String value; serializeJson(record, value);
  if (storage.getString("snapshot", "") != value &&
      (storage.putString("snapshot", value) != value.length() || storage.getString("snapshot", "") != value)) {
    storageReady = false; controlEnabled = false;
    Serial.println("[CONFIG] Loi ghi NVS; khong ACK"); return;
  }
  configuration.set(doc.as<JsonVariantConst>()); controlEnabled = authorized;
  Serial.print("[CONFIG] Da luu version "); Serial.println(version);
  configEventId = DeviceProtocol::newUuid(); configAckPending = true;
}

void receiveMessage(int length) {
  if (length <= 0 || length > static_cast<int>(MAX_MESSAGE) || queueCount >= QUEUE_SIZE) {
    while (mqtt.available()) mqtt.read();
    Serial.println("[MQTT] Bo message qua gioi han"); return;
  }
  size_t slot = (queueHead + queueCount) % QUEUE_SIZE;
  messages[slot].topic = mqtt.messageTopic(); messages[slot].retained = mqtt.messageRetain() != 0;
  char buffer[MAX_MESSAGE + 1]; size_t received = mqtt.readBytes(buffer, length);
  if (received != static_cast<size_t>(length) || memchr(buffer, '\0', received)) return;
  buffer[received] = '\0'; messages[slot].payload = buffer; ++queueCount;
}

void processMessages() {
  while (queueCount) {
    Incoming message = messages[queueHead];
    messages[queueHead].topic = ""; messages[queueHead].payload = "";
    queueHead = (queueHead + 1) % QUEUE_SIZE; --queueCount;
    JsonDocument doc;
    if (deserializeJson(doc, message.payload, DeserializationOption::NestingLimit(5)) || !doc.is<JsonObject>()) continue;
    if (message.topic == prefix + "/commands/configuration") {
      applyConfiguration(doc);
      // Re-evaluate the fire interlock before a CLOSE queued after this snapshot.
      updateLocalHardware();
    }
    else if (message.topic == prefix + "/commands/unlock") {
      actuator.receive(doc, message.retained, trustedClock(), hasConfiguration() && controlEnabled, "UNLOCK", !fireActive && securityEnabled()); telemetryPending = true;
    } else if (message.topic == prefix + "/commands/close") {
      actuator.receive(doc, message.retained, trustedClock(), hasConfiguration() && controlEnabled, "CLOSE", !fireActive && securityEnabled()); telemetryPending = true;
    } else if (message.topic == prefix + "/commands/alarm" &&
        alarmHistory.accept(doc, message.retained, trustedClock(), hasConfiguration() && controlEnabled && securityEnabled())) {
      remoteAlarm = true; remoteAlarmAt = millis(); Serial.println("[ALARM] Coi canh bao 5 giay");
    }
  }
}

void maintainNetwork() {
  if (WiFi.status() != WL_CONNECTED) {
    if (linkReady) mqtt.stop(); linkReady = false;
    if (millis() - lastWifiAttempt >= 15000) { lastWifiAttempt = millis(); WiFi.reconnect(); }
    return;
  }
  if (!mqtt.connected()) {
    linkReady = false;
    if (millis() - lastMqttAttempt < 5000) return;
    lastMqttAttempt = millis();
    if (!mqtt.connect(ControlConfig::MQTT_HOST, ControlConfig::MQTT_PORT)) {
      Serial.print("[MQTT] Loi ket noi: "); Serial.println(mqtt.connectError()); return;
    }
    const char* topics[] = {"configuration", "unlock", "close", "alarm"};
    for (const char* topic : topics) {
      if (!mqtt.subscribe(prefix + "/commands/" + topic, 1) || mqtt.subscribeQoS() != 1) {
        mqtt.stop(); Serial.println("[MQTT] Dang ky topic bi tu choi"); return;
      }
    }
    linkReady = true; heartbeatPending = true; telemetryPending = true;
    Serial.println("[MQTT] Node 2 ket noi thanh cong");
  }
  mqtt.poll();
}

void queueLocalAlerts() {
  if (!trustedClock() || millis() - lastAlertQueueAttempt < 5000) return;
  lastAlertQueueAttempt = millis();
  // A condition first observed without trusted UTC is observed again after sync;
  // do not invent a historical timestamp for an event that cannot be dated.
  if (fireActive && !fireEventQueued && !isnan(temperature))
    fireEventQueued = alerts.enqueue("HIGH_TEMPERATURE", temperature);
  if (doorActive && !doorEventQueued) doorEventQueued = alerts.enqueue("DOOR_OPEN_TOO_LONG");
}

void sendNetworkEvents() {
  if (!linkReady || !trustedClock()) return;
  if (actuator.moving()) return;
  // Command ACK has priority over telemetry/alerts to fit Backend's 15s TTL.
  String raw;
  int slot = actuator.pendingAck(raw);
  if (slot >= 0) {
    if (millis() - lastCommandAck >= 1000) {
      lastCommandAck = millis();
      JsonDocument ack;
      if (!deserializeJson(ack, raw) && publishSigned("command-ack", ack)) actuator.ackSent(slot);
    }
    return;
  }
  if ((heartbeatPending || millis() - lastHeartbeat >= 20000) && millis() - lastHeartbeatAttempt >= 3000) {
    lastHeartbeatAttempt = millis(); JsonDocument heartbeat; heartbeat["firmwareVersion"] = "node-control-online-1.2";
    if (publishSigned("heartbeat", heartbeat)) { heartbeatPending = false; lastHeartbeat = millis(); }
    else heartbeatPending = true;
  }
  if (configAckPending && hasConfiguration() && (!controlEnabled || (securityMode.ready(securityEnabled()) &&
      millis() - modeAppliedAt >= Node2Actuator::SETTLE_MS)) && millis() - lastAckAttempt >= 5000) {
    lastAckAttempt = millis(); JsonDocument ack;
    ack["schemaVersion"] = 1; ack["eventId"] = configEventId; ack["kind"] = "CONFIGURATION";
    ack["version"] = configuration["version"].as<uint32_t>(); ack["appliedAt"] = DeviceProtocol::utcNow();
    if (publishSigned("sync-ack", ack)) configAckPending = false;
  }
  updateLocalHardware();
  if (doorKnown && (telemetryPending || millis() - lastTelemetry >= 20000) && millis() - lastTelemetryAttempt >= 1000) {
    lastTelemetryAttempt = millis(); JsonDocument sample;
    sample["schemaVersion"] = 1; sample["eventId"] = DeviceProtocol::newUuid(); sample["observedAt"] = DeviceProtocol::utcNow();
    sample["temperature"] = nullptr; sample["humidity"] = nullptr;
    if (millis() - lastDhtRead <= 10000) {
      if (!isnan(temperature)) sample["temperature"] = temperature;
      if (!isnan(humidity)) sample["humidity"] = humidity;
    }
    sample["doorState"] = doorStable == MC38_CLOSED_LEVEL ? "CLOSED" : "OPEN";
    sample["lockState"] = "UNKNOWN"; // no bolt position sensor on SG90
    if (publishSigned("telemetry", sample)) { telemetryPending = false; lastTelemetry = millis(); }
  }
  if (millis() - lastAlertAttempt >= 5000) {
    lastAlertAttempt = millis(); JsonDocument event;
    if (alerts.peek(event) && publishSigned("alert", event)) alerts.delivered();
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(MC38_PIN, INPUT_PULLUP); pinMode(BUZZER_PIN, OUTPUT); pinMode(LED_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW); digitalWrite(LED_PIN, LOW);
  doorCandidate = digitalRead(MC38_PIN); doorChangedAt = millis(); dht.begin();
  setenv("TZ", "UTC0", 1); tzset();
  prefix = "smart-door/v1/doors/" + String(ControlConfig::DOOR_ID) + "/devices/" + ControlConfig::DEVICE_CODE;
  identity = String(ControlConfig::DOOR_ID) + ":" + ControlConfig::DEVICE_CODE + ":" + ControlConfig::CREDENTIAL_ID;
  configured = strlen(ControlConfig::WIFI_SSID) > 0 && strlen(ControlConfig::MQTT_HOST) > 0 &&
    ControlConfig::DOOR_ID > 0 && DeviceProtocol::topicCode(ControlConfig::DEVICE_CODE) &&
    strlen(ControlConfig::MQTT_USERNAME) > 0 && strlen(ControlConfig::MQTT_PASSWORD) > 0 &&
    signer.begin(ControlConfig::CREDENTIAL_ID, ControlConfig::SIGNING_KEY_BASE64);
  if (!configured) { Serial.println("[SETUP] Dien node_control_config.local.h bang credential NODE_2"); return; }
  restoreConfiguration();
  if (!actuator.begin(identity)) Serial.println("[NVS] Nhat ky unlock loi; chan lenh mo tu xa");
  if (!alarmHistory.begin(identity)) Serial.println("[NVS] Nhat ky alarm loi; chan coi tu xa");
  if (!alerts.begin(identity)) Serial.println("[NVS] Outbox canh bao loi; van xu ly tai cho");
  sntp_set_time_sync_notification_cb(onClockSync);
  WiFi.mode(WIFI_STA); WiFi.setAutoReconnect(true); WiFi.begin(ControlConfig::WIFI_SSID, ControlConfig::WIFI_PASSWORD);
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  mqtt.setId(String("esp32-control-") + ControlConfig::DEVICE_CODE + "-" + WiFi.macAddress());
  mqtt.setUsernamePassword(ControlConfig::MQTT_USERNAME, ControlConfig::MQTT_PASSWORD);
  mqtt.setCleanSession(true); mqtt.setConnectionTimeout(2000); mqtt.setTimeout(1000); mqtt.onMessage(receiveMessage);
  Serial.println("[SETUP] Node 2: mo 180, tu dong sau 10 giay; dong App/Serial 90. Bao nhiet uu tien giu mo.");
}

void loop() {
  if (!configured) { delay(10); return; }
  // Local sensor/alarm/servo work never depends on the Wi-Fi/MQTT connection.
  updateLocalHardware(); maintainNetwork(); processMessages(); updateLocalHardware();
  queueLocalAlerts(); sendNetworkEvents(); delay(2);
}
