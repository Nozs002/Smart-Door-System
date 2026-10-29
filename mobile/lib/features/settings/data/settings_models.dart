import '../../doors/data/door_models.dart';

class DeviceSyncStatus {
  const DeviceSyncStatus({
    required this.deviceId,
    required this.deviceCode,
    required this.nodeType,
    required this.lifecycleStatus,
    required this.configState,
    required this.credentialsState,
    this.appliedConfigVersion,
    this.appliedCredentialsVersion,
    this.lastHeartbeatAt,
  });

  factory DeviceSyncStatus.fromJson(Map<String, dynamic> json) =>
      DeviceSyncStatus(
        deviceId: (json['deviceId'] as num).toInt(),
        deviceCode: json['deviceCode'] as String,
        nodeType: json['nodeType'] as String,
        lifecycleStatus: json['lifecycleStatus'] as String,
        configState: json['configState'] as String,
        credentialsState: json['credentialsState'] as String,
        appliedConfigVersion: (json['appliedConfigVersion'] as num?)?.toInt(),
        appliedCredentialsVersion: (json['appliedCredentialsVersion'] as num?)
            ?.toInt(),
        lastHeartbeatAt: parseUtcTime(json['lastHeartbeatAt']),
      );

  final int deviceId;
  final String deviceCode;
  final String nodeType;
  final String lifecycleStatus;
  final int? appliedConfigVersion;
  final int? appliedCredentialsVersion;
  final String configState;
  final String credentialsState;
  final DateTime? lastHeartbeatAt;
}

class DoorConfiguration {
  const DoorConfiguration({
    required this.doorId,
    required this.maxFailedAttempts,
    required this.lockoutSeconds,
    required this.temperatureThreshold,
    this.openTimeoutSeconds,
    required this.version,
    required this.credentialsVersion,
    required this.devices,
    this.updatedAt,
  });

  factory DoorConfiguration.fromJson(Map<String, dynamic> json) =>
      DoorConfiguration(
        doorId: (json['doorId'] as num).toInt(),
        maxFailedAttempts: (json['maxFailedAttempts'] as num).toInt(),
        lockoutSeconds: (json['lockoutSeconds'] as num).toInt(),
        temperatureThreshold: (json['temperatureThreshold'] as num).toDouble(),
        openTimeoutSeconds: (json['openTimeoutSeconds'] as num?)?.toInt(),
        version: (json['version'] as num).toInt(),
        credentialsVersion: (json['credentialsVersion'] as num).toInt(),
        devices: (json['devices'] as List<dynamic>? ?? const [])
            .map(
              (item) => DeviceSyncStatus.fromJson(item as Map<String, dynamic>),
            )
            .toList(growable: false),
        updatedAt: parseUtcTime(json['updatedAt']),
      );

  final int doorId;
  final int maxFailedAttempts;
  final int lockoutSeconds;
  final double temperatureThreshold;
  final int? openTimeoutSeconds;
  final int version;
  final int credentialsVersion;
  final List<DeviceSyncStatus> devices;
  final DateTime? updatedAt;
}
