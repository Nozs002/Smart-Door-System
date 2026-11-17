import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';

void main() {
  test(
    'activation accepts lowercase and separators, excludes confusing symbols',
    () {
      expect(normalizeActivationCode(' k7mp-4q2d '), 'K7MP4Q2D');
      expect(validateActivationCode('k7mp-4q2d'), isNull);
      expect(validateActivationCode('123456'), isNotNull);
      expect(validateActivationCode('ABCDEFG!'), isNotNull);
      for (final symbol in ['0', '1', 'O', 'I', 'L']) {
        expect(validateActivationCode('2345678$symbol'), isNotNull);
      }
    },
  );

  test('timezone-less timestamps are UTC and explicit offsets survive', () {
    expect(
      parseUtcTime('2026-10-16T09:30:00'),
      DateTime.utc(2026, 10, 16, 9, 30),
    );
    expect(
      parseUtcTime('2026-10-16T16:30:00+07:00')!.toUtc(),
      DateTime.utc(2026, 10, 16, 9, 30),
    );
    expect(parseUtcTime(null), isNull);
  });

  test('connection and lifecycle status stay independent', () {
    final device = DeviceView.fromJson({
      'id': 1,
      'doorId': 2,
      'deviceCode': 'NODE-1',
      'nodeType': 'NODE_1',
      'lifecycleStatus': 'DISABLED',
      'connectionStatus': 'ONLINE',
    });
    expect(device.lifecycleStatus, 'DISABLED');
    expect(device.connectionStatus, 'ONLINE');
    expect(device.lastHeartbeatAt, isNull);
  });

  test('pagination uses server totalPages and handles empty pages', () {
    final empty = PageResult<DoorView>.fromJson({
      'items': [],
      'page': 0,
      'size': 20,
      'totalElements': 0,
      'totalPages': 0,
    }, DoorView.fromJson);
    expect(empty.hasNext, isFalse);
    expect(empty.items, isEmpty);
    final second = PageResult<DoorView>.fromJson({
      'items': [],
      'page': 1,
      'size': 20,
      'totalElements': 41,
      'totalPages': 3,
    }, DoorView.fromJson);
    expect(second.hasNext, isTrue);
  });
}
