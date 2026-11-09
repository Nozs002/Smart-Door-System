// shared/mqtt_contracts/mqtt_topics.h
#ifndef MQTT_TOPICS_H
#define MQTT_TOPICS_H
#include <Arduino.h>

inline String getAccessRequestTopic(String doorId, String deviceCode) { 
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/events/access-request"; 
}

inline String getTelemetryTopic(String doorId, String deviceCode) { 
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/events/telemetry"; 
}

inline String getCommandTopic(String doorId, String deviceCode) { 
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/commands/+"; 
}

// Sửa topic ACK theo đúng yêu cầu backend (events/command-ack) và ACL
inline String getCommandAckTopic(String doorId, String deviceCode) { 
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/events/command-ack"; 
}

inline String getAccessResultTopic(String doorId, String deviceCode) {
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/commands/access-result"; 
}

inline String getAlertTopic(String doorId, String deviceCode) { 
  return "smart-door/v1/doors/" + doorId + "/devices/" + deviceCode + "/events/alert"; 
}
#endif