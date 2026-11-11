import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/main.dart';

import 'auth_fakes.dart';
import 'support/fake_door_repository.dart';

void main() {
  testWidgets('login session opens owned doors and expires back to login', (
    tester,
  ) async {
    final session = testSession();
    addTearDown(session.dispose);
    await session.login('test', '123456789');
    expect(await session.accessToken(), 'test-token');
    await tester.pumpWidget(
      SmartDoorApp(session: session, repository: FakeDoorRepository()),
    );
    await tester.pumpAndSettle();
    expect(find.text('TỔNG QUAN CỬA'), findsOneWidget);
    expect(find.text('Hồ sơ tài khoản'), findsNothing);
    await tester.scrollUntilVisible(
      find.text('Quản lý cửa'),
      450,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Quản lý cửa'));
    await tester.pumpAndSettle();
    expect(find.text('Cửa của bạn'), findsOneWidget);
    await session.expire();
    await tester.pumpAndSettle();
    expect(await session.accessToken(), isNull);
    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(find.text('Quản lý cửa'), findsNothing);
  });
}
