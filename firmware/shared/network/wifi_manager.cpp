// test wifi node 2
#include <WiFi.h>
#include <PubSubClient.h>

#include "wifi_config.local.h"

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

unsigned long lastAttempt = 0;

void setup() {
  Serial.begin(115200);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  mqtt.setServer(MQTT_HOST, MQTT_PORT);

  Serial.println("Dang ket noi Wi-Fi...");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    delay(10);
    return;
  }

  if (!mqtt.connected()) {
    unsigned long now = millis();

    if (now - lastAttempt >= 5000) {
      lastAttempt = now;

      Serial.print("Wi-Fi IP: ");
      Serial.println(WiFi.localIP());

      String clientId = "esp32-node2-";
      clientId += WiFi.macAddress();

      if (mqtt.connect(
            clientId.c_str(),
            MQTT_USERNAME,
            MQTT_PASSWORD)) {
        Serial.println("MQTT KET NOI THANH CONG");
      } else {
        Serial.print("MQTT loi, ma: ");
        Serial.println(mqtt.state());
      }
    }
  } else {
    mqtt.loop();
  }

  delay(10);
}
