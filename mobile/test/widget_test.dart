import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/main.dart';

void main() {
  testWidgets('displays the Smart Door placeholder', (tester) async {
    await tester.pumpWidget(const SmartDoorApp());

    expect(find.text('Smart Door'), findsOneWidget);
  });
}
