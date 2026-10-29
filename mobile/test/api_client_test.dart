import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/access_token_provider.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/doors/data/door_repository.dart';

class _TokenProvider implements AccessTokenProvider {
  String? token = 'test-token';

  @override
  Future<String?> accessToken() async => token;
}

void main() {
  late HttpServer server;
  late ApiClient client;
  late _TokenProvider tokens;

  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    tokens = _TokenProvider();
    client = ApiClient(
      tokenProvider: tokens,
      baseUrl: 'http://${server.address.host}:${server.port}',
    );
  });

  tearDown(() async {
    client.close();
    await server.close(force: true);
  });

  test(
    'claim sends bearer identity and normalized code, unwraps API response',
    () async {
      final received = server.first;
      final result = RestDoorRepository(client).claimDoor('k7mp-4q2d');
      final request = await received;
      expect(request.method, 'POST');
      expect(request.uri.path, '/api/v1/doors/claim');
      expect(request.headers.value('authorization'), 'Bearer test-token');
      expect(jsonDecode(await utf8.decoder.bind(request).join()), {
        'activationCode': 'K7MP4Q2D',
      });
      request.response.headers.contentType = ContentType.json;
      request.response.write(
        jsonEncode({
          'message': 'Thành công',
          'data': {'id': 1, 'name': 'Cửa trước', 'status': 'SETUP_PENDING'},
          'timestamp': '2026-10-16T09:00:00Z',
          'correlationId': 'test-request',
        }),
      );
      await request.response.close();
      expect((await result).status, 'SETUP_PENDING');
    },
  );

  test('missing token fails before any network request', () async {
    tokens.token = null;
    await expectLater(
      client.request('GET', '/api/v1/doors'),
      throwsA(
        isA<ApiException>().having(
          (error) => error.authenticationRequired,
          'authentication required',
          true,
        ),
      ),
    );
  });

  test('ProblemDetail preserves readiness error detail and code', () async {
    final received = server.first;
    final result = client.request(
      'POST',
      '/api/v1/doors/1/devices/2/lifecycle',
    );
    final assertion = expectLater(
      result,
      throwsA(
        isA<ApiException>()
            .having((error) => error.statusCode, 'status', 409)
            .having((error) => error.errorCode, 'code', 'DEVICE_NOT_READY')
            .having((error) => error.message, 'detail', 'Node chưa sẵn sàng.'),
      ),
    );
    final request = await received;
    request.response.statusCode = 409;
    request.response.headers.contentType = ContentType.json;
    request.response.write(
      jsonEncode({
        'detail': 'Node chưa sẵn sàng.',
        'errorCode': 'DEVICE_NOT_READY',
      }),
    );
    await request.response.close();
    await assertion;
  });

  test('history sends device filter and pagination', () async {
    final received = server.first;
    final result = RestDoorRepository(client)
        .lifecycleEvents(1, page: 2, deviceId: 11);
    final request = await received;
    expect(request.uri.path, '/api/v1/doors/1/lifecycle-events');
    expect(request.uri.queryParameters, {
      'page': '2',
      'size': '20',
      'deviceId': '11',
    });
    request.response.headers.contentType = ContentType.json;
    request.response.write(
      jsonEncode({
        'data': {
          'items': [],
          'page': 2,
          'size': 20,
          'totalElements': 40,
          'totalPages': 2,
        },
      }),
    );
    await request.response.close();
    expect((await result).hasNext, false);
  });
}
