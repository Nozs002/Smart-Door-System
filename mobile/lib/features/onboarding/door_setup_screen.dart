import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import '../doors/data/door_repository.dart';
import '../doors/data/door_models.dart';
import '../credentials/data/credential_repository.dart';
import '../credentials/presentation/pin_screen.dart';
import '../settings/data/settings_repository.dart';
import '../settings/presentation/door_settings_screen.dart';

class DoorSetupScreen extends StatefulWidget {
  const DoorSetupScreen({
    super.key,
    required this.client,
    required this.repository,
    required this.doorId,
    required this.doorName,
    required this.onSignInRequested,
    this.credentials,
    this.settings,
  });
  final ApiClient client;
  final DoorRepository repository;
  final int doorId;
  final String doorName;
  final VoidCallback onSignInRequested;
  final CredentialRepository? credentials;
  final SettingsRepository? settings;
  @override
  State<DoorSetupScreen> createState() => _DoorSetupScreenState();
}

class _DoorSetupScreenState extends State<DoorSetupScreen> {
  List<dynamic> nodes = [];
  List<DeviceView> devices = [];
  bool busy = false;
  String? message;
  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    if (busy) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      final results = await Future.wait<Object>([
        widget.client
            .request('GET', '/api/v1/doors/${widget.doorId}/setup-nodes')
            .then((v) => v as List),
        widget.repository.listDevices(widget.doorId),
      ]);
      if (mounted) {
        setState(() {
          nodes = results[0] as List;
          devices = results[1] as List<DeviceView>;
        });
      }
    } catch (error) {
      if (mounted) setState(() => message = '$error');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> open(Widget screen) async {
    await Navigator.push(
      context,
      MaterialPageRoute<void>(builder: (_) => screen),
    );
    if (mounted) await refresh();
  }

  Future<void> complete() async {
    if (busy) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      final result = await widget.client.request(
        'POST',
        '/api/v1/doors/${widget.doorId}/complete-setup',
      ) as Map<String, dynamic>;
      if (result['status'] == 'ACTIVE' && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Bộ cửa đã kích hoạt.')));
        Navigator.of(context).pop();
        return;
      }
      if (mounted) {
        setState(
          () => message = result['status'] == 'ACTIVE'
              ? 'Bộ cửa đã kích hoạt.'
              : 'Vui lòng cập nhật trạng thái cửa.',
        );
      }
    } catch (error) {
      if (mounted) {
        setState(() => message = '$error');
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Thiết lập bộ cửa'),
      actions: [
        IconButton(
          onPressed: busy ? null : refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Text(
          'Cài Wi-Fi lần lượt cho hai Node, sau đó đặt PIN và gửi cấu hình. Giữ nút BOOT trên Node 3 giây để mở lại chế độ cài đặt.',
        ),
        if (busy) const LinearProgressIndicator(),
        if (message != null)
          Padding(padding: const EdgeInsets.all(12), child: Text(message!)),
        ...nodes.map((value) {
          final node = value as Map<String, dynamic>;
          return Card(
            child: ListTile(
              title: Text('Cài Wi-Fi ${node['nodeType']}'),
              subtitle: Text('${node['unitCode']}\n${node['setupSsid']}'),
              trailing: const Icon(Icons.wifi),
              onTap: busy ? null : () => open(NodeWifiScreen(node: node)),
            ),
          );
        }),
        if (nodes.isEmpty && !busy)
          const Text(
            'Bộ cửa cũ chưa có tem thiết lập mới. Có thể tiếp tục dùng cấu hình firmware hiện tại.',
          ),
        if (widget.credentials != null)
          ListTile(
            leading: const Icon(Icons.pin),
            title: const Text('Đặt PIN cửa'),
            onTap: () => open(
              DoorPinScreen(
                repository: widget.credentials!,
                doorId: widget.doorId,
                doorName: widget.doorName,
                onSignInRequested: widget.onSignInRequested,
              ),
            ),
          ),
        if (widget.settings != null)
          ListTile(
            leading: const Icon(Icons.sync),
            title: const Text('Gửi cấu hình và kiểm tra đồng bộ'),
            onTap: () => open(
              DoorSettingsScreen(
                repository: widget.settings!,
                doorId: widget.doorId,
                doorName: widget.doorName,
                onSignInRequested: widget.onSignInRequested,
              ),
            ),
          ),
        ...devices.map(
          (device) => ListTile(
            title: Text(device.deviceCode),
            subtitle: Text(
              '${device.lifecycleStatus} · ${device.connectionStatus}',
            ),
          ),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: busy ? null : complete,
          child: const Text('Hoàn tất và kích hoạt bộ cửa'),
        ),
        TextButton(
          onPressed: busy ? null : refresh,
          child: const Text('Đã về mạng nhà, cập nhật trạng thái'),
        ),
      ],
    ),
  );
}

class NodeWifiScreen extends StatefulWidget {
  const NodeWifiScreen({super.key, required this.node});
  final Map<String, dynamic> node;
  @override
  State<NodeWifiScreen> createState() => _NodeWifiScreenState();
}

class _NodeWifiScreenState extends State<NodeWifiScreen> {
  final ssid = TextEditingController(),
      password = TextEditingController(),
      setup = TextEditingController();
  final form = GlobalKey<FormState>();
  bool busy = false;
  String? message;
  List<String> networks = [];
  @override
  void initState() {
    super.initState();
    setup.text = widget.node['setupCode'] as String;
  }

  @override
  void dispose() {
    ssid.dispose();
    password.dispose();
    setup.dispose();
    super.dispose();
  }

  Future<Map<String, dynamic>> local(
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final response = await const MethodChannel('smartdoor/network')
        .invokeMapMethod<String, dynamic>('requestSetupNode', {
          'path': path,
          'setupCode': setup.text.trim(),
          'body': body,
        })
        .timeout(const Duration(seconds: 20));
    final status = response?['status'];
    if (status is! int || status < 200 || status >= 300) {
      throw const FormatException(
        'Node từ chối. Kiểm tra mã thiết lập và Wi-Fi.',
      );
    }
    return jsonDecode(response!['body'] as String) as Map<String, dynamic>;
  }

  Future<void> perform(Future<void> Function() work) async {
    if (busy) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      final identity = await local('/api/info');
      if (identity['unitCode'] != widget.node['unitCode'] ||
          identity['nodeType'] != widget.node['nodeType']) {
        final receivedCode = identity['unitCode'];
        final receivedType = identity['nodeType'];
        final received = receivedCode is String && receivedType is String
            ? '$receivedType · $receivedCode'
            : 'Node không trả đủ thông tin';
        throw FormatException(
          'Cần kết nối ${widget.node['setupSsid']} (${widget.node['nodeType']}). '
          'Node trả về: $received.',
        );
      }
      await work();
    } catch (error) {
      if (mounted) {
        setState(
          () => message = error is FormatException ? error.message : 'Chưa kết nối được Node. Mở Wi-Fi và chọn mạng trên tem thiết bị.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Cài Wi-Fi ESP32')),
    body: Form(
      key: form,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          SelectableText(
            'Kết nối Wi-Fi ${widget.node['setupSsid']}\nMật khẩu mạng thiết lập ${widget.node['setupCode']}',
          ),
          TextButton.icon(
            onPressed: () async {
              try {
                await const MethodChannel('smartdoor/network')
                    .invokeMethod<void>('openWifiSettings');
              } on PlatformException {
                if (mounted) {
                  setState(
                    () => message =
                        'Mở Cài đặt Wi-Fi của điện thoại để kết nối Node.',
                  );
                }
              }
            },
            icon: const Icon(Icons.settings),
            label: const Text('Mở cài đặt Wi-Fi điện thoại'),
          ),
          const Text(
            'Khi điện thoại báo mạng không có Internet, chọn tiếp tục kết nối. Sau đó quay lại App.',
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: setup,
            enabled: !busy,
            autocorrect: false,
            enableSuggestions: false,
            decoration: const InputDecoration(
              labelText: 'Mã thiết lập của Node',
              border: OutlineInputBorder(),
            ),
            validator: (v) => v == null || v.trim().length < 12
                ? 'Kiểm tra mã thiết lập.'
                : null,
          ),
          TextFormField(
            controller: ssid,
            enabled: !busy,
            maxLength: 32,
            decoration: const InputDecoration(
              labelText: 'Tên Wi-Fi nhà (2.4 GHz)',
            ),
            validator: (v) =>
                v == null || v.isEmpty || utf8.encode(v).length > 32
                ? 'Nhập tên Wi-Fi hợp lệ.'
                : null,
          ),
          if (networks.isNotEmpty)
            Wrap(
              spacing: 8,
              children: networks
                  .map(
                    (name) => ActionChip(
                      label: Text(name),
                      onPressed: () => ssid.text = name,
                    ),
                  )
                  .toList(),
            ),
          TextButton(
            onPressed: busy
                ? null
                : () => perform(() async {
                    final result = await local('/api/networks');
                    if (mounted) {
                      setState(() {
                        networks = (result['networks'] as List)
                            .cast<String>()
                            .toSet()
                            .toList();
                        message = networks.isEmpty
                            ? 'Node đang quét. Bấm tìm mạng lại sau vài giây hoặc nhập tên Wi-Fi.'
                            : null;
                      });
                    }
                  }),
            child: const Text('Tìm mạng Wi-Fi gần Node'),
          ),
          TextFormField(
            controller: password,
            enabled: !busy,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Mật khẩu Wi-Fi nhà'),
            validator: (v) =>
                v != null &&
                    v.isNotEmpty &&
                    (utf8.encode(v).length < 8 || utf8.encode(v).length > 63)
                ? 'Mật khẩu Wi-Fi cần 8–63 byte.'
                : null,
          ),
          if (busy) const LinearProgressIndicator(),
          if (message != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(message!),
            ),
          FilledButton(
            onPressed: busy
                ? null
                : () {
                    if (!form.currentState!.validate()) return;
                    perform(() async {
                      await local(
                        '/api/wifi',
                        body: {'ssid': ssid.text, 'password': password.text},
                      );
                      password.clear();
                      if (mounted) {
                        setState(
                          () => message = 'Đã gửi Wi-Fi tới Node. Đưa điện thoại về mạng nhà, quay lại màn hình bộ cửa và cập nhật trạng thái.',
                        );
                      }
                    });
                  },
            child: const Text('Lưu Wi-Fi vào Node'),
          ),
        ],
      ),
    ),
  );
}
