import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import 'code_input.dart';

class TechnicianInventoryScreen extends StatefulWidget {
  const TechnicianInventoryScreen({super.key, required this.client});

  final ApiClient client;

  @override
  State<TechnicianInventoryScreen> createState() =>
      _TechnicianInventoryScreenState();
}

class _TechnicianInventoryScreenState extends State<TechnicianInventoryScreen> {
  List<Map<String, dynamic>> units = [];
  bool busy = false;
  String? error;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() => action(loadInventory);

  Future<void> loadInventory() async {
    final result = await widget.client.request(
      'GET',
      '/api/v1/technician/inventory',
    );
    if (mounted) units = (result as List).cast<Map<String, dynamic>>();
  }

  Future<void> register(String role) => action(() async {
    await widget.client.request(
      'POST',
      '/api/v1/technician/inventory',
      body: {'nodeType': role},
    );
    await loadInventory();
  });

  Future<void> action(Future<void> Function() work) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await work();
    } catch (failure) {
      if (mounted) setState(() => error = '$failure');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> factory(Map<String, dynamic> unit) async {
    await action(() async {
      final result = await widget.client.request(
        'GET',
        '/api/v1/technician/inventory/${unit['unitCode']}/factory',
      ) as Map<String, dynamic>;
      if (!mounted) return;
      final code = result['unitCode'] as String,
          setup = result['setupCode'] as String;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Chuẩn bị thiết bị tại xưởng'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Wi-Fi thiết lập: ${result['setupSsid']}'),
                const SizedBox(height: 8),
                const Text('Mã định danh bo'),
                SelectableText(code),
                CopyCodeButton(code: code, label: 'Sao chép mã bo'),
                const Text('Mã thiết lập Wi-Fi'),
                SelectableText(setup),
                CopyCodeButton(code: setup, label: 'Sao chép mã thiết lập'),
                const SizedBox(height: 12),
                const Text(
                  'Lưu cấu hình thành enrollment_config.local.h cạnh sketch đúng Node, rồi nạp firmware một lần.',
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () async {
                await Clipboard.setData(
                  ClipboardData(text: result['firmwareConfig'] as String),
                );
              },
              child: const Text('Sao chép cấu hình nạp'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Đóng'),
            ),
          ],
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Kho ESP32'),
      actions: [
        IconButton(
          tooltip: 'Làm mới kho',
          onPressed: busy ? null : refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ListView.builder(
      padding: const EdgeInsets.all(20),
      itemCount: units.length + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Wrap(
                spacing: 12,
                children: [
                  for (final role in ['NODE_1', 'NODE_2'])
                    OutlinedButton(
                      onPressed: busy ? null : () => register(role),
                      child: Text('Đăng ký $role'),
                    ),
                ],
              ),
              if (busy) const LinearProgressIndicator(),
              if (error != null)
                Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (!busy && error == null && units.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Text('Kho chưa có bo ESP32.'),
                ),
            ],
          );
        }
        final unit = units[index - 1];
        final status = unit['deviceId'] == null ? 'Chưa gắn cửa' : 'Đã gắn cửa';
        return ListTile(
          title: Text('${unit['nodeType']}'),
          subtitle: Text('${unit['unitCode']}\n$status'),
          trailing: const Icon(Icons.chevron_right),
          onTap: busy ? null : () => factory(unit),
        );
      },
    ),
  );
}
