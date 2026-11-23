import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import 'code_input.dart';
import 'door_setup_screen.dart';

String newReplacementRequestId() {
  final bytes = List.generate(16, (_) => Random.secure().nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

String replacementStatus(String status) => switch (status) {
  'OPEN' => 'Chờ kỹ thuật viên nhận mã',
  'BOUND' => 'Đã gắn bo mới, chờ thiết lập',
  'REVOKING' => 'Đang thu hồi kết nối bo cũ',
  'COMPLETED' => 'Đã thay thiết bị',
  'CANCELLED' => 'Đã hủy',
  'EXPIRED' => 'Mã đã hết hạn',
  _ => status,
};

class ReplacementScreen extends StatefulWidget {
  const ReplacementScreen({
    super.key,
    required this.client,
    required this.doorId,
    required this.deviceId,
  });
  final ApiClient client;
  final int doorId, deviceId;
  @override
  State<ReplacementScreen> createState() => _ReplacementScreenState();
}

class _ReplacementScreenState extends State<ReplacementScreen> {
  final reason = TextEditingController();
  Map<String, dynamic>? request;
  String? error, createId;
  bool busy = false;
  Timer? timer;
  DateTime? pollingDeadline;
  String get base => '/api/v1/doors/${widget.doorId}';
  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void dispose() {
    timer?.cancel();
    reason.dispose();
    super.dispose();
  }

  Future<void> perform(Future<void> Function() work) async {
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

  Future<void> refresh() => perform(() async {
    final value = await widget.client.request(
      'GET',
      '$base/devices/${widget.deviceId}/replacement-request',
    );
    if (mounted) setState(() => request = value as Map<String, dynamic>?);
  });

  Future<void> create() async {
    if (reason.text.trim().isEmpty) {
      setState(() => error = 'Nhập lý do thay thiết bị.');
      return;
    }
    createId ??= newReplacementRequestId();
    await perform(() async {
      final value = await widget.client.request(
        'POST',
        '$base/devices/${widget.deviceId}/replacement-requests',
        body: {'requestId': createId, 'reason': reason.text.trim()},
      );
      if (mounted) {
        setState(() {
          request = value as Map<String, dynamic>;
          createId = null;
        });
      }
    });
  }

  Future<void> complete({bool confirm = true}) async {
    if (busy || request == null) return;
    if (confirm && request!['status'] == 'BOUND') {
      final approved = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Xác nhận thay Node?'),
          content: const Text(
            'Khóa kết nối bo cũ sẽ bị thu hồi và không được khôi phục. Các thiết bị có thể kết nối lại trong ít giây. Chỉ thực hiện khi bo mới đã kết nối và đồng bộ.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Chưa thay'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Xác nhận thay'),
            ),
          ],
        ),
      );
      if (approved != true || !mounted) return;
    }
    await perform(() async {
      final value = await widget.client.request(
        'POST',
        '$base/replacement-requests/${request!['id']}/complete',
      );
      if (!mounted) return;
      setState(() => request = value as Map<String, dynamic>);
      if (request!['status'] == 'REVOKING') {
        pollingDeadline ??= DateTime.now().add(const Duration(minutes: 1));
        timer?.cancel();
        if (DateTime.now().isBefore(pollingDeadline!)) {
          timer = Timer(const Duration(seconds: 3), () {
            if (mounted) complete(confirm: false);
          });
        } else {
          setState(
            () => error = 'Chưa hoàn tất thu hồi hoặc đồng bộ. Kiểm tra thiết bị rồi bấm Tiếp tục hoàn tất.',
          );
          pollingDeadline = null;
        }
      } else {
        timer?.cancel();
        pollingDeadline = null;
      }
    });
  }

  Future<void> cancel() async {
    final approved = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Hủy yêu cầu thay Node?'),
        content: const Text(
          'Nếu đã gắn bo mới, danh tính bo mới sẽ bị thu hồi. Bo cũ chưa bị thay đổi bởi thao tác hủy này.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Hủy yêu cầu'),
          ),
        ],
      ),
    );
    if (approved != true || !mounted) return;
    await perform(() async {
      final value = await widget.client.request(
        'POST',
        '$base/replacement-requests/${request!['id']}/cancel',
      );
      if (mounted) setState(() => request = value as Map<String, dynamic>);
    });
  }

  @override
  Widget build(BuildContext context) {
    final status = request?['status'] as String?;
    final canCreate =
        request == null || ['CANCELLED', 'EXPIRED'].contains(status);
    final setup = request?['newNodeSetup'] as Map<String, dynamic>?;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thay Node của cửa'),
        actions: [
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
          const Text(
            'Giữ nguyên cửa, chủ sở hữu và lịch sử. Bạn cấp mã ủy quyền cho kỹ thuật viên gắn đúng một bo thay thế, sau đó tự xác nhận hoàn tất.',
          ),
          if (busy) const LinearProgressIndicator(),
          if (error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          if (canCreate) ...[
            const SizedBox(height: 16),
            TextField(
              controller: reason,
              enabled: !busy,
              maxLength: 500,
              decoration: const InputDecoration(
                labelText: 'Lý do thay thiết bị',
              ),
            ),
            FilledButton(
              onPressed: busy ? null : create,
              child: const Text('Cấp mã ủy quyền thay Node'),
            ),
          ],
          if (request != null) ...[
            const SizedBox(height: 20),
            Text(
              replacementStatus(status!),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Text('${request!['nodeType']} · ${request!['reason']}'),
            if (request!['authorizationCode'] is String) ...[
              const SizedBox(height: 12),
              SelectableText(request!['authorizationCode'] as String),
              Text('Hết hạn: ${request!['expiresAt']}'),
              CopyCodeButton(
                code: request!['authorizationCode'] as String,
                label: 'Sao chép mã ủy quyền',
              ),
            ],
            if (['BOUND', 'REVOKING'].contains(status)) ...[
              const SizedBox(height: 12),
              Text(
                'Bo mới: ${request!['newDeviceCode'] ?? request!['newDeviceId']}',
              ),
              Text(
                request!['ready'] == true
                    ? 'Bo mới đã xác nhận cấu hình/PIN hiện tại.'
                    : 'Đợi bo mới kết nối và xác nhận cấu hình/PIN hiện tại.',
              ),
              Text(
                request!['brokerRevoked'] == true
                    ? 'Đã xác minh thu hồi kết nối bo cũ.'
                    : 'Chưa xác minh thu hồi kết nối bo cũ.',
              ),
              if (setup != null)
                OutlinedButton.icon(
                  onPressed: busy
                      ? null
                      : () async {
                          await Navigator.push(
                            context,
                            MaterialPageRoute<void>(
                              builder: (_) => NodeWifiScreen(node: setup),
                            ),
                          );
                          if (mounted) await refresh();
                        },
                  icon: const Icon(Icons.wifi),
                  label: const Text('Cài Wi-Fi cho bo thay thế'),
                ),
              OutlinedButton.icon(
                onPressed: busy
                    ? null
                    : () => perform(() async {
                        await widget.client.request('POST', '$base/sync/retry');
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Đã yêu cầu đồng bộ cấu hình/PIN hiện tại. Đợi thiết bị xác nhận rồi làm mới.',
                              ),
                            ),
                          );
                        }
                      }),
                icon: const Icon(Icons.sync),
                label: const Text('Đồng bộ cấu hình/PIN hiện tại'),
              ),
              FilledButton(
                onPressed: busy ? null : () => complete(),
                child: Text(
                  status == 'REVOKING'
                      ? 'Tiếp tục hoàn tất'
                      : 'Xác nhận thay Node',
                ),
              ),
            ],
            if (['OPEN', 'BOUND'].contains(status))
              TextButton(
                onPressed: busy ? null : cancel,
                child: const Text('Hủy yêu cầu thay Node'),
              ),
            if (status == 'COMPLETED')
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  'Đã ngừng sử dụng bo cũ và kích hoạt bo mới. Quay lại chi tiết cửa để kiểm tra.',
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class TechnicianReplacementScreen extends StatefulWidget {
  const TechnicianReplacementScreen({super.key, required this.client});
  final ApiClient client;
  @override
  State<TechnicianReplacementScreen> createState() =>
      _TechnicianReplacementScreenState();
}

class _TechnicianReplacementScreenState
    extends State<TechnicianReplacementScreen> {
  final authorization = TextEditingController(), unit = TextEditingController();
  final form = GlobalKey<FormState>();
  List<dynamic> items = [];
  bool busy = false;
  String? error;
  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void dispose() {
    authorization.dispose();
    unit.dispose();
    super.dispose();
  }

  Future<void> refresh() async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final value = await widget.client.request(
        'GET',
        '/api/v1/technician/replacements',
      );
      if (mounted) setState(() => items = value as List);
    } catch (failure) {
      if (mounted) setState(() => error = '$failure');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> bind() async {
    if (busy || !form.currentState!.validate()) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await widget.client.request(
        'POST',
        '/api/v1/technician/replacements',
        body: {
          'authorizationCode': authorization.text.trim(),
          'unitCode': unit.text.trim(),
        },
      );
      if (!mounted) return;
      authorization.clear();
      unit.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Đã gắn bo thay thế. Chủ cửa tiếp tục cài Wi-Fi và xác nhận trên App.',
          ),
        ),
      );
    } catch (failure) {
      if (mounted) setState(() => error = '$failure');
    } finally {
      if (mounted) setState(() => busy = false);
    }
    if (mounted && error == null) await refresh();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Lắp Node thay thế'),
      actions: [
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
        const Text(
          'Chuẩn bị và nạp firmware cho bo chưa gắn trong Kho ESP32 trước. Nhận mã ủy quyền từ chủ cửa để gắn đúng vai trò; không cần tạo cửa hoặc claim lại.',
        ),
        if (busy) const LinearProgressIndicator(),
        if (error != null)
          Text(
            error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        Form(
          key: form,
          child: Column(
            children: [
              CodeField(
                controller: authorization,
                label: 'Mã ủy quyền từ chủ cửa',
                enabled: !busy,
              ),
              CodeField(
                controller: unit,
                label: 'Mã bo ESP32 thay thế',
                enabled: !busy,
              ),
              FilledButton(
                onPressed: busy ? null : bind,
                child: const Text('Gắn bo vào cửa được ủy quyền'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'Các lần thay thiết bị',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        ...items.map((value) {
          final item = value as Map<String, dynamic>;
          return Card(
            child: ListTile(
              title: Text('${item['doorName'] ?? 'Cửa'} · ${item['nodeType']}'),
              subtitle: Text(
                '${replacementStatus(item['status'] as String)}\n${item['newDeviceCode'] ?? ''}',
              ),
            ),
          );
        }),
      ],
    ),
  );
}
