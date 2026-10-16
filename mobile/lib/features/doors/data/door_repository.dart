import '../../../core/api_client.dart';
import 'door_models.dart';

abstract interface class DoorRepository {
  Future<PageResult<DoorView>> listDoors({int page = 0});
  Future<DoorView> claimDoor(String activationCode);
  Future<DoorView> getDoor(int doorId);
  Future<DoorView> updateDoor(int doorId, String name, String? location);
  Future<List<DeviceView>> listDevices(int doorId);
  Future<DeviceView> getDevice(int doorId, int deviceId);
  Future<DeviceView> changeLifecycle(
    int doorId,
    int deviceId,
    String action,
    String reason,
  );
  Future<DeviceView> replaceDevice(
    int doorId,
    int deviceId,
    int replacementDeviceId,
    String reason,
  );
  Future<PageResult<LifecycleEvent>> lifecycleEvents(
    int doorId, {
    int page = 0,
    int? deviceId,
  });
}

class RestDoorRepository implements DoorRepository {
  RestDoorRepository(this.client);

  final ApiClient client;
  static const _doors = '/api/v1/doors';

  @override
  Future<PageResult<DoorView>> listDoors({int page = 0}) async =>
      PageResult.fromJson(
        await client.request(
          'GET',
          _doors,
          query: {'page': '$page', 'size': '20'},
        ) as Map<String, dynamic>,
        DoorView.fromJson,
      );

  @override
  Future<DoorView> claimDoor(String activationCode) async => DoorView.fromJson(
    await client.request(
      'POST',
      '$_doors/claim',
      body: {'activationCode': normalizeActivationCode(activationCode)},
    ) as Map<String, dynamic>,
  );

  @override
  Future<DoorView> getDoor(int doorId) async => DoorView.fromJson(
    await client.request('GET', '$_doors/$doorId') as Map<String, dynamic>,
  );

  @override
  Future<DoorView> updateDoor(
    int doorId,
    String name,
    String? location,
  ) async => DoorView.fromJson(
    await client.request(
      'PATCH',
      '$_doors/$doorId',
      body: {'name': name.trim(), 'location': location?.trim()},
    ) as Map<String, dynamic>,
  );

  @override
  Future<List<DeviceView>> listDevices(int doorId) async =>
      (await client.request('GET', '$_doors/$doorId/devices') as List<dynamic>)
          .map((json) => DeviceView.fromJson(json as Map<String, dynamic>))
          .toList(growable: false);

  @override
  Future<DeviceView> getDevice(int doorId, int deviceId) async =>
      DeviceView.fromJson(
        await client.request('GET', '$_doors/$doorId/devices/$deviceId')
            as Map<String, dynamic>,
      );

  @override
  Future<DeviceView> changeLifecycle(
    int doorId,
    int deviceId,
    String action,
    String reason,
  ) async => DeviceView.fromJson(
    await client.request(
      'POST',
      '$_doors/$doorId/devices/$deviceId/lifecycle',
      body: {'action': action, 'reason': reason.trim()},
    ) as Map<String, dynamic>,
  );

  @override
  Future<DeviceView> replaceDevice(
    int doorId,
    int deviceId,
    int replacementDeviceId,
    String reason,
  ) async => DeviceView.fromJson(
    await client.request(
      'POST',
      '$_doors/$doorId/devices/$deviceId/replace',
      body: {
        'replacementDeviceId': replacementDeviceId,
        'reason': reason.trim(),
      },
    ) as Map<String, dynamic>,
  );

  @override
  Future<PageResult<LifecycleEvent>> lifecycleEvents(
    int doorId, {
    int page = 0,
    int? deviceId,
  }) async => PageResult.fromJson(
    await client.request(
      'GET',
      '$_doors/$doorId/lifecycle-events',
      query: {
        'page': '$page',
        'size': '20',
        if (deviceId != null) 'deviceId': '$deviceId',
      },
    ) as Map<String, dynamic>,
    LifecycleEvent.fromJson,
  );
}
