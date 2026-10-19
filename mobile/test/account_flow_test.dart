import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/network/api_exception.dart';
import 'package:smart_door_mobile/features/authentication/profile_screen.dart';
import 'package:smart_door_mobile/features/authentication/register_screen.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';

void main() {
  testWidgets(
    'registers then logs in and fetches own profile with glass design',
    (tester) async {
      final api = FakeAuthApi();
      final store = MemorySessionStore();
      final session = testSession(api: api, store: store);
      addTearDown(session.dispose);
      await tester.pumpWidget(SmartDoorApp(session: session));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.widgetWithText(TextButton, 'Đăng ký'));
      await tester.tap(find.widgetWithText(TextButton, 'Đăng ký'));
      await tester.pumpAndSettle();
      final fields = find.byType(TextFormField);
      await tester.enterText(fields.at(0), 'Minh Anh');
      await tester.enterText(fields.at(1), 'minh.anh');
      await tester.enterText(fields.at(2), 'test-password');
      await tester.enterText(fields.at(3), 'test-password');
      await tester.ensureVisible(find.byType(FilledButton));
      await tester.tap(find.byType(FilledButton));
      await tester.pumpAndSettle();
      expect(api.registeredUsername, 'minh.anh');
      expect(store.value, isNull); // Registration must not create a session.
      expect(find.byType(RegisterScreen), findsNothing);
      await tester.enterText(find.byType(TextFormField).at(0), 'minh.anh');
      await tester.enterText(find.byType(TextFormField).at(1), 'test-password');
      await tester.ensureVisible(find.byType(FilledButton));
      await tester.tap(find.byType(FilledButton));
      await tester.pumpAndSettle();
      expect(find.text('Hồ sơ tài khoản'), findsOneWidget);
      expect(find.text('Minh Anh'), findsOneWidget);
      expect(api.receivedToken, 'test-token');
      expect(find.byType(BackdropFilter), findsNWidgets(2));
      expect(store.value, isNot(contains('test-password')));
      await tester.ensureVisible(
        find.widgetWithText(FilledButton, 'Đăng xuất'),
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Đăng xuất'));
      await tester.pumpAndSettle();
      expect(find.text('Chào mừng bạn trở lại.'), findsOneWidget);
      expect(store.value, isNull);
    },
  );

  testWidgets(
    'profile error offers retry and does not discard token on network failure',
    (tester) async {
      final api = FakeAuthApi();
      final session = testSession(api: api);
      addTearDown(session.dispose);
      await session.restore();
      await session.login('customer', 'test-password');
      api.failure = const ApiException('Không thể kết nối máy chủ.');
      await tester.pumpWidget(
        MaterialApp(home: ProfileScreen(session: session)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Không thể kết nối máy chủ.'), findsOneWidget);
      expect(session.signedIn, isTrue);
      api.failure = null;
      await tester.ensureVisible(find.text('Tải lại hồ sơ'));
      await tester.tap(find.text('Tải lại hồ sơ'));
      await tester.pumpAndSettle();
      expect(find.text('Khách hàng'), findsOneWidget);
      await session.logout();
    },
  );

  testWidgets('registration checks both minimum and UTF8 byte limit', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: RegisterScreen()));
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Khách hàng');
    await tester.enterText(fields.at(1), 'customer');
    await tester.enterText(fields.at(2), 'short');
    await tester.enterText(fields.at(3), 'short');
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Mật khẩu cần ít nhất 8 ký tự.'), findsOneWidget);
    await tester.enterText(fields.at(2), 'ấ' * 25);
    await tester.enterText(fields.at(3), 'ấ' * 25);
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Mật khẩu tối đa 72 byte UTF-8.'), findsOneWidget);
  });
}
