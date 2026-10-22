class PageResult<T> {
  const PageResult({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  factory PageResult.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) parse,
  ) => PageResult(
    items: (json['items'] as List<dynamic>)
        .map((item) => parse(item as Map<String, dynamic>))
        .toList(growable: false),
    page: (json['page'] as num).toInt(),
    size: (json['size'] as num).toInt(),
    totalElements: (json['totalElements'] as num).toInt(),
    totalPages: (json['totalPages'] as num).toInt(),
  );

  final List<T> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  bool get hasNext => page + 1 < totalPages;
}

class DoorView {
  const DoorView({
    required this.id,
    required this.name,
    this.location,
    required this.status,
    this.createdAt,
    this.updatedAt,
  });

  factory DoorView.fromJson(Map<String, dynamic> json) => DoorView(
    id: (json['id'] as num).toInt(),
    name: json['name'] as String,
    location: json['location'] as String?,
    status: json['status'] as String,
    createdAt: parseUtcTime(json['createdAt']),
    updatedAt: parseUtcTime(json['updatedAt']),
  );

  final int id;
  final String name;
  final String? location;
  final String status;
  final DateTime? createdAt;
  final DateTime? updatedAt;
}

class DeviceView {
  const DeviceView({
    required this.id,
    required this.doorId,
    required this.deviceCode,
    required this.nodeType,
    required this.lifecycleStatus,
    required this.connectionStatus,
    this.lastHeartbeatAt,
    this.firmwareVersion,
    this.appliedConfigVersion,
    this.appliedPinVersion,
  });

  factory DeviceView.fromJson(Map<String, dynamic> json) => DeviceView(
    id: (json['id'] as num).toInt(),
    doorId: (json['doorId'] as num).toInt(),
    deviceCode: json['deviceCode'] as String,
    nodeType: json['nodeType'] as String,
    lifecycleStatus: json['lifecycleStatus'] as String,
    connectionStatus: json['connectionStatus'] as String,
    lastHeartbeatAt: parseUtcTime(json['lastHeartbeatAt']),
    firmwareVersion: json['firmwareVersion'] as String?,
    appliedConfigVersion: (json['appliedConfigVersion'] as num?)?.toInt(),
    appliedPinVersion: (json['appliedPinVersion'] as num?)?.toInt(),
  );

  final int id;
  final int doorId;
  final String deviceCode;
  final String nodeType;
  final String lifecycleStatus;
  final String connectionStatus;
  final DateTime? lastHeartbeatAt;
  final String? firmwareVersion;
  final int? appliedConfigVersion;
  final int? appliedPinVersion;
}

class LifecycleEvent {
  const LifecycleEvent({
    required this.id,
    required this.eventId,
    required this.deviceId,
    required this.doorId,
    this.actorAccountId,
    required this.eventType,
    this.fromStatus,
    required this.toStatus,
    this.replacementDeviceId,
    this.reason,
    required this.occurredAt,
  });

  factory LifecycleEvent.fromJson(Map<String, dynamic> json) => LifecycleEvent(
    id: (json['id'] as num).toInt(),
    eventId: json['eventId'] as String,
    deviceId: (json['deviceId'] as num).toInt(),
    doorId: (json['doorId'] as num).toInt(),
    actorAccountId: (json['actorAccountId'] as num?)?.toInt(),
    eventType: json['eventType'] as String,
    fromStatus: json['fromStatus'] as String?,
    toStatus: json['toStatus'] as String,
    replacementDeviceId: (json['replacementDeviceId'] as num?)?.toInt(),
    reason: json['reason'] as String?,
    occurredAt: parseUtcTime(json['occurredAt'])!,
  );

  final int id;
  final String eventId;
  final int deviceId;
  final int doorId;
  final int? actorAccountId;
  final String eventType;
  final String? fromStatus;
  final String toStatus;
  final int? replacementDeviceId;
  final String? reason;
  final DateTime occurredAt;
}

/// Backend LocalDateTime values represent UTC even when the JSON omits Z.
DateTime? parseUtcTime(dynamic value) {
  if (value == null) return null;
  final source = value as String;
  final hasZone = RegExp(r'(Z|[+-]\d{2}:?\d{2})$').hasMatch(source);
  return DateTime.parse(hasZone ? source : '${source}Z');
}

String normalizeActivationCode(String value) =>
    value.toUpperCase().replaceAll(RegExp(r'[\s-]'), '');

String? validateActivationCode(String? value) {
  final code = normalizeActivationCode(value ?? '');
  if (!RegExp(r'^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$').hasMatch(code)) {
    return 'Nhập mã gồm 8 ký tự, ví dụ K7MP-4Q2D.';
  }
  return null;
}
