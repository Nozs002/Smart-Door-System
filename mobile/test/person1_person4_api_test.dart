import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/access_token_provider.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/credentials/data/credential_models.dart';
import 'package:smart_door_mobile/features/credentials/data/credential_repository.dart';
import 'package:smart_door_mobile/features/notifications/data/notification_repository.dart';
import 'package:smart_door_mobile/features/settings/data/settings_repository.dart';

class _Tokens implements AccessTokenProvider {
  @override
  Future<String?> accessToken() async => 'test-token';
}

void main() {
  late HttpServer server;
  late ApiClient client;
  late RestCredentialRepository credentials;
  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    client = ApiClient(
      tokenProvider: _Tokens(),
      baseUrl: 'http://127.0.0.1:${server.port}',
    );
    credentials = RestCredentialRepository(client);
  });
  tearDown(() async {
    client.close();
    await server.close(force: true);
  });

  test('RFID issuance normalizes hex and sends UTC expiry only within selected door', () async {
    final received = server.first;
    final expiry = DateTime.parse('2027-01-01T23:59:59+07:00');
    final result = credentials.issueRfid(12, 7, 'a1:b2-c3 d4', expiry);
    final request = await received;
    expect(request.method, 'POST');
    expect(request.uri.path, '/api/v1/doors/12/rfid-credentials');
    expect(request.headers.value('authorization'), 'Bearer test-token');
    expect(jsonDecode(await utf8.decoder.bind(request).join()), {
      'cardholderId': 7,
      'uid': 'A1B2C3D4',
      'expiresAt': '2027-01-01T16:59:59.000Z',
    });
    request.response.statusCode = 201;
    request.response.write(
      jsonEncode({
        'data': {
          'id': 31,
          'doorId': 12,
          'cardholderId': 7,
          'cardholderName': 'An',
          'uid': 'A1B2C3D4',
          'status': 'ACTIVE',
          'issuedAt': '2026-10-28T01:00:00Z',
          'expiresAt': expiry.toUtc().toIso8601String(),
        },
      }),
    );
    await request.response.close();
    expect((await result).doorId, 12);
    expect((await result).expiresAt!.isUtc, isTrue);
  });

  test(
    'cardholder pagination and disable preserve account-derived ownership',
    () async {
      var count = 0;
      server.listen((request) async {
        if (count++ == 0) {
          expect(request.uri.path, '/api/v1/cardholders');
          expect(request.uri.queryParameters, {'page': '2', 'size': '20'});
          request.response.write(
            '{"data":{"items":[],"page":2,"size":20,"totalElements":40,"totalPages":2}}',
          );
        } else {
          expect(request.method, 'PATCH');
          expect(request.uri.path, '/api/v1/cardholders/7');
          expect(jsonDecode(await utf8.decoder.bind(request).join()), {
            'displayName': 'An',
            'status': 'DISABLED',
          });
          request.response.write(
            '{"data":{"id":7,"displayName":"An","status":"DISABLED","createdAt":"2026-10-28T01:00:00Z"}}',
          );
        }
        await request.response.close();
      });
      expect((await credentials.listCardholders(page: 2)).hasNext, isFalse);
      expect(
        (await credentials.updateCardholder(7, ' An ', 'DISABLED')).status,
        'DISABLED',
      );
    },
  );

  test('PIN endpoint sends only the new PIN and parses safe status', () async {
    final received = server.first;
    final result = credentials.changePin(12, '001234');
    final request = await received;
    expect(request.method, 'PUT');
    expect(request.uri.path, '/api/v1/doors/12/pin');
    expect(jsonDecode(await utf8.decoder.bind(request).join()), {
      'pin': '001234',
    });
    request.response.write(
      '{"data":{"doorId":12,"configured":true,"status":"ACTIVE","version":8,"changedAt":"2026-10-28T01:00:00Z"}}',
    );
    await request.response.close();
    expect((await result).version, 8);
  });

  test(
    'notification unread filter and read are separate from alert status',
    () async {
      final repository = RestNotificationRepository(client);
      var count = 0;
      server.listen((request) async {
        final notification = {
          'id': 9,
          'alertId': 3,
          'doorId': 12,
          'title': 'Cảnh báo',
          'body': 'Nhiệt độ cao',
          'createdAt': '2026-10-28T01:00:00Z',
          'readAt': count == 0 ? null : '2026-10-28T02:00:00Z',
        };
        if (count++ == 0) {
          expect(request.uri.path, '/api/v1/notifications');
          expect(request.uri.queryParameters, {
            'page': '0',
            'size': '20',
            'unread': 'true',
          });
          request.response.write(
            jsonEncode({
              'data': {
                'items': [notification],
                'page': 0,
                'size': 20,
                'totalElements': 1,
                'totalPages': 1,
              },
            }),
          );
        } else {
          expect(request.method, 'POST');
          expect(request.uri.path, '/api/v1/notifications/9/read');
          request.response.write(jsonEncode({'data': notification}));
        }
        await request.response.close();
      });
      expect((await repository.list(unread: true)).items.single.unread, isTrue);
      expect((await repository.markRead(9)).unread, isFalse);
      expect(count, 2);
    },
  );

  test(
    'sync retry sends no settings mutation and keeps current version',
    () async {
      final received = server.first;
      final result = RestSettingsRepository(client).retrySync(12);
      final request = await received;
      expect(request.method, 'POST');
      expect(request.uri.path, '/api/v1/doors/12/sync/retry');
      expect(await utf8.decoder.bind(request).join(), isEmpty);
      request.response.write(
        '{"data":{"doorId":12,"maxFailedAttempts":5,"lockoutSeconds":60,"temperatureThreshold":50,"version":4,"credentialsVersion":8,"devices":[]}}',
      );
      await request.response.close();
      expect((await result).version, 4);
    },
  );

  test('401 with malformed body still clears the current session', () async {
    var expired = false;
    client.close();
    client = ApiClient(
      tokenProvider: _Tokens(),
      baseUrl: 'http://127.0.0.1:${server.port}',
      onUnauthorized: () async => expired = true,
    );
    final received = server.first;
    final result = client.request('GET', '/api/v1/cardholders');
    final assertion = expectLater(
      result,
      throwsA(isA<ApiException>().having((e) => e.statusCode, 'status', 401)),
    );
    final request = await received;
    request.response.statusCode = 401;
    request.response.write('not-json');
    await request.response.close();
    await assertion;
    expect(expired, isTrue);
  });

  test('RFID and PIN input rules match firmware contracts', () {
    expect(validRfidUid('a1:b2-c3 d4'), isTrue);
    expect(validRfidUid('AABBCCDDEEFF00'), isTrue);
    expect(validRfidUid('not-a-card'), isFalse);
    expect(validDoorPin('001234'), isTrue);
    expect(validDoorPin('12a4'), isFalse);
    expect(validDoorPin('123'), isFalse);
  });
}
