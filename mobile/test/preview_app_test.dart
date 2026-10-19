import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/preview/preview_app.dart';
import 'package:smart_door_mobile/preview/preview_door_repository.dart';

void main() {
  testWidgets('preview opens real feature screens and explains isolated data', (
    tester,
  ) async {
    _phoneSize(tester);
    await tester.pumpWidget(const PreviewApp());
    await tester.pumpAndSettle();

    expect(find.text('PREVIEW · DỮ LIỆU GIẢ'), findsOneWidget);
    expect(find.text('Cửa chính'), findsOneWidget);
    expect(find.text('Cửa kho'), findsOneWidget);
    expect(find.text('Vui lòng đăng nhập để quản lý cửa.'), findsNothing);

    await tester.tap(find.text('Cửa chính'));
    await tester.pumpAndSettle();
    expect(find.text('Thông tin cửa'), findsOneWidget);
    expect(find.text('PREVIEW · DỮ LIỆU GIẢ'), findsOneWidget);

    await tester.tap(find.byKey(const Key('previewHelp')));
    await tester.pumpAndSettle();
    expect(find.text('Thử giao diện với dữ liệu giả'), findsOneWidget);
    await tester.tap(find.text('Đã hiểu'));
    await tester.pumpAndSettle();
    expect(find.text('Thông tin cửa'), findsOneWidget);
  });

  testWidgets(
    'preview can claim sample door without creating an HTTP session',
    (tester) async {
      _phoneSize(tester);
      final repository = PreviewDoorRepository();
      await tester.pumpWidget(PreviewApp(repository: repository));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Thêm cửa'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('activationCode')),
        PreviewDoorRepository.demoActivationCode,
      );
      await tester.tap(find.text('Xác nhận thêm cửa'));
      await tester.pumpAndSettle();

      expect(find.text('Thông tin cửa'), findsOneWidget);
      expect((await repository.listDoors()).totalElements, 3);
      final claimed = await repository.claimDoor(
        PreviewDoorRepository.demoActivationCode,
      );
      expect(claimed.status, 'SETUP_PENDING');
      expect((await repository.listDoors()).totalElements, 3);
    },
  );

  testWidgets(
    'readiness switch tests denial then activation; reset restores home',
    (tester) async {
      _phoneSize(tester);
      final repository = PreviewDoorRepository();
      final node = await repository.getDevice(2, 21);
      await tester.pumpWidget(PreviewApp(repository: repository));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cửa kho'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text(node.deviceCode));
      await tester.tap(find.text(node.deviceCode));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Kích hoạt thiết bị'));
      await tester.tap(find.text('Kích hoạt thiết bị'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận'));
      await tester.pumpAndSettle();
      expect(
        (await repository.getDevice(2, 21)).lifecycleStatus,
        'PROVISIONED',
      );
      expect(find.text('Hủy'), findsOneWidget);

      await tester.tap(find.text('Hủy'));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('previewReadiness')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Kích hoạt thiết bị'));
      await tester.tap(find.text('Kích hoạt thiết bị'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận'));
      await tester.pumpAndSettle();
      expect((await repository.getDevice(2, 21)).lifecycleStatus, 'ACTIVE');

      await tester.tap(find.byKey(const Key('previewReset')));
      await tester.pumpAndSettle();
      expect(find.text('Cửa của bạn'), findsOneWidget);
      expect(find.text('Chi tiết thiết bị'), findsNothing);
      expect(
        tester
            .widget<FilterChip>(find.byKey(const Key('previewReadiness')))
            .selected,
        isFalse,
      );
      expect(
        tester
            .widget<FilterChip>(find.byKey(const Key('previewRevocation')))
            .selected,
        isFalse,
      );
    },
  );

  testWidgets('Android back returns from a feature to the preview list', (
    tester,
  ) async {
    _phoneSize(tester);
    await tester.pumpWidget(const PreviewApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cửa chính'));
    await tester.pumpAndSettle();
    expect(find.text('Thông tin cửa'), findsOneWidget);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Cửa của bạn'), findsOneWidget);
    expect(find.text('Thông tin cửa'), findsNothing);
    expect(find.text('PREVIEW · DỮ LIỆU GIẢ'), findsOneWidget);
  });

  testWidgets('preview controls fit a narrow phone', (tester) async {
    _phoneSize(tester, width: 320, height: 640);
    await tester.pumpWidget(const PreviewApp());
    await tester.pumpAndSettle();
    expect(find.text('Thêm cửa'), findsOneWidget);
    expect(find.byKey(const Key('previewHelp')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

void _phoneSize(
  WidgetTester tester, {
  double width = 400,
  double height = 1000,
}) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}
