import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/access_logs/data/access_repository.dart';
import 'package:smart_door_mobile/features/access_logs/presentation/access_log_screen.dart';
import 'package:smart_door_mobile/features/dashboard/door_overview_card.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';
import 'support/fake_door_repository.dart';

class FakeAccess extends AccessRepository {
  final requests = <(int, int, String?, String?)>[];
  Object? failure;
  Completer<DoorMonitoring?>? delayed;
  int summaryCalls = 0;
  @override
  Future<PageResult<AccessLogEntry>> logs(
    int doorId, {
    int page = 0,
    String? method,
    String? result,
  }) async {
    requests.add((doorId, page, method, result));
    if (failure != null) throw failure!;
    return PageResult(
      items: [
        AccessLogEntry(
          method: method ?? 'RFID',
          result: page == 0 ? 'GRANTED' : 'EXECUTED',
          cardholderName: 'Nguyen An',
          reason: page == 0 ? 'QUEUED' : 'EXECUTED',
        ),
      ],
      page: page,
      size: 1,
      totalElements: 2,
      totalPages: 2,
    );
  }

  @override
  Future<DoorMonitoring?> monitoring(int doorId) =>
      delayed?.future ??
      Future.value(
        DoorMonitoring(
          doorState: doorId == 1 ? 'CLOSED' : 'OPEN',
          lockState: 'LOCKED',
          observedAt: DateTime.now().toUtc(),
          fresh: true,
          temperature: 28.5,
          humidity: 65,
        ),
      );
  @override
  Future<AccessSummary> summary(int doorId) async {
    summaryCalls++;
    return const AccessSummary(2, null);
  }
}

void main() {
  testWidgets('history paginates and resets page when filtering', (
    tester,
  ) async {
    final repository = FakeAccess();
    await tester.pumpWidget(
      MaterialApp(
        home: AccessLogScreen(
          repository: repository,
          doorId: 12,
          doorName: 'Cửa test',
          onSignInRequested: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Thẻ RFID · Xác thực hợp lệ'), findsOneWidget);
    await tester.tap(find.text('Tải thêm'));
    await tester.pumpAndSettle();
    expect(repository.requests.last, (12, 1, null, null));
    expect(find.text('Thẻ RFID · Đã thực thi mở khóa'), findsOneWidget);
    await tester.tap(find.byType(DropdownButtonFormField<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mã PIN').last);
    await tester.pumpAndSettle();
    expect(repository.requests.last, (12, 0, 'PIN', null));
    expect(find.text('Mã PIN · Xác thực hợp lệ'), findsOneWidget);
    expect(find.text('Thẻ RFID · Đã thực thi mở khóa'), findsNothing);
  });

  testWidgets('history retries errors and requests login on 401', (
    tester,
  ) async {
    final repository = FakeAccess()
      ..failure = const ApiException('Mất kết nối');
    var signIn = false;
    await tester.pumpWidget(
      MaterialApp(
        home: AccessLogScreen(
          repository: repository,
          doorId: 1,
          doorName: 'Cửa',
          onSignInRequested: () => signIn = true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Mất kết nối'), findsOneWidget);
    repository.failure = const ApiException('Hết phiên', statusCode: 401);
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();
    expect(signIn, isTrue);
  });

  testWidgets('overview uses fresh sensors and hides stale values', (
    tester,
  ) async {
    Future<void> show(bool fresh) => tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 398,
            child: DoorOverviewCard(
              door: const DoorView(id: 1, name: 'Cửa', status: 'ACTIVE'),
              onTap: () {},
              monitoring: DoorMonitoring(
                doorState: 'OPEN',
                lockState: 'UNLOCKED',
                observedAt: DateTime.now().toUtc().subtract(
                  Duration(seconds: fresh ? 0 : 61),
                ),
                fresh: fresh,
                temperature: 28.5,
                humidity: 65,
              ),
            ),
          ),
        ),
      ),
    );
    await show(true);
    await tester.pumpAndSettle();
    expect(find.text('28.5°C'), findsOneWidget);
    expect(find.text('65.0%'), findsOneWidget);
    expect(find.textContaining('Cửa đang mở'), findsOneWidget);
    await show(false);
    await tester.pumpAndSettle();
    expect(find.text('28.5°C'), findsNothing);
    expect(find.text('—'), findsNWidgets(2));
    expect(find.textContaining('Dữ liệu cảm biến đã cũ'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'dashboard loads live data, opens access history and stops polling in background',
    (tester) async {
      final session = testSession();
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      final repository = FakeAccess();
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          repository: FakeDoorRepository(),
          accessRepository: repository,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('28.5°C'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Lịch sử ra vào'),
        250,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Lịch sử ra vào'));
      await tester.pumpAndSettle();
      expect(find.byType(AccessLogScreen), findsOneWidget);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      final calls = repository.summaryCalls;
      await tester.pump(const Duration(seconds: 40));
      expect(repository.summaryCalls, calls);
      await session.logout();
      await tester.pumpWidget(const SizedBox.shrink());
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    },
  );
}
