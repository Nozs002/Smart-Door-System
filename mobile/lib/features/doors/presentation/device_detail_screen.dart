import 'package:flutter/material.dart';

import '../data/door_models.dart';
import '../data/door_repository.dart';
import 'door_widgets.dart';
import 'lifecycle_history_screen.dart';

class DeviceDetailScreen extends StatefulWidget {
  const DeviceDetailScreen({
    required this.repository,
    required this.doorId,
    required this.deviceId,
    this.onSignInRequested,
    super.key,
  });

  final DoorRepository repository;
  final int doorId;
  final int deviceId;
  final VoidCallback? onSignInRequested;

  @override
  State<DeviceDetailScreen> createState() => _DeviceDetailScreenState();
}

class _DeviceDetailScreenState extends State<DeviceDetailScreen> {
  DeviceView? _device;
  List<DeviceView> _devices = [];
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await Future.wait<Object>([
        widget.repository.getDevice(widget.doorId, widget.deviceId),
        widget.repository.listDevices(widget.doorId),
      ]);
      if (!mounted) return;
      setState(() {
        _device = result[0] as DeviceView;
        _devices = result[1] as List<DeviceView>;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _change(String action) async {
    final changed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _LifecycleDialog(
        repository: widget.repository,
        device: _device!,
        action: action,
        replacements: _devices
            .where(
              (candidate) =>
                  candidate.id != _device!.id &&
                  candidate.nodeType == _device!.nodeType &&
                  candidate.lifecycleStatus == 'PROVISIONED',
            )
            .toList(growable: false),
      ),
    );
    if (changed == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã cập nhật vòng đời thiết bị.')),
      );
      await _load();
    }
  }

  void _history() => Navigator.of(context).push<void>(
    MaterialPageRoute(
      builder: (_) => LifecycleHistoryScreen(
        repository: widget.repository,
        doorId: widget.doorId,
        devices: _devices,
        initialDeviceId: widget.deviceId,
        onSignInRequested: widget.onSignInRequested,
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Chi tiết thiết bị'),
      actions: [
        IconButton(
          tooltip: 'Làm mới',
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: SafeArea(
      child: ContentWidth(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? FailurePanel(
                error: _error!,
                onRetry: _load,
                onSignInRequested: widget.onSignInRequested,
              )
            : RefreshIndicator(
                onRefresh: _load,
                child: ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(24),
                  children: [
                    Text(
                      _device!.deviceCode,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(nodeLabel(_device!.nodeType)),
                    const SizedBox(height: 20),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        StatusPill(_device!.lifecycleStatus),
                        StatusPill(_device!.connectionStatus),
                      ],
                    ),
                    const SizedBox(height: 16),
                    DetailLine(
                      'Heartbeat gần nhất',
                      displayTime(_device!.lastHeartbeatAt),
                    ),
                    DetailLine(
                      'Firmware',
                      _device!.firmwareVersion ?? 'Chưa có',
                    ),
                    DetailLine(
                      'Phiên bản cấu hình đã áp dụng',
                      _device!.appliedConfigVersion?.toString() ??
                          'Chưa xác nhận',
                    ),
                    DetailLine(
                      'Phiên bản PIN đã áp dụng',
                      _device!.appliedPinVersion?.toString() ?? 'Chưa xác nhận',
                    ),
                    const Divider(height: 32),
                    Text(
                      'Vòng đời thiết bị',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 12),
                    if (_device!.lifecycleStatus == 'PROVISIONED')
                      FilledButton.icon(
                        onPressed: () => _change('ACTIVATE'),
                        icon: const Icon(Icons.power_settings_new),
                        label: const Text('Kích hoạt thiết bị'),
                      ),
                    if (_device!.lifecycleStatus == 'ACTIVE')
                      OutlinedButton.icon(
                        onPressed: () => _change('DISABLE'),
                        icon: const Icon(Icons.pause_circle_outline),
                        label: const Text('Vô hiệu hóa thiết bị'),
                      ),
                    if (_device!.lifecycleStatus == 'DISABLED')
                      FilledButton.icon(
                        onPressed: () => _change('REACTIVATE'),
                        icon: const Icon(Icons.restart_alt),
                        label: const Text('Kích hoạt lại thiết bị'),
                      ),
                    if (_device!.lifecycleStatus == 'ACTIVE' ||
                        _device!.lifecycleStatus == 'DISABLED')
                      OutlinedButton.icon(
                        onPressed: () => _change('REPLACE'),
                        icon: const Icon(Icons.swap_horiz),
                        label: const Text('Thay thế thiết bị'),
                      ),
                    if (_device!.lifecycleStatus == 'ACTIVE' ||
                        _device!.lifecycleStatus == 'DISABLED')
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Theme.of(context).colorScheme.error,
                        ),
                        onPressed: () => _change('RETIRE'),
                        icon: const Icon(Icons.archive_outlined),
                        label: const Text('Ngừng sử dụng thiết bị'),
                      ),
                    if (_device!.lifecycleStatus == 'RETIRED')
                      const Text(
                        'Thiết bị đã ngừng sử dụng và không thể kích hoạt lại. '
                        'Thông tin và lịch sử vẫn được lưu giữ.',
                      ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: _history,
                      icon: const Icon(Icons.history),
                      label: const Text('Xem lịch sử thiết bị'),
                    ),
                  ],
                ),
              ),
      ),
    ),
  );
}

