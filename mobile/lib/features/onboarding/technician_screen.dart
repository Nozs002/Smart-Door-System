import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import 'code_input.dart';

class TechnicianScreen extends StatefulWidget {
  const TechnicianScreen({super.key, required this.client, this.onOpenProfile});
  final ApiClient client;
  final VoidCallback? onOpenProfile;
  @override
  State<TechnicianScreen> createState() => _TechnicianScreenState();
}

class _TechnicianScreenState extends State<TechnicianScreen> {
  List<dynamic> units = [], kits = [];
  bool busy = false;
  String? error;
  final name = TextEditingController(), location = TextEditingController();
  final first = TextEditingController(), second = TextEditingController();
  final form = GlobalKey<FormState>();
  String? requestId;
  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void dispose() {
    for (final c in [name, location, first, second]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> refresh() async {
    await action(() async {
      final values = await Future.wait([
        widget.client.request('GET', '/api/v1/technician/inventory'),
        widget.client.request('GET', '/api/v1/technician/kits'),
      ]);
      if (mounted) {
        setState(() {
          units = values[0] as List;
          kits = values[1] as List;
        });
      }
    });
  }

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

  String newRequestId() {
    final bytes = List.generate(16, (_) => Random.secure().nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<void> createKit() async {
    if (!form.currentState!.validate()) return;
    requestId ??= newRequestId();
    await action(() async {
      final result = await widget.client.request(
        'POST',
        '/api/v1/technician/kits',
        body: {
          'requestId': requestId,
          'name': name.text.trim(),
          'location': location.text.trim(),
          'node1Code': first.text.trim(),
          'node2Code': second.text.trim(),
        },
      ) as Map<String, dynamic>;
      if (!mounted) return;
      if (result['activationCode'] is String) {
        final code = result['activationCode'] as String;
        await showCode(
          context,
          'Mã nhận cửa',
          '$code\nHết hạn ${result['expiresAt']}',
          'smartdoor://claim/$code',
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Bộ cửa đã được tạo. Chọn Cấp lại mã bàn giao nếu cần.',
            ),
          ),
        );
      }
      requestId = null;
      kits =
          await widget.client.request('GET', '/api/v1/technician/kits') as List;
    });
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
                SelectableText(
                  'Node ${result['nodeType']}\nMã $code\nWi-Fi ${result['setupSsid']}\nMã thiết lập $setup',
                ),
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
              onPressed: () {
                Navigator.pop(context);
                showCode(
                  context,
                  'Tem thiết bị',
                  '$code\nMã thiết lập $setup',
                  'smartdoor://unit/$code?setup=$setup',
                );
              },
              child: const Text('Xem QR và mã'),
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
      title: const Text('Kỹ thuật viên'),
      actions: [
        if (widget.onOpenProfile != null)
          IconButton(
            tooltip: 'Hồ sơ tài khoản',
            onPressed: widget.onOpenProfile,
            icon: const Icon(Icons.person_outline),
          ),
        IconButton(
          tooltip: 'Làm mới',
          onPressed: busy ? null : refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        if (busy) const LinearProgressIndicator(),
        if (error != null)
          Text(
            error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Kho ESP32 và tem thiết bị',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 12,
                  children: ['NODE_1', 'NODE_2']
                      .map(
                        (role) => OutlinedButton(
                          onPressed: busy
                              ? null
                              : () => action(() async {
                                  await widget.client.request(
                                    'POST',
                                    '/api/v1/technician/inventory',
                                    body: {'nodeType': role},
                                  );
                                  units = await widget.client.request(
                                    'GET',
                                    '/api/v1/technician/inventory',
                                  ) as List;
                                }),
                          child: Text('Đăng ký $role'),
                        ),
                      )
                      .toList(),
                ),
                ...units.map((value) {
                  final unit = value as Map<String, dynamic>;
                  return ListTile(
                    title: Text('${unit['nodeType']}'),
                    subtitle: Text(
                      '${unit['unitCode']}\n${unit['deviceId'] == null ? 'Chưa gắn cửa' : 'Đã gắn cửa'}',
                    ),
                    trailing: const Icon(Icons.qr_code),
                    onTap: busy ? null : () => factory(unit),
                  );
                }),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        Text('Tạo bộ cửa', style: Theme.of(context).textTheme.titleLarge),
        Form(
          key: form,
          child: Column(
            children: [
              TextFormField(
                controller: name,
                maxLength: 100,
                decoration: const InputDecoration(labelText: 'Tên bộ cửa'),
                validator: (v) =>
                    v == null || v.trim().isEmpty ? 'Nhập tên cửa.' : null,
              ),
              TextFormField(
                controller: location,
                maxLength: 255,
                decoration: const InputDecoration(labelText: 'Vị trí'),
              ),
              CodeField(
                controller: first,
                label: 'Mã Node 1',
                kind: 'unit',
                enabled: !busy,
              ),
              CodeField(
                controller: second,
                label: 'Mã Node 2',
                kind: 'unit',
                enabled: !busy,
              ),
              FilledButton(
                onPressed: busy ? null : createKit,
                child: const Text('Tạo bộ cửa và mã bàn giao'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'Các bộ cửa đã tạo',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        ...kits.map((value) {
          final kit = value as Map<String, dynamic>;
          return Card(
            child: ListTile(
              title: Text('${kit['name']}'),
              subtitle: Text('Door ${kit['doorId']} · ${kit['status']}'),
              trailing: kit['status'] == 'UNCLAIMED'
                  ? TextButton(
                      onPressed: busy
                          ? null
                          : () => action(() async {
                              final result = await widget.client.request(
                                'POST',
                                '/api/v1/technician/kits/${kit['requestId']}/claim-code',
                              ) as Map<String, dynamic>;
                              final code = result['activationCode'] as String;
                              if (context.mounted) {
                                await showCode(
                                  context,
                                  'Mã nhận cửa mới',
                                  '$code\nHết hạn ${result['expiresAt']}',
                                  'smartdoor://claim/$code',
                                );
                              }
                            }),
                      child: const Text('Cấp lại mã'),
                    )
                  : null,
            ),
          );
        }),
      ],
    ),
  );
}
