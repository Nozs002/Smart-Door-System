import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_door_mobile/features/door_control/data/unlock_repository.dart';
import 'package:smart_door_mobile/features/door_control/presentation/unlock_sheet.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';

class FakeUnlockRepository implements UnlockRepository {
  int calls = 0;
  int closeCalls = 0;
  UnlockCommand? result;
  @override
  Future<UnlockCommand?> latest(int doorId) async => result;
  @override
  Future<UnlockCommand> unlock(int doorId, String requestId) async {
    calls++;
    return result = UnlockCommand(id: requestId, status: 'SENT');
  }

  @override
  Future<UnlockCommand> get(int doorId, String commandId) async => result!;
  @override
  Future<UnlockCommand> closeDoor(int doorId, String requestId) async {
    closeCalls++;
    return result = UnlockCommand(
      id: requestId,
      status: 'SENT',
      action: 'CLOSE',
    );
  }
}

void main() {
  testWidgets('Hold-open mode disables direct open and close controls', (
    tester,
  ) async {
    final repository = FakeUnlockRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: UnlockSheet(
            door: const DoorView(id: 1, name: 'Cửa chính', status: 'ACTIVE'),
            repository: repository,
            operatingMode: 'AUTOMATIC',
            onSettings: () {},
            onSignIn: () {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Đã chọn chế độ giữ mở'), findsOneWidget);
    expect(
      tester.widget<Slider>(find.byKey(const Key('unlockSlider'))).onChanged,
      isNull,
    );
    expect(
      tester
          .widget<OutlinedButton>(find.byKey(const Key('closeDoorButton')))
          .onPressed,
      isNull,
    );
    expect(repository.calls, 0);
    expect(repository.closeCalls, 0);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'Close requires confirmation and waits for the matching device result',
    (tester) async {
      final repository = FakeUnlockRepository();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: UnlockSheet(
              door: const DoorView(id: 1, name: 'Cửa chính', status: 'ACTIVE'),
              repository: repository,
              operatingMode: 'SECURITY',
              onSettings: () {},
              onSignIn: () {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final close = find.byKey(const Key('closeDoorButton'));
      await tester.ensureVisible(close);
      await tester.tap(close);
      await tester.pumpAndSettle();
      expect(repository.closeCalls, 0);
      await tester.tap(find.text('Hủy'));
      await tester.pumpAndSettle();
      expect(repository.closeCalls, 0);
      await tester.tap(close);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận đóng'));
      await tester.pump();
      await tester.pump();
      expect(repository.closeCalls, 1);
      expect(repository.calls, 0);
      expect(find.text('Đã gửi, chờ thiết bị xác nhận'), findsOneWidget);
      expect(tester.widget<OutlinedButton>(close).onPressed, isNull);
      repository.result = UnlockCommand(
        id: repository.result!.id,
        status: 'EXECUTED',
        action: 'CLOSE',
      );
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      expect(find.text('Thiết bị xác nhận lệnh đóng cửa'), findsOneWidget);
      expect(find.text('Thiết bị xác nhận lệnh mở khóa'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('Unlock panel supports narrow screens and large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(brightness: Brightness.light),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(360, 800),
            textScaler: TextScaler.linear(1.4),
          ),
          child: Scaffold(
            body: UnlockSheet(
              door: const DoorView(
                id: 1,
                name: 'Cửa chính tầng một',
                status: 'ACTIVE',
              ),
              repository: FakeUnlockRepository(),
              operatingMode: 'SECURITY',
              onSettings: () {},
              onSignIn: () {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('Partial swipe sends nothing; full swipe waits for device ACK', (
    tester,
  ) async {
    final repository = FakeUnlockRepository();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: UnlockSheet(
            door: const DoorView(id: 1, name: 'Cửa chính', status: 'ACTIVE'),
            repository: repository,
            operatingMode: 'SECURITY',
            onSettings: () {},
            onSignIn: () {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final sliderFinder = find.byKey(const Key('unlockSlider'));
    await tester.ensureVisible(sliderFinder);
    final origin = tester.getTopLeft(sliderFinder);
    final size = tester.getSize(sliderFinder);
    final start = origin + Offset(28, size.height / 2);
    await tester.tapAt(origin + Offset(size.width - 28, size.height / 2));
    await tester.pump();
    expect(repository.calls, 0);
    await tester.dragFrom(start, Offset((size.width - 56) * .4, 0));
    await tester.pumpAndSettle();
    expect(repository.calls, 0);
    await tester.dragFrom(start, Offset(size.width - 56, 0));
    await tester.pump();
    await tester.pump();
    expect(repository.calls, 1);
    expect(find.text('Đã gửi, chờ thiết bị xác nhận'), findsOneWidget);
    expect(
      tester.widget<Slider>(find.byKey(const Key('unlockSlider'))).onChanged,
      isNull,
    );
    repository.result = UnlockCommand(
      id: repository.result!.id,
      status: 'EXECUTED',
    );
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(find.text('Thiết bị xác nhận lệnh mở khóa'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
}
