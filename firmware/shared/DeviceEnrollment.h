#pragma once

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// Prepared once at the workshop. Door binding and household Wi-Fi arrive at runtime.
class DeviceEnrollment {
 public:
  String wifiSsid, wifiPassword, mqttHost, deviceCode, credentialId, username, password, signingKey;
  uint32_t doorId = 0;
  uint16_t mqttPort = 1884;
  bool factoryMode() const { return !unitCode.isEmpty(); }

  void factory(const char* unit,const char* bootstrap,const char* setup,const char* url,const char* ca,const char* role) {
    unitCode = unit; bootstrapSecret = bootstrap; setupCode = setup;
    endpoint = url; rootCa = ca; nodeType = role;
    doorId=0; wifiSsid=""; wifiPassword=""; deviceCode=""; credentialId="";
    username=""; password=""; signingKey=""; mqttHost="";
  }
  void legacy(const char* ssid,const char* pass,const char* host,uint16_t port,uint32_t door,const char* code,
              const char* credential,const char* user,const char* secret,const char* signing) {
    wifiSsid=ssid; wifiPassword=pass; mqttHost=host; mqttPort=port; doorId=door;
    deviceCode=code; credentialId=credential; username=user; password=secret; signingKey=signing;
  }
  bool begin() {
    if (unitCode.isEmpty()) return !wifiSsid.isEmpty();
    if (setupCode.length()<12 || bootstrapSecret.length()<32 || !prefs.begin("door-enroll",false)) return false;
    pinMode(0,INPUT_PULLUP);
    JsonDocument stored;
    String value=prefs.getString("network","");
    if (!deserializeJson(stored,value) && stored["unitCode"].as<String>()==unitCode) {
      wifiSsid=stored["ssid"].as<String>(); wifiPassword=stored["password"].as<String>();
    }
    stored.clear(); value=prefs.getString("identity","");
    bool ready=!deserializeJson(stored,value) && stored["unitCode"].as<String>()==unitCode && load(stored);
    WiFi.onEvent([](WiFiEvent_t, WiFiEventInfo_t info) {
      Serial.printf("[WIFI] Disconnected (reason=%u)\n", info.wifi_sta_disconnected.reason);
    }, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
    WiFi.onEvent([](WiFiEvent_t) {
      Serial.print("[WIFI] Connected, IP: "); Serial.println(WiFi.localIP());
    }, ARDUINO_EVENT_WIFI_STA_GOT_IP);
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    if (!wifiSsid.isEmpty()) WiFi.begin(wifiSsid.c_str(),wifiPassword.c_str());
    const char* headers[]={"X-Setup-Code"}; server.collectHeaders(headers,1);
    server.on("/api/info",HTTP_GET,[this]() {
      JsonDocument doc; doc["unitCode"]=unitCode; doc["nodeType"]=nodeType;
      doc["wifiConnected"]=WiFi.status()==WL_CONNECTED; doc["enrolled"]=doorId>0;
      send(doc);
    });
    server.on("/api/networks",HTTP_GET,[this]() {
      if (!authorized()) return;
      JsonDocument doc; auto list=doc["networks"].to<JsonArray>();
      int count=WiFi.scanComplete();
      if (count>=0) {
        for (int i=0;i<count;++i) list.add(WiFi.SSID(i));
        WiFi.scanDelete();
      } else if (count==WIFI_SCAN_FAILED) WiFi.scanNetworks(true);
      doc["scanning"]=count<0; send(doc);
    });
    server.on("/api/wifi",HTTP_POST,[this]() {
      if (!authorized()) return;
      JsonDocument doc;
      if (server.arg("plain").length()>512 || deserializeJson(doc,server.arg("plain")) ||
          !doc["ssid"].is<const char*>() || !doc["password"].is<const char*>()) {
        server.send(400,"application/json","{\"error\":\"Invalid Wi-Fi data\"}"); return;
      }
      String nextSsid=doc["ssid"].as<String>(), nextPassword=doc["password"].as<String>();
      if (nextSsid.isEmpty() || nextSsid.length()>32 || nextPassword.length()>63 ||
          (!nextPassword.isEmpty() && nextPassword.length()<8)) {
        server.send(400,"application/json","{\"error\":\"Invalid SSID/password length\"}"); return;
      }
      doc["unitCode"]=unitCode;
      String record; serializeJson(doc,record);
      if (prefs.putString("network",record)!=record.length() || prefs.getString("network","")!=record) {
        server.send(500,"application/json","{\"error\":\"Storage failed\"}"); return;
      }
      wifiSsid=nextSsid; wifiPassword=nextPassword; reconnectAt=millis()+500;
      server.send(202,"application/json","{\"accepted\":true}");
    });
    if (wifiSsid.isEmpty()) startPortal();
    return ready && !wifiSsid.isEmpty();
  }
  void poll(bool applicationReady) {
    if (unitCode.isEmpty()) return;
    if (digitalRead(0)==LOW) {
      if (!buttonSince) buttonSince=millis();
      if (!portal && millis()-buttonSince>=3000) startPortal();
    } else buttonSince=0;
    if (portal) {
      server.handleClient();
      if (millis()-portalSince>1200000UL) { server.stop(); WiFi.softAPdisconnect(true); portal=false; }
    }
    if (reconnectAt && static_cast<int32_t>(millis()-reconnectAt)>=0) {
      // Stop the station's current attempt while keeping the setup AP available.
      if (!WiFi.enableSTA(false)) {
        Serial.println("[WIFI] Could not stop the previous connection attempt");
        reconnectAt=millis()+5000;
        return;
      }
      reconnectAt=0;
      lastEnroll=0;
      if (WiFi.begin(wifiSsid.c_str(),wifiPassword.c_str())==WL_CONNECT_FAILED) {
        Serial.println("[WIFI] Could not start connection with the saved configuration");
        reconnectAt=millis()+5000;
      }
    }
    if (restartAt && static_cast<int32_t>(millis()-restartAt)>=0) ESP.restart();
    if (applicationReady || reconnectAt || restartAt || WiFi.status()!=WL_CONNECTED || millis()-lastEnroll<10000) return;
    lastEnroll=millis(); enroll();
  }
 private:
  String unitCode,bootstrapSecret,setupCode,endpoint,rootCa,nodeType;
  Preferences prefs;
  WebServer server{80};
  bool portal=false;
  unsigned long portalSince=0,buttonSince=0,reconnectAt=0,restartAt=0,lastEnroll=0;
  bool authorized() {
    if (server.header("X-Setup-Code")==setupCode) return true;
    server.send(403,"application/json","{\"error\":\"Invalid setup code\"}"); return false;
  }
  void send(JsonDocument& doc) {
    String body; serializeJson(doc,body); server.sendHeader("Cache-Control","no-store");
    server.send(200,"application/json",body);
  }
  void startPortal() {
    WiFi.mode(WIFI_AP_STA);
    if (WiFi.softAP(("SmartDoor-"+unitCode.substring(0,8)).c_str(),setupCode.c_str())) {
      portal=true; portalSince=millis(); server.begin();
      Serial.println("[SETUP] Wi-Fi setup ready at 192.168.4.1. Use the printed setup code.");
    }
  }
  bool load(JsonDocument& doc) {
    if (!doc["doorId"].is<uint32_t>() || doc["doorId"].as<uint32_t>()==0 ||
        doc["nodeType"].as<String>()!=nodeType || !doc["mqttPort"].is<uint16_t>()) return false;
    const char* fields[]={"deviceCode","credentialId","username","password","signingKey","mqttHost"};
    for (auto field:fields) if (!doc[field].is<const char*>() || !strlen(doc[field])) return false;
    doorId=doc["doorId"]; mqttPort=doc["mqttPort"]; deviceCode=doc["deviceCode"].as<String>();
    credentialId=doc["credentialId"].as<String>(); username=doc["username"].as<String>();
    password=doc["password"].as<String>(); signingKey=doc["signingKey"].as<String>(); mqttHost=doc["mqttHost"].as<String>();
    return mqttPort>0;
  }
  void enroll() {
    WiFiClient plain;
    WiFiClientSecure secure;
    HTTPClient http; http.setTimeout(3000); http.setConnectTimeout(2000); http.setFollowRedirects(HTTPC_DISABLE_FOLLOW_REDIRECTS);
    bool opened=false;
    if (endpoint.startsWith("https://") && !rootCa.isEmpty()) {
      configTime(0,0,"pool.ntp.org","time.google.com");
      secure.setCACert(rootCa.c_str()); opened=http.begin(secure,endpoint);
    } else if (endpoint.startsWith("http://")) opened=http.begin(plain,endpoint);
    if (!opened) return;
    JsonDocument request; request["unitCode"]=unitCode; request["bootstrapSecret"]=bootstrapSecret;
    String body; serializeJson(request,body); http.addHeader("Content-Type","application/json");
    int status=http.POST(body); body="";
    if (status!=200) Serial.printf("[SETUP] Enrollment failed (HTTP result=%d)\n", status);
    if (status==200 && http.getSize()<=4096) {
      JsonDocument response;
      if (!deserializeJson(response,http.getString())) {
        JsonDocument doc; doc.set(response["data"]);
        if (load(doc)) {
          doc["unitCode"]=unitCode;
          String record; serializeJson(doc,record);
          if (prefs.putString("identity",record)==record.length() && prefs.getString("identity","")==record)
            restartAt=millis()+500;
        }
      }
    }
    http.end();
  }
};
