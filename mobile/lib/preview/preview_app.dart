import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../features/doors/presentation/door_list_screen.dart';
import 'preview_door_repository.dart';

/// Separate debug entry point; the production authentication flow is unchanged.
class PreviewApp extends StatefulWidget {
  const PreviewApp({this.repository, super.key});

  final PreviewDoorRepository? repository;

  @override
  State<PreviewApp> createState() => _PreviewAppState();
}

class _PreviewAppState extends State<PreviewApp> {
  late PreviewDoorRepository _repository;
  GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();
  GlobalKey<NavigatorState> _featureNavigatorKey = GlobalKey<NavigatorState>();
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    if (!kDebugMode) {
      throw StateError('Preview chỉ được chạy ở chế độ debug.');
    }
    _repository = widget.repository ?? PreviewDoorRepository();
  }

  void _reset() {
    setState(() {
      _repository = PreviewDoorRepository();
      _navigatorKey = GlobalKey<NavigatorState>();
      _featureNavigatorKey = GlobalKey<NavigatorState>();
      _generation++;
    });
  }

  void _help() {
    final context = _navigatorKey.currentState?.overlay?.context;
    if (context == null) return;
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Thử giao diện với dữ liệu giả'),
        content: const SingleChildScrollView(
          child: Text(
            'Mọi thao tác chỉ thay đổi dữ liệu trong bộ nhớ của bản preview. '
            'Không gọi backend, không cần Docker, không ghi MySQL và không gửi lệnh tới ESP32.\n\n'
            '1. Nhấn Thêm cửa, nhập K7MP-4Q2D để nhận bộ cửa mẫu. '
            'Mã khác báo lỗi; nhập lại mã đúng trả về cùng cửa.\n\n'
            '2. Mở cửa để đổi tên/vị trí, xem trạng thái riêng từng Node và lịch sử.\n\n'
            '3. Bật Sẵn sàng để mô phỏng danh tính/cấu hình/PIN đã được xác minh, '
            'rồi thử kích hoạt Node của Cửa kho. Tắt để xem lỗi chưa sẵn sàng.\n\n'
            '4. Tại Cửa chính, thay Node 1 bằng Node đã đăng ký. '
            'Cần bật cả Sẵn sàng và Thu hồi; Thu hồi chỉ mô phỏng việc thu hồi quyền Node cũ.\n\n'
            '5. Đặt lại hoặc khởi động lại preview để khôi phục dữ liệu ban đầu.\n\n'
            'Kết quả preview dùng để đánh giá giao diện; kiểm thử API, JWT, '
            'database và MQTT cần chạy riêng.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Đã hiểu'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    key: ValueKey(_generation),
    navigatorKey: _navigatorKey,
    title: 'Smart Door · Preview',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
      useMaterial3: true,
    ),
    home: Builder(
      builder: (context) => Material(
        color: Theme.of(context).colorScheme.surface,
        child: Column(
          children: [
            ColoredBox(
              color: const Color(0xfffff3cd),
              child: SafeArea(
                bottom: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          const Icon(
                            Icons.science_outlined,
                            color: Color(0xff664d03),
                          ),
                          const SizedBox(width: 8),
                          const Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'PREVIEW · DỮ LIỆU GIẢ',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xff664d03),
                                  ),
                                ),
                                Text(
                                  'Không gọi API/MySQL · Mã K7MP-4Q2D',
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: Color(0xff664d03),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            key: const Key('previewReset'),
                            tooltip: 'Đặt lại dữ liệu preview',
                            onPressed: _reset,
                            icon: const Icon(Icons.restart_alt),
                          ),
                        ],
                      ),
                      Wrap(
                        spacing: 8,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          FilterChip(
                            key: const Key('previewReadiness'),
                            label: const Text('Sẵn sàng'),
                            tooltip:
                                'Mô phỏng danh tính và ACK cấu hình/PIN hợp lệ',
                            selected: _repository.readinessVerified,
                            onSelected: (value) => setState(
                              () => _repository.readinessVerified = value,
                            ),
                          ),
                          FilterChip(
                            key: const Key('previewRevocation'),
                            label: const Text('Thu hồi'),
                            tooltip:
                                'Mô phỏng đã thu hồi quyền MQTT của Node cũ',
                            selected: _repository.revocationVerified,
                            onSelected: (value) => setState(
                              () => _repository.revocationVerified = value,
                            ),
                          ),
                          TextButton(
                            key: const Key('previewHelp'),
                            onPressed: _help,
                            child: const Text('Hướng dẫn'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Expanded(
              child: MediaQuery.removePadding(
                context: context,
                removeTop: true,
                child: NavigatorPopHandler<void>(
                  onPopWithResult: (_) =>
                      _featureNavigatorKey.currentState?.pop(),
                  child: Navigator(
                    key: _featureNavigatorKey,
                    onGenerateRoute: (settings) => MaterialPageRoute<void>(
                      settings: settings,
                      builder: (_) => DoorListScreen(repository: _repository),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