class _LifecycleDialog extends StatefulWidget {
  const _LifecycleDialog({
    required this.repository,
    required this.device,
    required this.action,
    required this.replacements,
  });

  final DoorRepository repository;
  final DeviceView device;
  final String action;
  final List<DeviceView> replacements;

  @override
  State<_LifecycleDialog> createState() => _LifecycleDialogState();
}

class _LifecycleDialogState extends State<_LifecycleDialog> {
  final _form = GlobalKey<FormState>();
  final _reason = TextEditingController();
  int? _replacementId;
  bool _saving = false;
  Object? _error;

  bool get _requiresReason =>
      widget.action == 'RETIRE' || widget.action == 'REPLACE';

  bool get _showsReason => widget.action == 'DISABLE' || _requiresReason;

  String get _title => switch (widget.action) {
    'ACTIVATE' => 'Kích hoạt thiết bị',
    'DISABLE' => 'Vô hiệu hóa thiết bị',
    'REACTIVATE' => 'Kích hoạt lại thiết bị',
    'REPLACE' => 'Thay thế thiết bị',
    'RETIRE' => 'Ngừng sử dụng thiết bị',
    _ => widget.action,
  };

  String get _description => switch (widget.action) {
    'ACTIVATE' || 'REACTIVATE' =>
      'Hệ thống sẽ kiểm tra danh tính và trạng thái áp dụng cấu hình/PIN '
          'trước khi kích hoạt.',
    'DISABLE' => 'Thiết bị sẽ bị từ chối xử lý nghiệp vụ trong hệ thống.',
    'REPLACE' =>
      'Chọn Node cùng vai trò đã được kỹ thuật viên đăng ký. '
          'Hệ thống kiểm tra điều kiện sẵn sàng trước khi kích hoạt Node mới '
          'và ngừng sử dụng thiết bị cũ.',
    'RETIRE' =>
      'Thao tác này không thể khôi phục. Thiết bị sẽ ngừng sử dụng '
          'và lịch sử vẫn được giữ lại.',
    _ => '',
  };

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      if (widget.action == 'REPLACE') {
        await widget.repository.replaceDevice(
          widget.device.doorId,
          widget.device.id,
          _replacementId!,
          _reason.text,
        );
      } else {
        await widget.repository.changeLifecycle(
          widget.device.doorId,
          widget.device.id,
          widget.action,
          _showsReason ? _reason.text : '',
        );
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !_saving,
    child: AlertDialog(
      title: Text(_title),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_description),
              if (_showsReason) const SizedBox(height: 16),
              if (widget.action == 'REPLACE') ...[
                if (widget.replacements.isEmpty)
                  const Text(
                    'Chưa có Node thay thế phù hợp. '
                    'Liên hệ kỹ thuật viên để đăng ký thiết bị.',
                  )
                else
                  DropdownButtonFormField<int>(
                    key: const Key('replacementDevice'),
                    initialValue: _replacementId,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Node thay thế',
                    ),
                    items: [
                      for (final device in widget.replacements)
                        DropdownMenuItem(
                          value: device.id,
                          child: Text(device.deviceCode),
                        ),
                    ],
                    onChanged: _saving
                        ? null
                        : (value) => setState(() => _replacementId = value),
                    validator: (value) =>
                        value == null ? 'Vui lòng chọn Node thay thế.' : null,
                  ),
                const SizedBox(height: 12),
              ],
              if (_showsReason)
                TextFormField(
                  key: const Key('lifecycleReason'),
                  controller: _reason,
                  enabled: !_saving,
                  maxLength: 500,
                  minLines: 2,
                  maxLines: 4,
                  decoration: InputDecoration(
                    labelText: _requiresReason
                        ? 'Lý do'
                        : 'Lý do (không bắt buộc)',
                  ),
                  validator: (value) =>
                      _requiresReason && (value == null || value.trim().isEmpty)
                      ? 'Vui lòng nhập lý do.'
                      : null,
                ),
              if (_error != null)
                Text(
                  errorMessage(_error!),
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed:
              _saving ||
                  (widget.action == 'REPLACE' && widget.replacements.isEmpty)
              ? null
              : _submit,
          child: Text(_saving ? 'Đang xử lý…' : 'Xác nhận'),
        ),
      ],
    ),
  );
}
