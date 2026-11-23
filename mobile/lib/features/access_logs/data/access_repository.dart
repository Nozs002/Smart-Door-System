import '../../../core/api_client.dart';
import '../../doors/data/door_models.dart';

class AccessLogEntry {
  const AccessLogEntry({
    required this.method,
    required this.result,
    this.reason,
    this.cardholderName,
    this.occurredAt,
    this.receivedAt,
    this.correlationId,
  });
  factory AccessLogEntry.fromJson(Map<String, dynamic> j) => AccessLogEntry(
    method: j['method'] as String,
    result: j['result'] as String,
    reason: j['reason'] as String?,
    cardholderName: j['cardholderName'] as String?,
    occurredAt: parseUtcTime(j['occurredAt']),
    receivedAt: parseUtcTime(j['receivedAt']),
    correlationId: j['correlationId'] as String?,
  );
  final String method, result;
  final String? reason, cardholderName, correlationId;
  final DateTime? occurredAt, receivedAt;
}

class DoorMonitoring {
  const DoorMonitoring({
    this.temperature,
    this.humidity,
    required this.doorState,
    required this.lockState,
    required this.observedAt,
    required this.fresh,
  });
  factory DoorMonitoring.fromJson(Map<String, dynamic> j) => DoorMonitoring(
    temperature: (j['temperature'] as num?)?.toDouble(),
    humidity: (j['humidity'] as num?)?.toDouble(),
    doorState: j['doorState'] as String,
    lockState: j['lockState'] as String,
    observedAt: parseUtcTime(j['observedAt'])!,
    fresh: j['fresh'] as bool,
  );
  final double? temperature, humidity;
  final String doorState, lockState;
  final DateTime observedAt;
  final bool fresh;
  bool get isCurrent =>
      fresh && DateTime.now().toUtc().difference(observedAt).inSeconds <= 60;
}

class AccessSummary {
  const AccessSummary(this.cardholderCount, this.latestAccess);
  factory AccessSummary.fromJson(Map<String, dynamic> j) => AccessSummary(
    (j['cardholderCount'] as num).toInt(),
    j['latestAccess'] == null
        ? null
        : AccessLogEntry.fromJson(j['latestAccess'] as Map<String, dynamic>),
  );
  final int cardholderCount;
  final AccessLogEntry? latestAccess;
}

abstract class AccessRepository {
  Future<PageResult<AccessLogEntry>> logs(
    int doorId, {
    int page = 0,
    String? method,
    String? result,
  });
  Future<DoorMonitoring?> monitoring(int doorId);
  Future<AccessSummary> summary(int doorId);
}

class RestAccessRepository implements AccessRepository {
  RestAccessRepository(this.client);
  final ApiClient client;
  @override
  Future<PageResult<AccessLogEntry>> logs(
    int doorId, {
    int page = 0,
    String? method,
    String? result,
  }) async => PageResult.fromJson(
    await client.request(
      'GET',
      '/api/v1/doors/$doorId/access-logs',
      query: {
        'page': '$page',
        'size': '20',
        'method': ?method,
        'result': ?result,
      },
    ) as Map<String, dynamic>,
    AccessLogEntry.fromJson,
  );
  @override
  Future<DoorMonitoring?> monitoring(int doorId) async {
    final value = await client.request(
      'GET',
      '/api/v1/doors/$doorId/monitoring',
    );
    return value == null
        ? null
        : DoorMonitoring.fromJson(value as Map<String, dynamic>);
  }

  @override
  Future<AccessSummary> summary(int doorId) async => AccessSummary.fromJson(
    await client.request('GET', '/api/v1/doors/$doorId/access-summary')
        as Map<String, dynamic>,
  );
}

String accessMethodLabel(String value) =>
    const {
      'RFID': 'Thẻ RFID',
      'PIN': 'Mã PIN',
      'APP': 'Ứng dụng',
      'EXIT_BUTTON': 'Nút mở trong nhà',
      'AUTO_PROXIMITY': 'Cảm biến khoảng cách',
      'FIRE_EMERGENCY': 'Thoát hiểm',
    }[value] ??
    value;
String accessResultLabel(String value) =>
    const {
      'GRANTED': 'Xác thực hợp lệ',
      'DENIED': 'Từ chối xác thực',
      'EXECUTED': 'Đã thực thi lệnh',
      'FAILED': 'Không thực thi lệnh',
      'UNKNOWN': 'Chờ/chưa rõ kết quả',
    }[value] ??
    value;
String accessReasonLabel(String? value) =>
    const {
      'LOCKED_OUT': 'Đang khóa tạm',
      'INVALID_PIN': 'PIN không hợp lệ',
      'INVALID_RFID': 'Thẻ không hợp lệ',
      'QUEUED': 'Đã xếp hàng mở khóa',
      'CONTROL_UNAVAILABLE': 'Chưa thể gửi lệnh mở khóa',
      'SECURITY_DISABLED': 'Bảo mật đang tắt; cửa ở chế độ giữ mở',
      'PENDING': 'Đang chờ gửi',
      'TIMED_OUT': 'Hết thời gian chờ xác nhận',
      'REJECTED': 'Thiết bị từ chối',
      'FAILED': 'Gửi lệnh thất bại',
      'UNKNOWN': 'Chưa rõ kết quả gửi',
      'EXECUTED': 'Thiết bị xác nhận thực thi',
      'CLOSE_PENDING': 'Đang chờ gửi lệnh đóng cửa',
      'CLOSE_SENT': 'Đã gửi lệnh đóng cửa',
      'CLOSE_EXECUTED': 'Thiết bị xác nhận đóng cửa',
      'CLOSE_REJECTED': 'Thiết bị từ chối đóng cửa',
      'CLOSE_TIMED_OUT': 'Hết thời gian chờ xác nhận đóng cửa',
      'CLOSE_UNKNOWN': 'Chưa rõ kết quả đóng cửa',
      'CLOSE_FAILED': 'Gửi lệnh đóng cửa thất bại',
    }[value] ??
    value ??
    '';
