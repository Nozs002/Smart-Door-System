import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/access_token_provider.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/access_logs/data/access_repository.dart';

class Tokens implements AccessTokenProvider {
  @override
  Future<String?> accessToken() async => 'test-token';
}

void main() {
  test('access repository scopes and parses history, nullable monitoring and summary', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final client = ApiClient(
      tokenProvider: Tokens(),
      baseUrl: 'http://127.0.0.1:${server.port}',
    );
    addTearDown(() async {
      client.close();
      await server.close(force: true);
    });
    final repository = RestAccessRepository(client);
    final requests = <String>[];
    final subscription = server.listen((request) async {
      expect(request.headers.value('authorization'), 'Bearer test-token');
      expect(request.method, 'GET');
      requests.add(request.uri.path);
      Object? data;
      if (request.uri.path.endsWith('/access-logs')) {
        expect(request.uri.queryParameters, {
          'page': '2',
          'size': '20',
          'method': 'PIN',
          'result': 'DENIED',
        });
        data = {
          'items': [
            {
              'method': 'PIN',
              'result': 'DENIED',
              'reason': 'LOCKED_OUT',
              'receivedAt': '2026-11-02T03:00:00.000',
            },
          ],
          'page': 2,
          'size': 20,
          'totalElements': 41,
          'totalPages': 3,
        };
      } else if (request.uri.path.endsWith('/access-summary')) {
        data = {'cardholderCount': 3, 'latestAccess': null};
      }
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode({'data': data}));
      await request.response.close();
    });
    addTearDown(subscription.cancel);
    final history = await repository.logs(
      8,
      page: 2,
      method: 'PIN',
      result: 'DENIED',
    );
    expect(history.items.single.receivedAt, DateTime.utc(2026, 11, 2, 3));
    expect(history.hasNext, isFalse);
    expect(await repository.monitoring(8), isNull);
    expect((await repository.summary(8)).cardholderCount, 3);
    expect(requests, [
      '/api/v1/doors/8/access-logs',
      '/api/v1/doors/8/monitoring',
      '/api/v1/doors/8/access-summary',
    ]);
  });
}
