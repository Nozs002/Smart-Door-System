import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/alerts/data/alert_models.dart';
import 'package:smart_door_mobile/features/alerts/data/alert_repository.dart';
import 'package:smart_door_mobile/features/alerts/presentation/alert_list_screen.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';
import 'package:smart_door_mobile/features/notifications/data/notification_repository.dart';
import 'package:smart_door_mobile/features/notifications/presentation/notification_screen.dart';
import 'package:smart_door_mobile/features/settings/data/settings_models.dart';
import 'package:smart_door_mobile/features/settings/data/settings_repository.dart';
import 'package:smart_door_mobile/features/settings/presentation/door_settings_screen.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';
import 'support/fake_credential_repository.dart';
import 'support/fake_door_repository.dart';

class _Settings implements SettingsRepository {
  final retries = <int>[];
  int saves = 0;
  String mode = 'SECURITY';
  bool fire = true;
  bool open = false;
  int? timeout;
  bool failSave = false;
  DoorConfiguration get value => DoorConfiguration(
    operatingMode: mode,
    fireAlarmEnabled: fire,
    doorOpenAlertEnabled: open,
    openTimeoutSeconds: timeout,
    doorId: 12,
    maxFailedAttempts: 5,
    lockoutSeconds: 60,
    temperatureThreshold: 50,
    version: 4,
    credentialsVersion: 8,
    devices: const [
      DeviceSyncStatus(
        deviceId: 1,
        deviceCode: 'NODE-1',
        nodeType: 'NODE_1',
        lifecycleStatus: 'ACTIVE',
        configState: 'PENDING',
        credentialsState: 'PENDING',
        appliedConfigVersion: 3,
        appliedCredentialsVersion: 7,
      ),
    ],
  );
  @override
  Future<DoorConfiguration> getConfiguration(int doorId) async => value;
  @override
  Future<DoorConfiguration> retrySync(int doorId) async {
    retries.add(doorId);
    return value;
  }

  @override
  Future<DoorConfiguration> updateConfiguration(
    int doorId, {
    required int maxFailedAttempts,
    required int lockoutSeconds,
    required double temperatureThreshold,
    int? openTimeoutSeconds,
    String? operatingMode,
    bool? fireAlarmEnabled,
    bool? doorOpenAlertEnabled,
  }) async {
    saves++;
    if (failSave) throw Exception('Save failed');
    mode = operatingMode ?? mode;
    fire = fireAlarmEnabled ?? fire;
    open = doorOpenAlertEnabled ?? open;
    timeout = openTimeoutSeconds;
    return value;
  }
}

class _Alerts implements AlertRepository {
  final queries = <(int, int?, String?)>[];
  final changes = <String>[];
  String currentStatus = 'ACTIVE';
  AlertView alert(int id) => AlertView(
    id: id,
    doorId: 12,
    doorName: 'Cửa trước',
    type: 'HIGH_TEMPERATURE',
    severity: 'CRITICAL',
    status: currentStatus,
    triggeredAt: DateTime.utc(2026, 10, 28),
    observedTemperature: 60,
  );
  @override
  Future<PageResult<AlertView>> listAlerts({
    int page = 0,
    int? doorId,
    String? status,
  }) async {
    queries.add((page, doorId, status));
    return testPage([alert(page + 3)], page: page, totalPages: 2);
  }

  @override
  Future<AlertView> getAlert(int alertId) async => alert(alertId);
  @override
  Future<AlertView> changeStatus(int alertId, String status) async {
    changes.add(status);
    currentStatus = status;
    return alert(alertId);
  }
}

class _Notifications implements NotificationRepository {
  bool read = false;
  final reads = <int>[];
  int unreadQueries = 0;
  OwnerNotification get value => OwnerNotification(
    id: 9,
    alertId: 3,
    doorId: 12,
    title: 'Nhiệt độ tại cửa trước',
    body: 'Nhiệt độ vượt ngưỡng',
    createdAt: DateTime.utc(2026, 10, 28),
    readAt: read ? DateTime.utc(2026, 10, 28, 1) : null,
  );
  @override
  Future<PageResult<OwnerNotification>> list({
    int page = 0,
    bool unread = false,
  }) async {
    if (unread) unreadQueries++;
    return testPage(unread && read ? [] : [value]);
  }

  @override
  Future<OwnerNotification> markRead(int id) async {
    reads.add(id);
    read = true;
    return value;
  }
}

