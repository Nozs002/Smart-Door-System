import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/network/api_exception.dart';
import 'package:smart_door_mobile/features/authentication/auth_api.dart';

void main() {
  late HttpServer server;
  late HttpAuthApi api;
  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    api = HttpAuthApi(baseUri: Uri.parse('http://127.0.0.1:${server.port}'));
  });
  tearDown(() => server.close(force: true));

  test(
    'register sends unmodified credentials without a bearer token',
    () async {
      server.listen((request) async {
        expect(request.method, 'POST');
        expect(request.uri.path, '/api/v1/auth/register');
        expect(request.headers.value('Authorization'), isNull);
        final body = jsonDecode(await utf8.decoder.bind(request).join());
        expect(body['username'], ' customer ');
        expect(body['password'], ' password ');
        request.response.statusCode = 201;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'data': {
              'id': 1,
              'username': ' customer ',
              'displayName': 'Customer',
              'status': 'ACTIVE',
              'createdAt': '2026-10-14T00:00:00Z',
            },
          }),
        );
        await request.response.close();
      });
      await api.register('Customer', ' customer ', ' password ');
    },
  );

  test('profile attaches bearer and parses only a valid account', () async {
    server.listen((request) async {
      expect(request.uri.path, '/api/v1/accounts/me');
      expect(request.headers.value('Authorization'), 'Bearer test-token');
      request.response.write(
        jsonEncode({
          'data': {
            'id': 1,
            'username': 'customer',
            'displayName': 'Customer',
            'status': 'ACTIVE',
            'createdAt': '2026-10-14T00:00:00Z',
          },
        }),
      );
      await request.response.close();
    });
    final profile = await api.profile('test-token');
    expect(profile.id, 1);
    expect(profile.createdAt.isUtc, isTrue);
  });

  test('conflict and malformed response produce safe client errors', () async {
    var count = 0;
    server.listen((request) async {
      if (count++ == 0) {
        request.response.statusCode = 409;
        request.response.write(
          '{"errorCode":"CONFLICT","detail":"secret-internal-details"}',
        );
      } else {
        request.response.write('{"data":{"id":"wrong-type"}}');
      }
      await request.response.close();
    });
    await expectLater(
      api.register('Customer', 'customer', 'password'),
      throwsA(
        isA<ApiException>()
            .having((error) => error.statusCode, 'status', 409)
            .having(
              (error) => error.message,
              'safe message',
              'Tên đăng nhập đã được sử dụng.',
            ),
      ),
    );
    await expectLater(api.profile('test-token'), throwsA(isA<ApiException>()));
  });

  test('redirect response is not followed with credentials', () async {
    var calls = 0;
    server.listen((request) async {
      calls++;
      request.response.statusCode = 302;
      request.response.headers.set('location', '/unexpected');
      await request.response.close();
    });
    await expectLater(api.profile('test-token'), throwsA(isA<ApiException>()));
    expect(calls, 1);
  });

  test(
    'password change and logout send authenticated POST contracts',
    () async {
      var count = 0;
      server.listen((request) async {
        expect(request.method, 'POST');
        expect(request.headers.value('Authorization'), 'Bearer test-token');
        if (count++ == 0) {
          expect(request.uri.path, '/api/v1/accounts/me/password');
          expect(jsonDecode(await utf8.decoder.bind(request).join()), {
            'currentPassword': ' old password ',
            'newPassword': ' new password ',
          });
          request.response.write('{"data":{"requiresLogin":true}}');
        } else {
          expect(request.uri.path, '/api/v1/auth/logout');
          request.response.write('{"data":{"loggedOut":true}}');
        }
        await request.response.close();
      });
      await api.changePassword(
        'test-token',
        ' old password ',
        ' new password ',
      );
      await api.logout('test-token');
      expect(count, 2);
    },
  );
}
