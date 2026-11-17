import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/authentication/account_models.dart';
import 'package:smart_door_mobile/features/authentication/auth_session.dart';
import 'package:smart_door_mobile/features/authentication/login_screen.dart';
import 'package:smart_door_mobile/features/dashboard/dashboard_screen.dart';
import 'package:smart_door_mobile/features/onboarding/technician_screen.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';
import 'support/fake_door_repository.dart';

AccountProfile profileFor(String role) => AccountProfile(
  id: 1,
  username: role == 'TECHNICIAN' ? 'technician' : 'customer',
  displayName: role == 'TECHNICIAN' ? 'Kỹ thuật viên thử' : 'Khách hàng thử',
  status: 'ACTIVE',
  createdAt: DateTime.utc(2026, 11, 15),
  role: role,
);

class RoleAuthApi extends FakeAuthApi {
  String role = 'TECHNICIAN';
  bool profileUnavailable = false;
  Completer<AccountProfile>? pendingProfile;

  @override
  Future<AccountProfile> profile(String token) async {
    if (pendingProfile != null) return pendingProfile!.future;
    if (profileUnavailable) throw Exception('Profile unavailable');
    return profileFor(role);
  }
}

class TechnicianClient extends ApiClient {
  TechnicianClient(AuthSession session) : super(tokenProvider: session);
  final paths = <String>[];

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
  }) async {
    paths.add(path);
    if (path == '/api/v1/technician/inventory' ||
        path == '/api/v1/technician/kits') {
      return <dynamic>[];
    }
    throw StateError('Unexpected request');
  }
}

void main() {
  Future<(AuthSession, TechnicianClient)> prepare(RoleAuthApi api) async {
    final session = testSession(api: api);
    final client = TechnicianClient(session);
    addTearDown(session.dispose);
    addTearDown(client.close);
    await session.login('test-account', 'test-password');
    return (session, client);
  }

  testWidgets('restored technician lands on direct installation sections', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final (session, client) = await prepare(RoleAuthApi());
    await tester.pumpWidget(
      SmartDoorApp(
        session: session,
        apiClient: client,
        repository: FakeDoorRepository(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(TechnicianScreen), findsOneWidget);
    expect(find.byType(DashboardScreen), findsNothing);
    expect(find.text('Kho ESP32 và tem thiết bị'), findsOneWidget);
    expect(find.text('Đăng ký NODE_1'), findsOneWidget);
    expect(find.text('Đăng ký NODE_2'), findsOneWidget);
    expect(find.byType(ExpansionTile), findsNothing);
    expect(find.text('Thêm cửa'), findsNothing);
    expect(find.text('Quản lý cửa'), findsNothing);
    expect(find.text('Quản lý lắp đặt'), findsNothing);
    await tester.scrollUntilVisible(
      find.text('Các bộ cửa đã tạo'),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Các bộ cửa đã tạo'), findsOneWidget);
    expect(
      client.paths,
      containsAll(['/api/v1/technician/inventory', '/api/v1/technician/kits']),
    );
    await session.logout();
    await tester.pumpAndSettle();
  });

  testWidgets('customer still lands on door dashboard', (tester) async {
    final (session, client) = await prepare(RoleAuthApi()..role = 'CUSTOMER');
    await tester.pumpWidget(
      SmartDoorApp(
        session: session,
        apiClient: client,
        repository: FakeDoorRepository()..doors = [],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(DashboardScreen), findsOneWidget);
    expect(find.byType(TechnicianScreen), findsNothing);
    expect(find.text('Thêm cửa'), findsOneWidget);
    expect(client.paths, isEmpty);
    await session.logout();
    await tester.pumpAndSettle();
  });

  testWidgets(
    'technician profile offers account actions and logout switches role cleanly',
    (tester) async {
      final api = RoleAuthApi();
      final (session, client) = await prepare(api);
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          apiClient: client,
          repository: FakeDoorRepository(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Hồ sơ tài khoản'));
      await tester.pumpAndSettle();
      expect(find.text('Hồ sơ tài khoản'), findsOneWidget);
      expect(find.text('Quản lý cửa'), findsNothing);
      expect(find.text('Quản lý lắp đặt'), findsNothing);
      expect(find.text('Người giữ thẻ'), findsNothing);
      await tester.scrollUntilVisible(
        find.text('Đổi mật khẩu'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Đổi mật khẩu'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.widgetWithText(FilledButton, 'Đăng xuất'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Đăng xuất'));
      await tester.pumpAndSettle();
      expect(find.byType(LoginScreen), findsOneWidget);
      api.role = 'CUSTOMER';
      await session.login('customer', 'test-password');
      await tester.pumpAndSettle();
      expect(find.byType(DashboardScreen), findsOneWidget);
      expect(find.byType(TechnicianScreen), findsNothing);
      await session.logout();
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'role lookup failure offers retry before any feature screen loads',
    (tester) async {
      final api = RoleAuthApi()..profileUnavailable = true;
      final (session, client) = await prepare(api);
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          apiClient: client,
          repository: FakeDoorRepository(),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(TechnicianScreen), findsNothing);
      expect(find.byType(DashboardScreen), findsNothing);
      expect(client.paths, isEmpty);
      api.profileUnavailable = false;
      await tester.tap(find.text('Thử lại'));
      await tester.pumpAndSettle();
      expect(find.byType(TechnicianScreen), findsOneWidget);
      await session.logout();
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'late profile from logged out session cannot select the next account home',
    (tester) async {
      final pending = Completer<AccountProfile>();
      final api = RoleAuthApi()..pendingProfile = pending;
      final (session, client) = await prepare(api);
      await tester.pumpWidget(
        SmartDoorApp(
          session: session,
          apiClient: client,
          repository: FakeDoorRepository(),
        ),
      );
      await tester.pump();
      expect(find.byType(DashboardScreen), findsNothing);
      expect(find.byType(TechnicianScreen), findsNothing);
      await session.logout();
      await tester.pumpAndSettle();
      api.pendingProfile = null;
      api.role = 'CUSTOMER';
      await session.login('customer', 'test-password');
      await tester.pumpAndSettle();
      pending.complete(profileFor('TECHNICIAN'));
      await tester.pumpAndSettle();
      expect(find.byType(DashboardScreen), findsOneWidget);
      expect(find.byType(TechnicianScreen), findsNothing);
      expect(client.paths, isEmpty);
      await session.logout();
      await tester.pumpAndSettle();
    },
  );
}
