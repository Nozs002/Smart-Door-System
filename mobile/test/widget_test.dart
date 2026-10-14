import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/main.dart';
import 'package:smart_door_mobile/features/authentication/auth_widgets.dart';
import 'package:smart_door_mobile/features/authentication/login_screen.dart';
import 'package:smart_door_mobile/features/authentication/register_screen.dart';

void main() {
  testWidgets('opens registration and returns to login with glass cards', (
    tester,
  ) async {
    await tester.pumpWidget(const SmartDoorApp());
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
