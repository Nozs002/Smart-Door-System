import '../core/api_client.dart';
import '../features/doors/data/door_models.dart';
import '../features/doors/data/door_repository.dart';

/// Local simulation for exploring the UI before authentication is integrated.
///
/// No HTTP client, token, backend, database or physical device is used. This is
/// sample behavior for the preview, not evidence that the backend works.
class PreviewDoorRepository implements DoorRepository {
  PreviewDoorRepository({DateTime Function()? now})
    : _now = now ?? DateTime.now {
    _seed();
  }

  static const demoActivationCode = 'K7MP-4Q2D';
  static const pageSize = 5;

  /// Controlled by the preview toolbar, simulating the future integration.
  bool readinessVerified = false;
  bool revocationVerified = false;

  final DateTime Function() _now;
  final Map<int, DoorView> _doors = {};
  final Map<int, DeviceView> _devices = {};
  final List<LifecycleEvent> _events = [];
  int _nextEventId = 1;
  bool _claimedDemoCode = false;

  DateTime get _utcNow => _now().toUtc();

  void _seed() {
    final now = _utcNow;
    final created = now.subtract(const Duration(days: 3));
    _doors.addAll({
      1: DoorView(
        id: 1,
        name: 'Cửa chính',
        location: 'Tầng trệt',
        status: 'ACTIVE',
        createdAt: created,
        updatedAt: created,
      ),
      2: DoorView(
        id: 2,
        name: 'Cửa kho',
        location: 'Tầng 1',
        status: 'SETUP_PENDING',
        createdAt: created,
        updatedAt: created,
      ),
    });
    _devices.addAll({
      11: DeviceView(
        id: 11,
        doorId: 1,
        deviceCode: 'DEMO-READER-01',
        nodeType: 'NODE_1',
        lifecycleStatus: 'ACTIVE',
        connectionStatus: 'ONLINE',
        lastHeartbeatAt: now.subtract(const Duration(seconds: 10)),
        firmwareVersion: 'preview-1.0',
        appliedConfigVersion: 3,
        appliedPinVersion: 2,
      ),
      12: DeviceView(
        id: 12,
        doorId: 1,
        deviceCode: 'DEMO-LOCK-01',
        nodeType: 'NODE_2',
        lifecycleStatus: 'ACTIVE',
        connectionStatus: 'OFFLINE',
        lastHeartbeatAt: now.subtract(const Duration(minutes: 8)),
        firmwareVersion: 'preview-1.0',
        appliedConfigVersion: 3,
      ),
      13: const DeviceView(
        id: 13,
        doorId: 1,
        deviceCode: 'DEMO-READER-REPLACEMENT',
        nodeType: 'NODE_1',
        lifecycleStatus: 'PROVISIONED',
        connectionStatus: 'UNKNOWN',
      ),
      21: const DeviceView(
        id: 21,
        doorId: 2,
        deviceCode: 'DEMO-READER-02',
        nodeType: 'NODE_1',
        lifecycleStatus: 'PROVISIONED',
        connectionStatus: 'UNKNOWN',
      ),
      22: DeviceView(
        id: 22,
        doorId: 2,
        deviceCode: 'DEMO-LOCK-02',
        nodeType: 'NODE_2',
        lifecycleStatus: 'PROVISIONED',
        connectionStatus: 'ONLINE',
        lastHeartbeatAt: now.subtract(const Duration(seconds: 15)),
        firmwareVersion: 'preview-1.0',
      ),
    });

    // More than one page, including multiple transitions for the node filter.
    for (final device in _devices.values) {
      _record(
        device,
        'PROVISIONED',
        null,
        'PROVISIONED',
        'Dữ liệu mẫu: đăng ký thiết bị.',
        at: created,
        technician: true,
      );
      if (device.lifecycleStatus == 'ACTIVE') {
        _record(
          device,
          'ACTIVATED',
          'PROVISIONED',
          'ACTIVE',
          'Dữ liệu mẫu: kích hoạt thiết bị.',
          at: created.add(const Duration(hours: 1)),
        );
      }
    }
    _record(
      _devices[11]!,
      'DISABLED',
      'ACTIVE',
      'DISABLED',
      'Dữ liệu mẫu: bảo trì đầu đọc.',
      at: created.add(const Duration(days: 1)),
    );
    _record(
      _devices[11]!,
      'REACTIVATED',
      'DISABLED',
      'ACTIVE',
      'Dữ liệu mẫu: hoàn thành bảo trì.',
      at: created.add(const Duration(days: 1, hours: 1)),
    );
  }

  @override
  Future<PageResult<DoorView>> listDoors({int page = 0}) async {
    final doors = _doors.values.toList()
      ..sort((left, right) => left.id.compareTo(right.id));
    return _page(doors, page);
  }

