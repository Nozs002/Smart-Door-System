import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/onboarding/code_input.dart';
import 'package:smart_door_mobile/features/onboarding/door_setup_screen.dart';

void main() {
  testWidgets('code field requires a nonblank value', (tester) async {
    final controller = TextEditingController();
    final form = GlobalKey<FormState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Form(
            key: form,
            child: CodeField(controller: controller, label: 'Mã Node 1'),
          ),
        ),
      ),
    );
    expect(form.currentState!.validate(), isFalse);
    await tester.enterText(find.byType(TextFormField), '   ');
    expect(form.currentState!.validate(), isFalse);
    await tester.enterText(find.byType(TextFormField), 'board-manual');
    expect(form.currentState!.validate(), isTrue);
    expect(controller.text, 'board-manual');
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });

  testWidgets('Wi-Fi setup reports a mismatched Node and blocks credentials', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(800, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    const unitCode = '11111111-1111-1111-1111-111111111111';
    const channel = MethodChannel('smartdoor/network');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final paths = <String>[];
    var reportedType = 'NODE_2';
    messenger.setMockMethodCallHandler(channel, (call) async {
      final path = call.arguments['path'] as String;
      paths.add(path);
      return {
        'status': 200,
        'body': jsonEncode(
          path == '/api/info'
              ? {'unitCode': unitCode, 'nodeType': reportedType}
              : {'saved': true},
        ),
      };
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    await tester.pumpWidget(
      const MaterialApp(
        home: NodeWifiScreen(
          node: {
            'nodeType': 'NODE_1',
            'unitCode': unitCode,
            'setupSsid': 'SmartDoor-11111111',
            'setupCode': 'test-setup-code',
          },
        ),
      ),
    );
    await tester.enterText(find.byType(TextFormField).at(1), 'House');
    final save = find.text('Lưu Wi-Fi vào Node');
    await tester.tap(save);
    await tester.pumpAndSettle();

    expect(paths, ['/api/info']);
    expect(
      find.textContaining('Cần kết nối SmartDoor-11111111 (NODE_1)'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Node trả về: NODE_2 · $unitCode'),
      findsOneWidget,
    );

    reportedType = 'NODE_1';
    await tester.tap(save);
    await tester.pumpAndSettle();
    expect(paths, ['/api/info', '/api/info', '/api/wifi']);
  });
}