void main() {
  testWidgets(
    'dashboard persists all three switches and keeps state on failure',
    (tester) async {
      tester.view.physicalSize = const Size(430, 1200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final session = testSession();
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      final settings = _Settings();
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          repository: FakeDoorRepository(),
          settingsRepository: settings,
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Báo cháy'),
        250,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();
      final switches = find.byType(Switch);
      expect(tester.widget<Switch>(switches.at(0)).value, isTrue);
      expect(tester.widget<Switch>(switches.at(1)).value, isTrue);
      expect(tester.widget<Switch>(switches.at(2)).value, isFalse);
      tester.widget<Switch>(switches.at(0)).onChanged!(false);
      await tester.pumpAndSettle();
      expect(settings.mode, 'SECURITY');
      expect(settings.saves, 0);
      await tester.tap(find.text('Hủy'));
      await tester.pumpAndSettle();
      expect(settings.mode, 'SECURITY');
      tester.widget<Switch>(switches.at(0)).onChanged!(false);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Giữ cửa mở'));
      await tester.pumpAndSettle();
      expect(settings.mode, 'AUTOMATIC');
      tester.widget<Switch>(switches.at(1)).onChanged!(false);
      await tester.pumpAndSettle();
      expect(settings.fire, isFalse);
      tester.widget<Switch>(switches.at(2)).onChanged!(true);
      await tester.pumpAndSettle();
      expect(settings.open, isTrue);
      expect(settings.timeout, 30);
      tester.widget<Switch>(switches.at(0)).onChanged!(true);
      await tester.pumpAndSettle();
      expect(settings.mode, 'AUTOMATIC');
      await tester.tap(find.text('Bật bảo mật'));
      await tester.pumpAndSettle();
      expect(settings.mode, 'SECURITY');
      settings.failSave = true;
      tester.widget<Switch>(switches.at(1)).onChanged!(true);
      await tester.pumpAndSettle();
      expect(tester.widget<Switch>(switches.at(1)).value, isFalse);
      expect(tester.takeException(), isNull);
      await session.logout();
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'notification badge polls while foreground and pauses in background',
    (tester) async {
      final session = testSession();
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      final notifications = _Notifications();
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          repository: FakeDoorRepository(),
          notificationRepository: notifications,
          alertRepository: _Alerts(),
        ),
      );
      await tester.pumpAndSettle();
      expect(notifications.unreadQueries, 1);
      expect(
        find.descendant(of: find.byType(Badge), matching: find.text('1')),
        findsOneWidget,
      );
      await tester.pump(const Duration(seconds: 30));
      await tester.pumpAndSettle();
      expect(notifications.unreadQueries, 2);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump(const Duration(seconds: 45));
      expect(notifications.unreadQueries, 2);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(notifications.unreadQueries, 3);
      await session.expire();
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
  testWidgets(
    'sync retry preserves version and never reports pending device as applied',
    (tester) async {
      final repository = _Settings();
      await tester.pumpWidget(
        MaterialApp(
          home: DoorSettingsScreen(
            repository: repository,
            doorId: 12,
            doorName: 'Cửa trước',
            onSignInRequested: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Yêu cầu đồng bộ lại'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.ensureVisible(find.text('Yêu cầu đồng bộ lại'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yêu cầu đồng bộ lại'));
      await tester.pumpAndSettle();
      expect(repository.retries, [12]);
      expect(repository.saves, 0);
      expect(find.text('Cấu hình v4'), findsOneWidget);
      expect(find.textContaining('Đang chờ'), findsWidgets);
      expect(find.textContaining('đã nhận 3 / cần 4'), findsOneWidget);
      expect(find.text('Đã áp dụng'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'alert list requests pages and acknowledged filter per selected door',
    (tester) async {
      final repository = _Alerts();
      await tester.pumpWidget(
        MaterialApp(
          home: AlertListScreen(
            repository: repository,
            doorId: 12,
            onSignInRequested: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(repository.queries, [(0, 12, null)]);
      await tester.tap(find.text('Xem thêm cảnh báo'));
      await tester.pumpAndSettle();
      expect(repository.queries.last, (1, 12, null));
      expect(find.text('Nhiệt độ vượt ngưỡng'), findsNWidgets(2));
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đã xem').last);
      await tester.pumpAndSettle();
      expect(repository.queries.last, (0, 12, 'ACKNOWLEDGED'));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'reading inbox does not acknowledge or resolve alert; explicit actions do',
    (tester) async {
      final notifications = _Notifications();
      final alerts = _Alerts();
      await tester.pumpWidget(
        MaterialApp(
          home: NotificationScreen(
            repository: notifications,
            alerts: alerts,
            onSignInRequested: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Nhiệt độ tại cửa trước'));
      await tester.pumpAndSettle();
      expect(notifications.reads, [9]);
      expect(alerts.changes, isEmpty);
      expect(find.text('Chi tiết cảnh báo'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Đánh dấu đã xem'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.ensureVisible(find.text('Đánh dấu đã xem'));
      await tester.tap(find.text('Đánh dấu đã xem'));
      await tester.pumpAndSettle();
      expect(alerts.changes, ['ACKNOWLEDGED']);
      await tester.ensureVisible(find.text('Đánh dấu đã giải quyết'));
      await tester.tap(find.text('Đánh dấu đã giải quyết'));
      await tester.pumpAndSettle();
      expect(alerts.changes, ['ACKNOWLEDGED', 'RESOLVED']);
      expect(find.text('Đánh dấu đã giải quyết'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}
