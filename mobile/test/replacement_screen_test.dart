import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/access_token_provider.dart';
import 'package:smart_door_mobile/core/api_client.dart';
import 'package:smart_door_mobile/features/onboarding/replacement_screen.dart';
import 'package:smart_door_mobile/features/doors/data/door_repository.dart';
import 'package:smart_door_mobile/features/doors/presentation/device_detail_screen.dart';

class ReplacementClient extends ApiClient {
  ReplacementClient() : super(tokenProvider: const SignedOutTokenProvider());
  Map<String, dynamic>? ticket;
  Map<String, dynamic>? boundPayload;
  int completeCalls = 0;
  bool retired = false;
  final calls = <String>[];

  Map<String, dynamic> device() => {
    'id': 11,
    'doorId': 1,
    'deviceCode': 'OLD-N1',
    'nodeType': 'NODE_1',
    'lifecycleStatus': retired ? 'RETIRED' : 'ACTIVE',
    'connectionStatus': 'OFFLINE',
  };
  Map<String, dynamic> pending() => {
    'id': '11111111-1111-4111-8111-111111111111',
    'doorId': 1,
    'oldDeviceId': 11,
    'newDeviceId': 12,
    'nodeType': 'NODE_1',
    'status': 'BOUND',
    'reason': 'Bo bị hỏng',
    'ready': true,
    'brokerRevoked': false,
    'newDeviceCode': 'NEW-N1',
  };
  @override
  Future<dynamic> request(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
  }) async {
    calls.add('$method $path');
    if (path == '/api/v1/doors/1/devices') return [device()];
    if (path == '/api/v1/doors/1/devices/11') return device();
    if (path.endsWith('/replacement-request')) return ticket;
    if (path.endsWith('/replacement-requests')) {
      ticket = pending()
        ..addAll({
          'status': 'OPEN',
          'newDeviceId': null,
          'authorizationCode': 'RPL-EXAMPLE-NOT-REAL',
          'expiresAt': '2026-11-19T00:00:00',
        });
      expect(body!['reason'], 'Bo bị hỏng');
      expect(body['requestId'], matches(RegExp(r'^[a-f0-9-]{36}$')));
      return ticket;
    }
    if (path.endsWith('/complete')) {
      completeCalls++;
      ticket = pending()
        ..['status'] = completeCalls == 1 ? 'REVOKING' : 'COMPLETED';
      return ticket;
    }
    if (path == '/api/v1/technician/replacements') {
      if (method == 'POST') {
        boundPayload = body;
        return pending();
      }
      return [];
    }
    throw StateError('Unexpected request: $method $path');
  }
}

void phoneSize(WidgetTester tester) {
  tester.view.physicalSize = const Size(430, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  testWidgets('owner creates a scoped grant with a copyable code', (
    tester,
  ) async {
    phoneSize(tester);
    final client = ReplacementClient();
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: ReplacementScreen(client: client, doorId: 1, deviceId: 11),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Bo bị hỏng');
    await tester.tap(find.text('Cấp mã ủy quyền thay Node'));
    await tester.pumpAndSettle();
    expect(find.text('RPL-EXAMPLE-NOT-REAL'), findsOneWidget);
    expect(find.text('Sao chép mã ủy quyền'), findsOneWidget);
    expect(client.completeCalls, 0);
  });

  testWidgets(
    'technician can bind through manual inputs without customer access',
    (tester) async {
      phoneSize(tester);
      final client = ReplacementClient();
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(home: TechnicianReplacementScreen(client: client)),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextFormField).at(0),
        ' RPL-EXAMPLE-NOT-REAL ',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        '11111111-1111-4111-8111-111111111111',
      );
      await tester.tap(find.text('Gắn bo vào cửa được ủy quyền'));
      await tester.pumpAndSettle();
      expect(client.boundPayload!['authorizationCode'], 'RPL-EXAMPLE-NOT-REAL');
      expect(
        client.boundPayload!['unitCode'],
        '11111111-1111-4111-8111-111111111111',
      );
      expect(client.calls.any((path) => path.contains('/doors/')), isFalse);
    },
  );

  testWidgets(
    'completion stays pending until Broker proof and cancels polling on success',
    (tester) async {
      phoneSize(tester);
      final client = ReplacementClient();
      client.ticket = client.pending();
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          home: ReplacementScreen(client: client, doorId: 1, deviceId: 11),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận thay Node'));
      await tester.pumpAndSettle();
      expect(client.completeCalls, 0);
      await tester.tap(find.text('Xác nhận thay'));
      await tester.pumpAndSettle();
      expect(client.completeCalls, 1);
      expect(find.text('Đang thu hồi kết nối bo cũ'), findsOneWidget);
      expect(find.text('Đã thay thiết bị'), findsNothing);
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Đã thay thiết bị'), findsOneWidget);
      expect(client.completeCalls, 2);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'retired device exposes replacement recovery on the real API path',
    (tester) async {
      phoneSize(tester);
      final client = ReplacementClient()..retired = true;
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          home: DeviceDetailScreen(
            repository: RestDoorRepository(client),
            doorId: 1,
            deviceId: 11,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Bổ sung Node thay thế'));
      await tester.tap(find.text('Bổ sung Node thay thế'));
      await tester.pumpAndSettle();
      expect(find.text('Cấp mã ủy quyền thay Node'), findsOneWidget);
      expect(find.text('Kích hoạt lại thiết bị'), findsNothing);
    },
  );
}
