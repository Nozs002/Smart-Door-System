#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <Arduino.h>
#include <WiFi.h>

// Kết nối ESP32 với Wi-Fi
void connectWiFi(const char* ssid, const char* password);

// Kiểm tra trạng thái Wi-Fi
bool isWiFiConnected();

#endif