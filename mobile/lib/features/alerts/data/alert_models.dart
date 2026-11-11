import '../../doors/data/door_models.dart';

class AlertView {
  const AlertView({
    required this.id,
    required this.doorId,
    required this.doorName,
    required this.type,
    required this.severity,
    required this.status,
    required this.triggeredAt,
    this.eventId,
    this.sourceDeviceId,
    this.observedTemperature,
    this.resolvedAt,
  });

  factory AlertView.fromJson(Map<String, dynamic> json) => AlertView(
    id: (json['id'] as num).toInt(),
    eventId: json['eventId'] as String?,
    doorId: (json['doorId'] as num).toInt(),
    doorName: json['doorName'] as String,
    sourceDeviceId: (json['sourceDeviceId'] as num?)?.toInt(),
    type: json['type'] as String,
    severity: json['severity'] as String,
    status: json['status'] as String,
    observedTemperature: (json['observedTemperature'] as num?)?.toDouble(),
    triggeredAt: parseUtcTime(json['triggeredAt'])!,
    resolvedAt: parseUtcTime(json['resolvedAt']),
  );

  final int id;
  final String? eventId;
  final int doorId;
  final String doorName;
  final int? sourceDeviceId;
  final String type;
  final String severity;
  final String status;
  final double? observedTemperature;
  final DateTime triggeredAt;
  final DateTime? resolvedAt;

  String get title => switch (type) {
    'HIGH_TEMPERATURE' => 'Nhiệt độ vượt ngưỡng',
    'TOO_MANY_FAILURES' => 'Xác thực sai nhiều lần',
    'DOOR_OPEN_TOO_LONG' => 'Cửa mở quá lâu',
    _ => 'Cảnh báo',
  };
}