  @override
  Future<DoorView> claimDoor(String activationCode) async {
    if (normalizeActivationCode(activationCode) !=
        normalizeActivationCode(demoActivationCode)) {
      throw const ApiException(
        'Mã mẫu không hợp lệ. Dùng K7MP-4Q2D để thử thêm cửa.',
        statusCode: 400,
      );
    }
    if (_claimedDemoCode) return _door(3);
    final now = _utcNow;
    final door = DoorView(
      id: 3,
      name: 'Cửa mới nhận',
      location: 'Bộ cửa thử nghiệm',
      status: 'SETUP_PENDING',
      createdAt: now,
      updatedAt: now,
    );
    _doors[3] = door;
    for (final (id, role, code) in [
      (31, 'NODE_1', 'DEMO-READER-03'),
      (32, 'NODE_2', 'DEMO-LOCK-03'),
    ]) {
      final device = DeviceView(
        id: id,
        doorId: door.id,
        deviceCode: code,
        nodeType: role,
        lifecycleStatus: 'PROVISIONED',
        connectionStatus: 'UNKNOWN',
      );
      _devices[id] = device;
      _record(
        device,
        'PROVISIONED',
        null,
        'PROVISIONED',
        'Dữ liệu mẫu: bộ cửa được đăng ký trước khi nhận quyền sở hữu.',
        at: now,
        technician: true,
      );
    }
    _claimedDemoCode = true;
    return door;
  }

  @override
  Future<DoorView> getDoor(int doorId) async => _door(doorId);

  @override
  Future<DoorView> updateDoor(int doorId, String name, String? location) async {
    final current = _door(doorId);
    final cleanName = name.trim();
    final cleanLocation = location?.trim();
    if (cleanName.isEmpty || cleanName.length > 100) {
      throw const ApiException(
        'Tên cửa phải có từ 1 đến 100 ký tự.',
        statusCode: 400,
      );
    }
    if (cleanLocation != null && cleanLocation.length > 255) {
      throw const ApiException(
        'Vị trí cửa không được quá 255 ký tự.',
        statusCode: 400,
      );
    }
    final updated = DoorView(
      id: current.id,
      name: cleanName,
      location: cleanLocation == '' ? null : cleanLocation,
      status: current.status,
      createdAt: current.createdAt,
      updatedAt: _utcNow,
    );
    _doors[doorId] = updated;
    return updated;
  }

  @override
  Future<List<DeviceView>> listDevices(int doorId) async {
    _door(doorId);
    final devices =
        _devices.values.where((device) => device.doorId == doorId).toList()
          ..sort((left, right) => left.id.compareTo(right.id));
    return List.unmodifiable(devices);
  }

  @override
  Future<DeviceView> getDevice(int doorId, int deviceId) async =>
      _device(doorId, deviceId);

  @override
  Future<DeviceView> changeLifecycle(
    int doorId,
    int deviceId,
    String action,
    String reason,
  ) async {
    final current = _device(doorId, deviceId);
    final cleanReason = _reason(reason, required: action == 'RETIRE');
    final (requiredStatus, targetStatus, eventType) = switch (action) {
      'ACTIVATE' => ('PROVISIONED', 'ACTIVE', 'ACTIVATED'),
      'DISABLE' => ('ACTIVE', 'DISABLED', 'DISABLED'),
      'REACTIVATE' => ('DISABLED', 'ACTIVE', 'REACTIVATED'),
      'RETIRE' => (current.lifecycleStatus, 'RETIRED', 'RETIRED'),
      _ => throw const ApiException(
        'Thao tác vòng đời không hợp lệ.',
        statusCode: 400,
      ),
    };
    if (current.lifecycleStatus != requiredStatus ||
        (action == 'RETIRE' &&
            !['ACTIVE', 'DISABLED'].contains(current.lifecycleStatus))) {
      throw const ApiException(
        'Không thể thực hiện thao tác từ trạng thái hiện tại.',
        statusCode: 409,
      );
    }
    if (targetStatus == 'ACTIVE') {
      _requireReadiness();
      _requireFreeRole(current);
    }
    final updated = _withStatus(current, targetStatus);
    _devices[deviceId] = updated;
    _record(
      current,
      eventType,
      current.lifecycleStatus,
      targetStatus,
      cleanReason,
    );
    _reconcileDoor(doorId);
    return updated;
  }

  @override
  Future<DeviceView> replaceDevice(
    int doorId,
    int deviceId,
    int replacementDeviceId,
    String reason,
  ) async {
    final current = _device(doorId, deviceId);
    final replacement = _device(doorId, replacementDeviceId);
    final cleanReason = _reason(reason, required: true);
    if (deviceId == replacementDeviceId ||
        !['ACTIVE', 'DISABLED'].contains(current.lifecycleStatus) ||
        replacement.lifecycleStatus != 'PROVISIONED' ||
        current.nodeType != replacement.nodeType) {
      throw const ApiException(
        'Cần chọn Node thay thế đã đăng ký, cùng cửa và cùng vai trò.',
        statusCode: 409,
      );
    }
    _requireReadiness();
    if (!revocationVerified) {
      throw const ApiException(
        'Mô phỏng: chưa xác nhận thu hồi quyền của Node cũ. '
        'Bật xác nhận trong bảng điều khiển thử giao diện.',
        statusCode: 409,
      );
    }
    _requireFreeRole(replacement, retiringId: current.id);

    // Validate everything before mutating either device or writing history.
    final at = _utcNow;
    final updated = _withStatus(replacement, 'ACTIVE');
    _devices[deviceId] = _withStatus(current, 'RETIRED');
    _devices[replacementDeviceId] = updated;
    _record(
      current,
      'REPLACED',
      current.lifecycleStatus,
      'RETIRED',
      cleanReason,
      at: at,
      replacementDeviceId: replacementDeviceId,
    );
    _record(
      replacement,
      'ACTIVATED',
      'PROVISIONED',
      'ACTIVE',
      cleanReason,
      at: at,
    );
    _reconcileDoor(doorId);
    return updated;
  }

