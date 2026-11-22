import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/onboarding/code_input.dart';

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
}
