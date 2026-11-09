#include <WiFi.h>
#include <MQTT.h>
#include <ArduinoJson.h>
#include <time.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <Keypad.h>

#include "../shared/config.h"
#include "../shared/mqtt_contracts/mqtt_topics.h"
#include "../shared/security/envelope.h" 
#include "../shared/network/wifi_manager.h"

WiFiClient net;
MQTTClient mqtt(1024);

#define SS_PIN 5
#define RST_PIN 15
MFRC522 rfid(SS_PIN, RST_PIN);
LiquidCrystal_I2C lcd(0x27, 16, 2);

const byte ROWS = 4;
const byte COLS = 3;
char keys[ROWS][COLS] = {{'1','2','3'}, {'4','5','6'}, {'7','8','9'}, {'*','0','#'}};
byte rowPins[ROWS] = {13, 14, 27, 26};
byte colPins[COLS] = {25, 33, 32};
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);
String enteredPin = "";

void sendAccessRequest(String method, String value) {
  StaticJsonDocument<256> doc;
  doc["schemaVersion"] = 1;
  doc["eventId"] = generateUUID(); 
  doc["method"] = method;
  doc["value"] = value;
  doc["occurredAt"] = getISO8601Time(); // ĐÃ SỬA: Dùng UTC ISO-8601
  
  String rawPayload; 
  serializeJson(doc, rawPayload);
  
  String topic = getAccessRequestTopic(DOOR_ID, DEVICE_CODE_NODE_1);
  // ĐÃ SỬA: Gọi đúng tên hàm buildSignedEnvelope
  String finalEnvelope = buildSignedEnvelope(topic, CREDENTIAL_ID_NODE_1, rawPayload, HMAC_SECRET_NODE_1);
  
  mqtt.publish(topic, finalEnvelope, false, 1);
  Serial.println("[AUTH] Đã gửi yêu cầu xác thực: " + method);
}

void onMessage(String &topic, String &payload) {
  if (topic == getAccessResultTopic(DOOR_ID, DEVICE_CODE_NODE_1)) {
    StaticJsonDocument<512> doc;
    deserializeJson(doc, payload);
    
    String raw = doc["payload"] | payload; 
    StaticJsonDocument<256> resultDoc;
    deserializeJson(resultDoc, raw);
    
    lcd.clear();
    String result = resultDoc["result"] | resultDoc["status"] | "FAILED";
    if (result == "SUCCESS" || result == "GRANTED") {
      lcd.print("XAC THUC DUNG!");
    } else {
      lcd.print("TU CHOI TRUY CAP");
    }
    delay(2000);
    lcd.clear(); lcd.print("San sang...");
  }
}

void setup() {
  Serial.begin(115200);
  SPI.begin(); rfid.PCD_Init();
  lcd.init(); lcd.backlight(); lcd.print("San sang...");
  
  connectWiFi(WIFI_SSID, WIFI_PASSWORD);
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  
  mqtt.begin(MQTT_HOST, MQTT_PORT, net);
  mqtt.onMessage(onMessage);
}

void reconnect() {
  while (!mqtt.connected()) {
    if (mqtt.connect(MQTT_CLIENT_ID_NODE_1, MQTT_USER_NODE_1, MQTT_PASSWORD_NODE_1)) {
      Serial.println("Node 1: MQTT OK!");
      mqtt.subscribe(getAccessResultTopic(DOOR_ID, DEVICE_CODE_NODE_1), 1); 
    } else delay(5000);
  }
}

void loop() {
  mqtt.loop();
  if (!mqtt.connected()) { reconnect(); }

  char key = keypad.getKey();
  if (key) {
    if (key == '#') {
      if (enteredPin.length() > 0) {
        lcd.clear(); lcd.print("Dang gui PIN...");
        sendAccessRequest("PIN", enteredPin);
      }
      enteredPin = ""; 
      delay(1000);
      lcd.clear(); lcd.print("San sang...");
    } else if (key == '*') {
       enteredPin = ""; 
       lcd.setCursor(0, 1); lcd.print("                ");
    } else {
      enteredPin += key;
      lcd.setCursor(0, 1); lcd.print("PIN: ");
      for(size_t i = 0; i < enteredPin.length(); i++) lcd.print("*");
    }
  }
  
  if (rfid.PICC_IsNewCardPresent() && rfid.PICC_ReadCardSerial()) {
    String uid = "";
    for (byte i = 0; i < rfid.uid.size; i++) {
      uid += String(rfid.uid.uidByte[i] < 0x10 ? "0" : "");
      uid += String(rfid.uid.uidByte[i], HEX);
    }
    uid.toUpperCase();
    
    lcd.clear(); lcd.print("Dang quet the...");
    sendAccessRequest("RFID", uid);
    
    rfid.PICC_HaltA(); delay(1500); 
    lcd.clear(); lcd.print("San sang...");
  }
}