import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/api_client.dart' as door_api;
import 'package:smart_door_mobile/core/network/api_exception.dart';
import 'package:smart_door_mobile/features/authentication/password_change_screen.dart';
import 'package:smart_door_mobile/features/credentials/presentation/cardholder_screen.dart';
import 'package:smart_door_mobile/features/credentials/presentation/pin_screen.dart';
import 'package:smart_door_mobile/features/credentials/presentation/rfid_screen.dart';
import 'package:smart_door_mobile/features/doors/presentation/door_list_screen.dart';

import 'auth_fakes.dart';
import 'support/fake_credential_repository.dart';
import 'support/fake_door_repository.dart';

void main() {
  testWidgets('door list exposes credentials from the selected door details', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: DoorListScreen(
          repository: FakeDoorRepository(),
          credentialRepository: FakeCredentialRepository(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cửa trước'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('PIN của cửa'),
      150,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.ensureVisible(find.text('PIN của cửa'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('PIN của cửa'));
    await tester.pumpAndSettle();
    expect(find.text('PIN · Cửa trước'), findsOneWidget);
    expect(find.text('Cửa chưa có PIN'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'create, rename and disable cardholder without deleting history',
    (tester) async {
      final repository = FakeCredentialRepository();
      await tester.pumpWidget(
        MaterialApp(
          home: CardholderScreen(
            repository: repository,
            onSignInRequested: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thêm người giữ thẻ'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField), 'Bình');
      await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
      await tester.pumpAndSettle();
      expect(repository.people.length, 2);
      expect(find.text('Bình'), findsOneWidget);
      await tester.tap(find.text('An'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField), 'An mới');
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Vô hiệu hóa').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
      await tester.pumpAndSettle();
      expect(repository.people.first.displayName, 'An mới');
      expect(repository.people.first.status, 'DISABLED');
      expect(repository.people.length, 2);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'PIN returns after saving but stays open on validation or API failure',
    (tester) async {
      final repository = FakeCredentialRepository();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => Navigator.of(context).push<void>(
                  MaterialPageRoute(
                    builder: (_) => DoorPinScreen(
                      repository: repository,
                      doorId: 12,
                      doorName: 'Cửa trước',
                      onSignInRequested: () {},
                    ),
                  ),
                ),
                child: const Text('Đổi PIN của cửa'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Đổi PIN của cửa'));
      await tester.pumpAndSettle();
      expect(find.text('Cửa chưa có PIN'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('doorPin')), '001234');
      await tester.enterText(find.byKey(const Key('confirmDoorPin')), '9999');
      await tester.tap(find.text('Lưu PIN mới'));
      await tester.pumpAndSettle();
      expect(repository.pinChanges, isEmpty);
      expect(find.text('PIN xác nhận chưa khớp.'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('confirmDoorPin')), '001234');
      repository.pinFailure = const door_api.ApiException(
        'Không thể lưu PIN. Thử lại.',
      );
      await tester.tap(find.text('Lưu PIN mới'));
      await tester.pumpAndSettle();
      expect(find.byType(DoorPinScreen), findsOneWidget);
      expect(find.text('Không thể lưu PIN. Thử lại.'), findsOneWidget);
      expect(repository.pinChanges, isEmpty);

      repository.pinFailure = null;
      await tester.tap(find.text('Lưu PIN mới'));
      await tester.pumpAndSettle();
      expect(repository.pinChanges, [(12, '001234')]);
      expect(find.byType(DoorPinScreen), findsNothing);
      expect(find.text('Đổi PIN của cửa'), findsOneWidget);
      expect(
        find.text(
          'Đã lưu PIN mới. Kiểm tra trạng thái đồng bộ trước khi sử dụng tại thiết bị.',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Đổi PIN của cửa'));
      await tester.pumpAndSettle();
      expect(find.text('Đã thiết lập PIN · phiên bản 1'), findsOneWidget);
      for (final input in tester.widgetList<TextFormField>(
        find.byType(TextFormField),
      )) {
        expect(input.controller!.text, isEmpty);
      }
      for (final input in tester.widgetList<TextField>(
        find.byType(TextField),
      )) {
        expect(input.obscureText, isTrue);
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('RFID issuance and revocation stay scoped to selected door', (
    tester,
  ) async {
    final repository = FakeCredentialRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: RfidScreen(
          repository: repository,
          doorId: 12,
          doorName: 'Cửa trước',
          onSignInRequested: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Cấp thẻ RFID'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('rfidCardholder')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('An').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('rfidUid')), 'a1:b2:c3:d4');
    await tester.tap(find.widgetWithText(FilledButton, 'Cấp thẻ'));
    await tester.pumpAndSettle();
    expect(repository.issued, [(12, 7, 'a1:b2:c3:d4')]);
    expect(find.text('UID: A1B2C3D4'), findsOneWidget);
    await tester.tap(find.text('Thu hồi thẻ'));
    await tester.pumpAndSettle();
    expect(repository.revoked, isEmpty);
    await tester.tap(find.widgetWithText(FilledButton, 'Thu hồi'));
    await tester.pumpAndSettle();
    expect(repository.revoked, [(12, 31)]);
    expect(find.text('Trạng thái: Đã thu hồi'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'password form validates confirmation and preserves session on rejection',
    (tester) async {
      final api = FakeAuthApi();
      final session = testSession(api: api);
      addTearDown(session.dispose);
      await session.login('test', '123456789');
      api.failure = const ApiException(
        'Sai mật khẩu hiện tại',
        statusCode: 400,
      );
      await tester.pumpWidget(
        MaterialApp(home: PasswordChangeScreen(session: session)),
      );
      await tester.enterText(find.byKey(const Key('currentPassword')), 'wrong');
      await tester.enterText(
        find.byKey(const Key('newPassword')),
        'newpassword',
      );
      await tester.enterText(
        find.byKey(const Key('confirmPassword')),
        'different',
      );
      await tester.tap(find.text('Xác nhận đổi mật khẩu'));
      await tester.pumpAndSettle();
      expect(api.passwordChangeCalls, 0);
      expect(find.text('Mật khẩu xác nhận chưa khớp.'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('confirmPassword')),
        'newpassword',
      );
      await tester.tap(find.text('Xác nhận đổi mật khẩu'));
      await tester.pumpAndSettle();
      expect(api.passwordChangeCalls, 1);
      expect(session.signedIn, isTrue);
      expect(find.text('Sai mật khẩu hiện tại'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await session.expire();
    },
  );
}
