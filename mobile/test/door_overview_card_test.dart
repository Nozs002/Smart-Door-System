import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/core/theme/app_theme.dart';
import 'package:smart_door_mobile/features/dashboard/door_overview_card.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';

void main() {
  testWidgets('overview does not infer physical lock or sensor state', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.theme(Brightness.light),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 398,
              child: DoorOverviewCard(
                door: const DoorView(
                  id: 1,
                  name: 'Cửa chính',
                  status: 'ACTIVE',
                ),
                onTap: () => opened = true,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getSize(find.byType(DoorOverviewCard)).height,
      closeTo(240, 1),
    );
    expect(find.text('Cửa chính'), findsOneWidget);
    expect(find.text('Lệnh gần nhất: Chưa có dữ liệu'), findsOneWidget);
    expect(find.text('Trạng thái vật lý chưa được xác nhận'), findsOneWidget);
    expect(find.text('—'), findsNWidgets(2));
    expect(find.text('Đang hoạt động'), findsNothing);
    expect(find.text('27°C'), findsNothing);
    expect(find.text('65%'), findsNothing);
    expect(find.byType(ShaderMask), findsOneWidget);
    await tester.tap(find.byType(DoorOverviewCard));
    expect(opened, isTrue);
  });
  testWidgets('overview grows for narrow screens and large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.theme(Brightness.dark),
        home: Scaffold(
          body: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(1.6)),
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: DoorOverviewCard(
                  door: const DoorView(
                    id: 1,
                    name: 'Cửa chính có tên dài để kiểm tra',
                    status: 'DISABLED',
                  ),
                  onTap: () {},
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(DoorOverviewCard)).height,
      greaterThan(240),
    );
  });
}
