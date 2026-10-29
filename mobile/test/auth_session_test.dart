import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/network/api_exception.dart';

import 'auth_fakes.dart';

void main() {
  test(
    'restores valid session and clears it when server returns 401',
    () async {
      final store = MemorySessionStore();
      store.value = jsonEncode({
        'token': 'test-token',
        'expiresAt': DateTime.now()
            .toUtc()
            .add(const Duration(minutes: 15))
            .toIso8601String(),
      });
      final api = FakeAuthApi();
      final session = testSession(api: api, store: store);
      addTearDown(session.dispose);
      await session.restore();
      expect(session.signedIn, isTrue);
      api.failure = const ApiException('Expired', statusCode: 401);
      await expectLater(session.profile(), throwsA(isA<ApiException>()));
      expect(session.signedIn, isFalse);
      expect(store.value, isNull);
    },
  );
  test('expired and corrupt persisted sessions do not restore', () async {
    for (final saved in [
      'not-json',
      jsonEncode({'token': 'test', 'expiresAt': '2020-01-01T00:00:00Z'}),
    ]) {
      final store = MemorySessionStore()..value = saved;
      final session = testSession(store: store);
      await session.restore();
      expect(session.signedIn, isFalse);
      expect(store.value, isNull);
      session.dispose();
    }
  });

  test(
    'logout revokes the current server token and clears secure storage',
    () async {
      final api = FakeAuthApi();
      final store = MemorySessionStore();
      final session = testSession(api: api, store: store);
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      await session.logout();
      expect(api.logoutCalls, 1);
      expect(api.receivedToken, 'test-token');
      expect(session.signedIn, isFalse);
      expect(store.value, isNull);
      expect(session.notice, isNull);
    },
  );

  test(
    'logout network failure clears locally without claiming server revocation',
    () async {
      final api = FakeAuthApi();
      final store = MemorySessionStore();
      final session = testSession(api: api, store: store);
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      api.failure = const ApiException('Mất kết nối');
      await session.logout();
      expect(session.signedIn, isFalse);
      expect(store.value, isNull);
      expect(session.notice, contains('chưa xác nhận'));
    },
  );

  test(
    'password rejection preserves session; success requires a new login',
    () async {
      final api = FakeAuthApi();
      final store = MemorySessionStore();
      final session = testSession(api: api, store: store);
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      api.failure = const ApiException(
        'Sai mật khẩu hiện tại',
        statusCode: 400,
      );
      await expectLater(
        session.changePassword('wrong', 'newpassword'),
        throwsA(isA<ApiException>()),
      );
      expect(session.signedIn, isTrue);
      expect(store.value, isNotNull);
      api.failure = null;
      await session.changePassword('oldpassword', 'newpassword');
      expect(api.passwordChangeCalls, 2);
      expect(session.signedIn, isFalse);
      expect(store.value, isNull);
      expect(session.notice, contains('Đã đổi mật khẩu'));
    },
  );
}
