#include "wifi_manager.h"

void connectWiFi(const char* ssid, const char* password) {
    Serial.println();
    Serial.println("Dang ket noi Wi-Fi...");

    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println();
    Serial.println("Da ket noi Wi-Fi thanh cong!");

    Serial.print("Dia chi IP cua ESP32: ");
    Serial.println(WiFi.localIP());
}

bool isWiFiConnected() {
    return WiFi.status() == WL_CONNECTED;
}