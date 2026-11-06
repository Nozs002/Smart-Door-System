import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/main.dart';
import 'package:smart_door_mobile/features/authentication/auth_widgets.dart';
import 'package:smart_door_mobile/features/authentication/login_screen.dart';
import 'package:smart_door_mobile/features/authentication/register_screen.dart';

import 'auth_fakes.dart';

void main() {
  testWidgets('opens registration and returns to login with glass cards', (
    tester,
  ) async {
    final session = testSession();
    addTearDown(session.dispose);
    await tester.pumpWidget(SmartDoorApp(session: session));
    await tester.pumpAndSettle();
    expect(find.text('SmartLock'), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNWidgets(2));
    await tester.ensureVisible(find.widgetWithText(TextButton, 'Đăng ký'));
    await tester.tap(find.widgetWithText(TextButton, 'Đăng ký'));
    await tester.pumpAndSettle();
    expect(find.text('Tạo tài khoản'), findsOneWidget);
    expect(find.byType(TextFormField), findsNWidgets(4));
    await tester.ensureVisible(find.widgetWithText(TextButton, 'Đăng nhập'));
    await tester.tap(find.widgetWithText(TextButton, 'Đăng nhập'));
    await tester.pumpAndSettle();
    expect(find.byType(TextFormField), findsNWidgets(2));
  });

  testWidgets('validates required fields and UTF-8 password limit', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: LoginScreen()));
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Vui lòng nhập tên đăng nhập.'), findsOneWidget);
    expect(find.text('Vui lòng nhập mật khẩu.'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField).at(0), 'customer');
    await tester.enterText(find.byType(TextFormField).at(1), 'ấ' * 25);
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Mật khẩu tối đa 72 byte UTF-8.'), findsOneWidget);
  });

  for (final completeRegistration in [false, true]) {
    testWidgets(
      'returning from registration clears login feedback (registered: $completeRegistration)',
      (tester) async {
        const expired = 'Phiên đã hết hạn. Vui lòng đăng nhập lại.';
        const rejected = 'Thông tin đăng nhập không hợp lệ.';
        await tester.pumpWidget(
          MaterialApp(
            home: LoginScreen(
              notice: expired,
              onSubmit: (_, _) async => throw const AuthFormException(rejected),
              onRegister: (_, _, _) async {},
            ),
          ),
        );
        expect(find.text(expired), findsOneWidget);
        await tester.enterText(find.byType(TextFormField).at(0), 'customer');
        await tester.enterText(
          find.byType(TextFormField).at(1),
          'wrong-password',
        );
        await tester.ensureVisible(find.byType(FilledButton));
        await tester.tap(find.byType(FilledButton));
        await tester.pumpAndSettle();
        expect(find.text(rejected), findsOneWidget);

        await tester.ensureVisible(find.widgetWithText(TextButton, 'Đăng ký'));
        await tester.tap(find.widgetWithText(TextButton, 'Đăng ký'));
        await tester.pumpAndSettle();
        if (completeRegistration) {
          final fields = find.byType(TextFormField);
          await tester.enterText(fields.at(0), 'Tài khoản mới');
          await tester.enterText(fields.at(1), 'new-customer');
          await tester.enterText(fields.at(2), 'new-password');
          await tester.enterText(fields.at(3), 'new-password');
          await tester.ensureVisible(find.byType(FilledButton));
          await tester.tap(find.byType(FilledButton));
        } else {
          await tester.ensureVisible(
            find.widgetWithText(TextButton, 'Đăng nhập'),
          );
          await tester.tap(find.widgetWithText(TextButton, 'Đăng nhập'));
        }
        await tester.pumpAndSettle();
        expect(find.byType(LoginScreen), findsOneWidget);
        expect(find.byType(RegisterScreen), findsNothing);
        expect(find.text(rejected), findsNothing);
        expect(find.text(expired), findsNothing);
        expect(
          tester
              .widget<TextFormField>(find.byType(TextFormField).at(1))
              .controller!
              .text,
          isEmpty,
        );
        if (completeRegistration) {
          expect(
            find.text('Tạo tài khoản thành công. Vui lòng đăng nhập.'),
            findsOneWidget,
          );
        }

        // A new failed attempt must still show its own error.
        await tester.enterText(
          find.byType(TextFormField).at(1),
          'another-wrong-password',
        );
        await tester.ensureVisible(find.byType(FilledButton));
        await tester.tap(find.byType(FilledButton));
        await tester.pumpAndSettle();
        expect(find.text(rejected), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('prevents duplicate submit and shows a safe error', (
    tester,
  ) async {
    final pending = Completer<void>();
    var attempts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: LoginScreen(
          onSubmit: (username, password) {
            attempts++;
            expect(username, ' customer ');
            expect(password, ' unchanged ');
            return pending.future;
          },
        ),
      ),
    );
    await tester.enterText(find.byType(TextFormField).at(0), ' customer ');
    await tester.enterText(find.byType(TextFormField).at(1), ' unchanged ');
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pump();
    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNull,
    );
    expect(attempts, 1);
    pending.completeError(
      const AuthFormException('Thông tin đăng nhập không hợp lệ.'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Thông tin đăng nhập không hợp lệ.'), findsOneWidget);
  });

  testWidgets('registration rejects mismatched passwords', (tester) async {
    var submitted = false;
    await tester.pumpWidget(
      MaterialApp(
        home: RegisterScreen(
          onSubmit: (_, _, _) async {
            submitted = true;
          },
        ),
      ),
    );
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Khách hàng');
    await tester.enterText(fields.at(1), 'customer');
    await tester.enterText(fields.at(2), 'password-one');
    await tester.enterText(fields.at(3), 'password-two');
    await tester.ensureVisible(find.byType(FilledButton));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Mật khẩu xác nhận chưa khớp.'), findsOneWidget);
    expect(submitted, isFalse);
  });

  testWidgets('supports narrow screens large text and keyboard', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(1.6),
            viewInsets: EdgeInsets.only(bottom: 260),
          ),
          child: RegisterScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byType(FilledButton));
    expect(tester.takeException(), isNull);
  });
}
