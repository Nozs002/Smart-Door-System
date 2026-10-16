import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/preview/preview_door_repository.dart';

void main() {
  late PreviewDoorRepository repository;
  final now = DateTime.utc(2026, 10, 16, 10);

  setUp(() {
    repository = PreviewDoorRepository(now: () => now);
  });

  Matcher apiError(int status) => isA<ApiException>().having(
    (error) => error.statusCode,
    'status code',
    status,
  );

  test(
    'demo starts with mixed lifecycle and independent node connections',
    () async {
      final doors = await repository.listDoors();
      expect(doors.items.map((door) => door.name), ['Cửa chính', 'Cửa kho']);
      expect(doors.items.map((door) => door.status), [
        'ACTIVE',
        'SETUP_PENDING',
      ]);
      expect((await repository.getDevice(1, 11)).connectionStatus, 'ONLINE');
      expect((await repository.getDevice(1, 12)).connectionStatus, 'OFFLINE');
      expect((await repository.getDevice(1, 13)).connectionStatus, 'UNKNOWN');
      expect(
        (await repository.getDevice(2, 22)).lifecycleStatus,
        'PROVISIONED',
      );
      expect((await repository.getDevice(2, 22)).connectionStatus, 'ONLINE');
    },
  );

  test(
    'claim normalizes code, creates two nodes and retries without duplicates',
    () async {
      await expectLater(repository.getDoor(3), throwsA(apiError(404)));
      await expectLater(
        repository.claimDoor('WRONG123'),
        throwsA(apiError(400)),
      );
      expect((await repository.listDoors()).totalElements, 2);

      final door = await repository.claimDoor(' k7mp - 4q2d ');
      expect(door.status, 'SETUP_PENDING');
      final devices = await repository.listDevices(door.id);
      expect(devices.map((device) => device.nodeType), ['NODE_1', 'NODE_2']);
      expect(
        devices.every((device) => device.lifecycleStatus == 'PROVISIONED'),
        isTrue,
      );
      expect(
        devices.every((device) => device.connectionStatus == 'UNKNOWN'),
        isTrue,
      );
      final retry = await repository.claimDoor(
        PreviewDoorRepository.demoActivationCode,
      );
      expect(retry.id, door.id);
      expect((await repository.listDoors()).totalElements, 3);
      expect((await repository.listDevices(door.id)).length, 2);
      expect((await repository.lifecycleEvents(door.id)).totalElements, 2);
    },
  );

  test(
    'returned collections are immutable and prior views are snapshots',
    () async {
      final doors = await repository.listDoors();
      final devices = await repository.listDevices(1);
      final history = await repository.lifecycleEvents(1);
      expect(() => doors.items.clear(), throwsUnsupportedError);
      expect(() => devices.clear(), throwsUnsupportedError);
      expect(() => history.items.clear(), throwsUnsupportedError);

      await repository.updateDoor(1, '  Cửa sân  ', '  Tầng 2  ');
      expect(doors.items.first.name, 'Cửa chính');
      expect((await repository.getDoor(1)).name, 'Cửa sân');
      expect((await repository.getDoor(1)).location, 'Tầng 2');
      expect(
        (await repository.getDoor(1)).createdAt,
        doors.items.first.createdAt,
      );
      expect((await repository.getDoor(1)).updatedAt, now);
    },
  );

  test('unknown and cross-door resources are not exposed or changed', () async {
    await expectLater(repository.getDoor(99), throwsA(apiError(404)));
    await expectLater(repository.listDevices(99), throwsA(apiError(404)));
    await expectLater(repository.getDevice(1, 21), throwsA(apiError(404)));
    await expectLater(
      repository.changeLifecycle(1, 21, 'ACTIVATE', ''),
      throwsA(apiError(404)),
    );
    await expectLater(
      repository.lifecycleEvents(1, deviceId: 21),
      throwsA(apiError(404)),
    );
    await expectLater(
      repository.replaceDevice(1, 11, 21, 'Thay'),
      throwsA(apiError(404)),
    );
    expect((await repository.getDevice(2, 21)).lifecycleStatus, 'PROVISIONED');
  });

  test('activation requires readiness and an unoccupied active role', () async {
    final before = (await repository.lifecycleEvents(2)).totalElements;
    await expectLater(
      repository.changeLifecycle(2, 21, 'ACTIVATE', ''),
      throwsA(apiError(409)),
    );
    expect((await repository.getDevice(2, 21)).lifecycleStatus, 'PROVISIONED');
    expect((await repository.lifecycleEvents(2)).totalElements, before);

    repository.readinessVerified = true;
    await repository.changeLifecycle(2, 21, 'ACTIVATE', ' Thiết lập xong ');
    expect((await repository.getDoor(2)).status, 'SETUP_PENDING');
    await repository.changeLifecycle(2, 22, 'ACTIVATE', '');
    expect((await repository.getDoor(2)).status, 'ACTIVE');
    await expectLater(
      repository.changeLifecycle(1, 13, 'ACTIVATE', ''),
      throwsA(apiError(409)),
    );
    expect((await repository.getDevice(1, 13)).lifecycleStatus, 'PROVISIONED');
  });

  test(
    'failed replacement never partially retires node or adds history',
    () async {
      final before = (await repository.lifecycleEvents(1)).totalElements;
      await expectLater(
        repository.replaceDevice(1, 11, 13, 'Hỏng'),
        throwsA(apiError(409)),
      );
      repository.readinessVerified = true;
      await expectLater(
        repository.replaceDevice(1, 11, 13, 'Hỏng'),
        throwsA(apiError(409)),
      );
      repository.revocationVerified = true;
      await expectLater(
        repository.replaceDevice(1, 11, 11, 'Hỏng'),
        throwsA(apiError(409)),
      );
      await expectLater(
        repository.replaceDevice(1, 12, 13, 'Hỏng'),
        throwsA(apiError(409)),
      );
      await expectLater(
        repository.replaceDevice(1, 11, 13, '  '),
        throwsA(apiError(400)),
      );
      expect((await repository.getDevice(1, 11)).lifecycleStatus, 'ACTIVE');
      expect(
        (await repository.getDevice(1, 13)).lifecycleStatus,
        'PROVISIONED',
      );
      expect((await repository.getDoor(1)).status, 'ACTIVE');
      expect((await repository.lifecycleEvents(1)).totalElements, before);
    },
  );

  test('replacement retains old node and records both transitions in time/id order', () async {
    repository.readinessVerified = true;
    repository.revocationVerified = true;
    final before = (await repository.lifecycleEvents(1)).totalElements;
    final updated = await repository.replaceDevice(
      1,
      11,
      13,
      '  Thay đầu đọc  ',
    );
    expect(updated.id, 13);
    expect(updated.lifecycleStatus, 'ACTIVE');
    expect((await repository.getDevice(1, 11)).lifecycleStatus, 'RETIRED');
    expect((await repository.listDevices(1)).length, 3);
    expect((await repository.getDoor(1)).status, 'ACTIVE');
    final history = await repository.lifecycleEvents(1);
    expect(history.totalElements, before + 2);
    expect(history.items.first.deviceId, 13);
    expect(history.items.first.eventType, 'ACTIVATED');
    expect(history.items.first.occurredAt, now);
    expect(history.items[1].deviceId, 11);
    expect(history.items[1].eventType, 'REPLACED');
    expect(history.items[1].replacementDeviceId, 13);
    expect(history.items[1].reason, 'Thay đầu đọc');
    expect(history.items.first.id, greaterThan(history.items[1].id));
  });

  test(
    'disable keeps metadata and connection; retired nodes cannot be revived',
    () async {
      final original = await repository.getDevice(1, 11);
      final disabled = await repository.changeLifecycle(
        1,
        11,
        'DISABLE',
        ' Bảo trì ',
      );
      expect(disabled.lifecycleStatus, 'DISABLED');
      expect(disabled.connectionStatus, original.connectionStatus);
      expect(disabled.lastHeartbeatAt, original.lastHeartbeatAt);
      expect(disabled.deviceCode, original.deviceCode);
      expect(disabled.firmwareVersion, original.firmwareVersion);
      expect(disabled.appliedConfigVersion, original.appliedConfigVersion);
      expect(disabled.appliedPinVersion, original.appliedPinVersion);
      expect((await repository.getDoor(1)).status, 'SETUP_PENDING');
      await expectLater(
        repository.changeLifecycle(1, 11, 'REACTIVATE', ''),
        throwsA(apiError(409)),
      );
      repository.readinessVerified = true;
      await repository.changeLifecycle(1, 11, 'REACTIVATE', '');
      expect((await repository.getDoor(1)).status, 'ACTIVE');
      await repository.changeLifecycle(1, 11, 'RETIRE', 'Hỏng');
      final count = (await repository.lifecycleEvents(1)).totalElements;
      for (final action in ['ACTIVATE', 'REACTIVATE', 'DISABLE', 'RETIRE']) {
        await expectLater(
          repository.changeLifecycle(1, 11, action, 'Thử'),
          throwsA(apiError(409)),
        );
      }
      expect((await repository.lifecycleEvents(1)).totalElements, count);
      expect((await repository.getDevice(1, 11)).lifecycleStatus, 'RETIRED');
    },
  );

  test(
    'history pagination, node filter and page validation remain consistent',
    () async {
      final first = await repository.lifecycleEvents(1);
      final second = await repository.lifecycleEvents(1, page: 1);
      expect(first.size, PreviewDoorRepository.pageSize);
      expect(first.hasNext, isTrue);
      expect(second.hasNext, isFalse);
      expect(first.totalElements, first.items.length + second.items.length);
      final all = [...first.items, ...second.items];
      expect(all.map((event) => event.id).toSet().length, all.length);
      expect(all.every((event) => event.doorId == 1), isTrue);
      for (var index = 1; index < all.length; index++) {
        expect(
          all[index - 1].occurredAt.isBefore(all[index].occurredAt),
          isFalse,
        );
      }
      final filtered = await repository.lifecycleEvents(1, deviceId: 11);
      expect(filtered.totalElements, 4);
      expect(filtered.items.every((event) => event.deviceId == 11), isTrue);
      expect((await repository.lifecycleEvents(1, page: 99)).items, isEmpty);
      expect((await repository.listDoors(page: 99)).items, isEmpty);
      await expectLater(repository.listDoors(page: -1), throwsA(apiError(400)));
      await expectLater(
        repository.lifecycleEvents(1, page: -1),
        throwsA(apiError(400)),
      );
    },
  );

  test(
    'invalid names, reasons and transitions leave sample state intact',
    () async {
      final count = (await repository.lifecycleEvents(1)).totalElements;
      await expectLater(
        repository.updateDoor(1, '  ', ''),
        throwsA(apiError(400)),
      );
      await expectLater(
        repository.updateDoor(1, 'x' * 101, null),
        throwsA(apiError(400)),
      );
      await expectLater(
        repository.updateDoor(1, 'Cửa', 'x' * 256),
        throwsA(apiError(400)),
      );
      await expectLater(
        repository.changeLifecycle(1, 11, 'DISABLE', 'x' * 501),
        throwsA(apiError(400)),
      );
      await expectLater(
        repository.changeLifecycle(1, 11, 'RETIRE', '  '),
        throwsA(apiError(400)),
      );
      await expectLater(
        repository.changeLifecycle(1, 13, 'RETIRE', 'Thử'),
        throwsA(apiError(409)),
      );
      await expectLater(
        repository.changeLifecycle(1, 11, 'UNKNOWN', ''),
        throwsA(apiError(400)),
      );
      expect((await repository.getDoor(1)).name, 'Cửa chính');
      expect((await repository.getDevice(1, 11)).lifecycleStatus, 'ACTIVE');
      expect((await repository.lifecycleEvents(1)).totalElements, count);
      final updated = await repository.updateDoor(1, 'Cửa chính', '   ');
      expect(updated.location, isNull);
    },
  );

  test(
    'each preview instance starts fresh and does not share a claimed code',
    () async {
      await repository.claimDoor(PreviewDoorRepository.demoActivationCode);
      await repository.updateDoor(1, 'Đã sửa', null);
      final fresh = PreviewDoorRepository(now: () => now);
      expect((await fresh.listDoors()).totalElements, 2);
      expect((await fresh.getDoor(1)).name, 'Cửa chính');
      expect(fresh.readinessVerified, isFalse);
      expect(fresh.revocationVerified, isFalse);
      expect(
        (await fresh.claimDoor(PreviewDoorRepository.demoActivationCode)).id,
        3,
      );
    },
  );
}
