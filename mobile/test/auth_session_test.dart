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
}
