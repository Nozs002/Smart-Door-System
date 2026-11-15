import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/onboarding/code_input.dart';

void main() {
  test('QR and manual codes resolve to the same identity', () {
    const code = '11111111-1111-4111-8111-111111111111';
    expect(readCode('  $code  ', 'unit'), code);
    expect(
      readCode('smartdoor://unit/$code?setup=private-test-code', 'unit'),
      code,
    );
    expect(readCode('smartdoor://claim/K7MP4Q2D', 'claim'), 'K7MP4Q2D');
  });
  test('claim and device QR codes cannot be exchanged', () {
    expect(
      () => readCode('smartdoor://unit/board', 'claim'),
      throwsFormatException,
    );
    expect(
      () => readCode('smartdoor://claim/code', 'unit'),
      throwsFormatException,
    );
    expect(
      () => readCode('smartdoor://unit/a/b', 'unit'),
      throwsFormatException,
    );
  });
  testWidgets('manual entry works without opening camera', (tester) async {
    final controller = TextEditingController();
    final form = GlobalKey<FormState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Form(
            key: form,
            child: CodeField(
              controller: controller,
              label: 'Mã Node 1',
              kind: 'unit',
            ),
          ),
        ),
      ),
    );
    expect(find.byTooltip('Quét QR'), findsOneWidget);
    expect(form.currentState!.validate(), isFalse);
    await tester.enterText(find.byType(TextFormField), 'board-manual');
    expect(form.currentState!.validate(), isTrue);
    expect(controller.text, 'board-manual');
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });
}