  @override
  Future<PageResult<LifecycleEvent>> lifecycleEvents(
    int doorId, {
    int page = 0,
    int? deviceId,
  }) async {
    _door(doorId);
    if (deviceId != null) _device(doorId, deviceId);
    final events =
        _events
            .where(
              (event) =>
                  event.doorId == doorId &&
                  (deviceId == null || event.deviceId == deviceId),
            )
            .toList()
          ..sort((left, right) {
            final time = right.occurredAt.compareTo(left.occurredAt);
            return time == 0 ? right.id.compareTo(left.id) : time;
          });
    return _page(events, page);
  }

  DoorView _door(int doorId) {
    final door = _doors[doorId];
    if (door == null) {
      throw const ApiException('Không tìm thấy cửa.', statusCode: 404);
    }
    return door;
  }

  DeviceView _device(int doorId, int deviceId) {
    _door(doorId);
    final device = _devices[deviceId];
    if (device == null || device.doorId != doorId) {
      throw const ApiException('Không tìm thấy thiết bị.', statusCode: 404);
    }
    return device;
  }

  String? _reason(String value, {required bool required}) {
    final clean = value.trim();
    if ((required && clean.isEmpty) || clean.length > 500) {
      throw const ApiException(
        'Vui lòng nhập lý do hợp lệ, tối đa 500 ký tự.',
        statusCode: 400,
      );
    }
    return clean.isEmpty ? null : clean;
  }

  void _requireReadiness() {
    if (!readinessVerified) {
      throw const ApiException(
        'Mô phỏng: chưa xác nhận danh tính, cấu hình và PIN. '
        'Bật xác nhận trong bảng điều khiển thử giao diện.',
        statusCode: 409,
      );
    }
  }

  void _requireFreeRole(DeviceView device, {int? retiringId}) {
    if (_devices.values.any(
      (other) =>
          other.id != device.id &&
          other.id != retiringId &&
          other.doorId == device.doorId &&
          other.nodeType == device.nodeType &&
          other.lifecycleStatus == 'ACTIVE',
    )) {
      throw const ApiException(
        'Đã có Node đang hoạt động ở vai trò này. Hãy dùng thao tác thay thế.',
        statusCode: 409,
      );
    }
  }

  DeviceView _withStatus(DeviceView current, String status) => DeviceView(
    id: current.id,
    doorId: current.doorId,
    deviceCode: current.deviceCode,
    nodeType: current.nodeType,
    lifecycleStatus: status,
    connectionStatus: current.connectionStatus,
    lastHeartbeatAt: current.lastHeartbeatAt,
    firmwareVersion: current.firmwareVersion,
    appliedConfigVersion: current.appliedConfigVersion,
    appliedPinVersion: current.appliedPinVersion,
  );

  void _reconcileDoor(int doorId) {
    final current = _door(doorId);
    if (current.status == 'DISABLED') return;
    final activeRoles = _devices.values
        .where(
          (device) =>
              device.doorId == doorId && device.lifecycleStatus == 'ACTIVE',
        )
        .map((device) => device.nodeType)
        .toSet();
    final status = activeRoles.containsAll(['NODE_1', 'NODE_2'])
        ? 'ACTIVE'
        : 'SETUP_PENDING';
    _doors[doorId] = DoorView(
      id: current.id,
      name: current.name,
      location: current.location,
      status: status,
      createdAt: current.createdAt,
      updatedAt: _utcNow,
    );
  }

  void _record(
    DeviceView device,
    String eventType,
    String? fromStatus,
    String toStatus,
    String? reason, {
    DateTime? at,
    bool technician = false,
    int? replacementDeviceId,
  }) {
    final id = _nextEventId++;
    _events.add(
      LifecycleEvent(
        id: id,
        eventId: 'preview-event-$id',
        doorId: device.doorId,
        deviceId: device.id,
        actorAccountId: technician ? null : 1,
        eventType: eventType,
        fromStatus: fromStatus,
        toStatus: toStatus,
        replacementDeviceId: replacementDeviceId,
        reason: reason,
        occurredAt: at ?? _utcNow,
      ),
    );
  }

  PageResult<T> _page<T>(List<T> items, int page) {
    if (page < 0) {
      throw const ApiException('Số trang không hợp lệ.', statusCode: 400);
    }
    return PageResult(
      items: List.unmodifiable(items.skip(page * pageSize).take(pageSize)),
      page: page,
      size: pageSize,
      totalElements: items.length,
      totalPages: (items.length / pageSize).ceil(),
    );
  }
}
