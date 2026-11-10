import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/access_token_provider.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/door_control/data/unlock_repository.dart';

class CloseTestTokens implements AccessTokenProvider {
  @override
  Future<String?> accessToken() async => 'test-token';
}

void main() {
  test(
    'close sends explicit action to the owned door and keeps it when parsing',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final client = ApiClient(
        tokenProvider: CloseTestTokens(),
        baseUrl: 'http://127.0.0.1:${server.port}',
      );
      addTearDown(() async {
        client.close();
        await server.close(force: true);
      });
      final bodies = <Map<String, dynamic>>[];
      final subscription = server.listen((request) async {
        expect(request.headers.value('authorization'), 'Bearer test-token');
        expect(request.uri.path, '/api/v1/doors/5/commands');
        expect(request.method, 'POST');
        final body = jsonDecode(
          await utf8.decoder.bind(request).join(),
        ) as Map<String, dynamic>;
        bodies.add(body);
        request.response.statusCode = 202;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'data': {
              'id': body['requestId'],
              'status': 'PENDING',
              'action': body['action'],
            },
          }),
        );
        await request.response.close();
      });
      addTearDown(subscription.cancel);
      final repository = RestUnlockRepository(client);
      final result = await repository.closeDoor(5, 'close-test-id');
      expect(result.action, 'CLOSE');
      expect(result.pending, isTrue);
      expect(bodies.single, {'requestId': 'close-test-id', 'action': 'CLOSE'});
      expect((await repository.unlock(5, 'open-test-id')).action, 'UNLOCK');
      expect(bodies.last['action'], 'UNLOCK');
    },
  );
  test(
    'legacy responses default to open; unsupported actions fail parsing',
    () {
      expect(
        UnlockCommand.fromJson({'id': 'old', 'status': 'EXECUTED'}).action,
        'UNLOCK',
      );
      expect(
        () => UnlockCommand.fromJson({
          'id': 'bad',
          'status': 'EXECUTED',
          'action': 'OTHER',
        }),
        throwsFormatException,
      );
    },
  );
}
