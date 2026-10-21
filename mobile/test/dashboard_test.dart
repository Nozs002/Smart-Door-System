import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/dashboard/dashboard_screen.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';
import 'support/fake_door_repository.dart';

class DashboardRepository extends FakeDoorRepository {
  Object? failure;
  final requests = <int>[];
  final delayed = Completer<List<DeviceView>>();
  bool delayFirst = false;
  @override
  Future<PageResult<DoorView>> listDoors({int page = 0}) {
    if (failure != null) return Future.error(failure!);
    return super.listDoors(page: page);
  }

  @override
  Future<List<DeviceView>> listDevices(int id) async {
    requests.add(id);
    if (delayFirst && id == 1) return delayed.future;
    return [
      DeviceView(
        id: id * 10,
        doorId: id,
        deviceCode: 'NODE-$id',
        nodeType: 'NODE_1',
        lifecycleStatus: 'ACTIVE',
        connectionStatus: 'UNKNOWN',
      ),
    ];
  }
}

void main() {
  testWidgets('empty dashboard can open claim flow', (tester) async {
    final session = testSession();
    addTearDown(session.dispose);
    await session.login('test', '123456789');
    await tester.pumpWidget(
      SmartDoorApp(
        session: session,
        repository: DashboardRepository()..doors = [],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(DashboardScreen), findsOneWidget);
    expect(find.text('Bạn chưa có cửa nào'), findsOneWidget);
    await tester.tap(find.text('Thêm cửa'));
    await tester.pumpAndSettle();
    expect(find.text('Cửa của bạn'), findsOneWidget);
    await session.logout();
    await tester.pumpAndSettle();
  });

  testWidgets('dashboard retries network failure and handles expired token', (
    tester,
  ) async {
    final session = testSession();
    addTearDown(session.dispose);
    await session.login('test', '123456789');
    final repository = DashboardRepository()
      ..failure = const ApiException('Mất kết nối');
    await tester.pumpWidget(
      SmartDoorApp(session: session, repository: repository),
    );
    await tester.pumpAndSettle();
    expect(find.text('Mất kết nối'), findsOneWidget);
    expect(session.signedIn, isTrue);
    repository.failure = null;
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();
    expect(repository.requests, [1]);
    await tester.scrollUntilVisible(
      find.text('NODE-1'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('NODE-1'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byTooltip('Làm mới dashboard'),
      -300,
      scrollable: find.byType(Scrollable).first,
    );
    repository.failure = const ApiException('Phiên hết hạn', statusCode: 401);
    await tester.tap(find.byTooltip('Làm mới dashboard'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pumpAndSettle();
    expect(session.signedIn, isFalse);
    expect(find.byType(TextFormField), findsNWidgets(2));
  });

  testWidgets(
    'selected door loads its nodes, theme and narrow large text fit',
    (tester) async {
      tester.view.physicalSize = const Size(320, 720);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      tester.platformDispatcher.textScaleFactorTestValue = 1.6;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      final session = testSession();
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      final repository = DashboardRepository()
        ..doors = [
          const DoorView(id: 1, name: 'Cửa trước', status: 'ACTIVE'),
          const DoorView(id: 2, name: 'Cửa sau', status: 'DISABLED'),
        ];
      await tester.pumpWidget(
        SmartDoorApp(session: session, repository: repository),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.tap(find.byType(DropdownButtonFormField<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cửa sau').last);
      await tester.pumpAndSettle();
      expect(repository.requests.last, 2);
      expect(find.text('Đang xem: Cửa sau'), findsOneWidget);
      await tester.tap(find.byTooltip('Chuyển giao diện'));
      await tester.pumpAndSettle();
      expect(
        Theme.of(tester.element(find.byType(DashboardScreen))).brightness,
        Brightness.dark,
      );
      await tester.scrollUntilVisible(
        find.text('CẤU HÌNH CỬA'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(repository.lifecycleCalls, isEmpty);
      await session.logout();
      await tester.pumpAndSettle();
    },
  );
}
