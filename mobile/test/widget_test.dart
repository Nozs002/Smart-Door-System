import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/doors/presentation/device_detail_screen.dart';
import 'package:smart_door_mobile/features/doors/presentation/door_detail_screen.dart';
import 'package:smart_door_mobile/features/doors/presentation/lifecycle_history_screen.dart';
import 'package:smart_door_mobile/main.dart';

import 'support/fake_door_repository.dart';

void main() {
  testWidgets('signed-out app asks for login without a demo account', (
    tester,
  ) async {
    _phoneSize(tester);
    await tester.pumpWidget(const SmartDoorApp());
    await tester.pumpAndSettle();
    expect(find.text('Vui lòng đăng nhập để quản lý cửa.'), findsOneWidget);
    expect(find.text('Thêm cửa'), findsNothing);
    expect(find.text('Thử lại'), findsOneWidget);
  });

  testWidgets('claim validates and normalizes code before loading door', (
    tester,
  ) async {
    _phoneSize(tester);
    final repository = FakeDoorRepository()..doors = [];
    await tester.pumpWidget(SmartDoorApp(repository: repository));
    await tester.pumpAndSettle();
    expect(find.text('Bạn chưa có cửa nào'), findsOneWidget);
    await tester.tap(find.text('Thêm cửa'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('activationCode')), '123');
    await tester.tap(find.text('Xác nhận thêm cửa'));
    await tester.pumpAndSettle();
    expect(repository.claimedCode, isNull);
    expect(find.text('Nhập mã gồm 8 ký tự, ví dụ K7MP-4Q2D.'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('activationCode')),
      'k7mp-4q2d',
    );
    await tester.tap(find.text('Xác nhận thêm cửa'));
    await tester.pumpAndSettle();
    expect(repository.claimedCode, 'K7MP4Q2D');
    expect(find.text('Thông tin cửa'), findsOneWidget);
    expect(repository.lifecycleCalls, isEmpty);
  });

  testWidgets(
    'door detail separates node connection and lifecycle, edits door',
    (tester) async {
      _phoneSize(tester);
      final repository = FakeDoorRepository();
      await tester.pumpWidget(
        MaterialApp(home: DoorDetailScreen(repository: repository, doorId: 1)),
      );
      await tester.pumpAndSettle();
      expect(find.text('NODE-READER'), findsOneWidget);
      expect(find.text('NODE-LOCK'), findsOneWidget);
      expect(find.text('Trực tuyến'), findsOneWidget);
      expect(find.text('Chưa có heartbeat'), findsOneWidget);
      expect(find.text('Đã vô hiệu hóa'), findsOneWidget);
      expect(find.text('Đã đăng ký'), findsOneWidget);
      await tester.tap(find.byTooltip('Sửa tên và vị trí'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('doorName')), 'Cửa sân');
      await tester.enterText(find.byKey(const Key('doorLocation')), 'Tầng 1');
      await tester.tap(find.text('Lưu'));
      await tester.pumpAndSettle();
      expect(repository.door.name, 'Cửa sân');
      expect(repository.door.location, 'Tầng 1');
      expect(find.text('Cửa sân'), findsNWidgets(2));
    },
  );

  testWidgets('ownership error is visible and retry reloads', (tester) async {
    _phoneSize(tester);
    final repository = FakeDoorRepository()
      ..doorError = const ApiException('Không tìm thấy cửa.', statusCode: 404);
    await tester.pumpWidget(
      MaterialApp(home: DoorDetailScreen(repository: repository, doorId: 1)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Không tìm thấy cửa.'), findsOneWidget);
    expect(find.text('Thông tin cửa'), findsNothing);
    repository.doorError = null;
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();
    expect(find.text('Thông tin cửa'), findsOneWidget);
  });

  testWidgets('activation reports readiness denial and preserves state', (
    tester,
  ) async {
    _phoneSize(tester);
    final repository = FakeDoorRepository()
      ..lifecycleError = const ApiException(
        'Node chưa xác nhận cấu hình và PIN.',
        statusCode: 409,
      );
    await tester.pumpWidget(
      MaterialApp(
        home: DeviceDetailScreen(
          repository: repository,
          doorId: 1,
          deviceId: 12,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Ngừng sử dụng thiết bị'), findsNothing);
    await tester.ensureVisible(find.text('Kích hoạt thiết bị'));
    await tester.tap(find.text('Kích hoạt thiết bị'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận'));
    await tester.pumpAndSettle();
    expect(repository.lifecycleCalls.single, 'ACTIVATE');
    expect(find.text('Node chưa xác nhận cấu hình và PIN.'), findsOneWidget);
    expect(repository.devices.last.lifecycleStatus, 'PROVISIONED');
    expect(find.text('Hủy'), findsOneWidget);
  });

  testWidgets('retirement needs reason and is irreversible in UI', (
    tester,
  ) async {
    _phoneSize(tester);
    final repository = FakeDoorRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: DeviceDetailScreen(
          repository: repository,
          doorId: 1,
          deviceId: 11,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Ngừng sử dụng thiết bị'));
    await tester.tap(find.text('Ngừng sử dụng thiết bị'));
    await tester.pumpAndSettle();
    expect(repository.lifecycleCalls, isEmpty);
    await tester.tap(find.text('Xác nhận'));
    await tester.pumpAndSettle();
    expect(find.text('Vui lòng nhập lý do.'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('lifecycleReason')),
      'Thiết bị hỏng',
    );
    await tester.tap(find.text('Xác nhận'));
    await tester.pumpAndSettle();
    expect(repository.lifecycleCalls.single, 'RETIRE');
    expect(repository.devices.first.lifecycleStatus, 'RETIRED');
    expect(find.text('Kích hoạt lại thiết bị'), findsNothing);
    expect(find.text('Ngừng sử dụng thiết bị'), findsNothing);
  });

  testWidgets('history appends pages and filter resets pagination', (
    tester,
  ) async {
    _phoneSize(tester);
    final repository = FakeDoorRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: LifecycleHistoryScreen(
          repository: repository,
          doorId: 1,
          devices: repository.devices,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(repository.historyRequests, [(page: 0, deviceId: null)]);
    await tester.ensureVisible(find.text('Xem thêm lịch sử'));
    await tester.tap(find.text('Xem thêm lịch sử'));
    await tester.pumpAndSettle();
    expect(repository.historyRequests.last, (page: 1, deviceId: null));
    expect(find.text('Lý do: Sự kiện 0'), findsOneWidget);
    expect(find.text('Lý do: Sự kiện 1'), findsOneWidget);
    await tester.tap(find.byKey(const Key('historyDeviceFilter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('NODE-READER').last);
    await tester.pumpAndSettle();
    expect(repository.historyRequests.last, (page: 0, deviceId: 11));
    expect(find.text('Lý do: Sự kiện 1'), findsNothing);
  });
}

void _phoneSize(WidgetTester tester) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(600, 1100);
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}
