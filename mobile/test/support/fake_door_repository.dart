import 'package:smart_door_mobile/features/doors/data/door_models.dart';
import 'package:smart_door_mobile/features/doors/data/door_repository.dart';

class FakeDoorRepository implements DoorRepository {
  DoorView door = const DoorView(
    id: 1,
    name: 'Cửa trước',
    location: 'Tầng trệt',
    status: 'SETUP_PENDING',
  );
  List<DoorView> doors = [
    const DoorView(id: 1, name: 'Cửa trước', status: 'SETUP_PENDING'),
  ];
  List<DeviceView> devices = [
    const DeviceView(
      id: 11,
      doorId: 1,
      deviceCode: 'NODE-READER',
      nodeType: 'NODE_1',
      lifecycleStatus: 'DISABLED',
      connectionStatus: 'ONLINE',
    ),
    const DeviceView(
      id: 12,
      doorId: 1,
      deviceCode: 'NODE-LOCK',
      nodeType: 'NODE_2',
      lifecycleStatus: 'PROVISIONED',
      connectionStatus: 'UNKNOWN',
    ),
  ];
  Object? doorError;
  Object? lifecycleError;
  String? claimedCode;
  final lifecycleCalls = <String>[];
  final historyRequests = <({int page, int? deviceId})>[];

  @override
  Future<DoorView> claimDoor(String activationCode) async {
    claimedCode = activationCode;
    doors = [door];
    return door;
  }

  @override
  Future<DeviceView> getDevice(int doorId, int deviceId) async =>
      devices.firstWhere((device) => device.id == deviceId);

  @override
  Future<DoorView> getDoor(int doorId) async {
    if (doorError != null) throw doorError!;
    return door;
  }

  @override
  Future<PageResult<LifecycleEvent>> lifecycleEvents(
    int doorId, {
    int page = 0,
    int? deviceId,
  }) async {
    historyRequests.add((page: page, deviceId: deviceId));
    return PageResult(
      items: [
        LifecycleEvent(
          id: page + 1,
          eventId: 'event-$page',
          deviceId: deviceId ?? 11,
          doorId: doorId,
          eventType: 'PROVISIONED',
          toStatus: 'PROVISIONED',
          reason: 'Sự kiện $page',
          occurredAt: DateTime.utc(2026, 10, 16),
        ),
      ],
      page: page,
      size: 1,
      totalElements: 2,
      totalPages: 2,
    );
  }

  @override
  Future<List<DeviceView>> listDevices(int doorId) async => devices;

  @override
  Future<PageResult<DoorView>> listDoors({int page = 0}) async => PageResult(
    items: doors,
    page: page,
    size: 20,
    totalElements: doors.length,
    totalPages: doors.isEmpty ? 0 : 1,
  );

  @override
  Future<DeviceView> replaceDevice(
    int doorId,
    int deviceId,
    int replacementDeviceId,
    String reason,
  ) async {
    await changeLifecycle(doorId, deviceId, 'RETIRE', reason);
    return changeLifecycle(doorId, replacementDeviceId, 'ACTIVATE', reason);
  }

  @override
  Future<DeviceView> changeLifecycle(
    int doorId,
    int deviceId,
    String action,
    String reason,
  ) async {
    lifecycleCalls.add(action);
    if (lifecycleError != null) throw lifecycleError!;
    final old = devices.firstWhere((device) => device.id == deviceId);
    final updated = DeviceView(
      id: old.id,
      doorId: old.doorId,
      deviceCode: old.deviceCode,
      nodeType: old.nodeType,
      lifecycleStatus: switch (action) {
        'RETIRE' => 'RETIRED',
        'DISABLE' => 'DISABLED',
        _ => 'ACTIVE',
      },
      connectionStatus: old.connectionStatus,
    );
    devices = [
      for (final device in devices)
        if (device.id == deviceId) updated else device,
    ];
    return updated;
  }

  @override
  Future<DoorView> updateDoor(int doorId, String name, String? location) async {
    door = DoorView(
      id: doorId,
      name: name,
      location: location,
      status: door.status,
    );
    return door;
  }
}
