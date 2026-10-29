import 'package:flutter/material.dart';

import '../data/door_models.dart';
import '../data/door_repository.dart';
import 'device_detail_screen.dart';
import 'door_widgets.dart';
import 'lifecycle_history_screen.dart';
import '../../credentials/data/credential_repository.dart';
import '../../credentials/presentation/cardholder_screen.dart';
import '../../credentials/presentation/rfid_screen.dart';
import '../../credentials/presentation/pin_screen.dart';
import '../../settings/data/settings_repository.dart';
import '../../settings/presentation/door_settings_screen.dart';
import '../../alerts/data/alert_repository.dart';
import '../../alerts/presentation/alert_list_screen.dart';

class DoorDetailScreen extends StatefulWidget {
  const DoorDetailScreen({
    required this.repository,
    required this.doorId,
    this.onSignInRequested,
    this.credentialRepository,
    this.settingsRepository,
    this.alertRepository,
    super.key,
  });

  final DoorRepository repository;
  final int doorId;
  final VoidCallback? onSignInRequested;
  final CredentialRepository? credentialRepository;
  final SettingsRepository? settingsRepository;
  final AlertRepository? alertRepository;

  @override
  State<DoorDetailScreen> createState() => _DoorDetailScreenState();
}

class _DoorDetailScreenState extends State<DoorDetailScreen> {
  DoorView? _door;
  List<DeviceView> _devices = [];
  bool _loading = true;
  Object? _error;

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
        widget.repository.getDoor(widget.doorId),
        widget.repository.listDevices(widget.doorId),
      ]);
      if (!mounted) return;
      setState(() {
        _door = result[0] as DoorView;
        _devices = result[1] as List<DeviceView>;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _edit() async {
    final updated = await showDialog<DoorView>(
      context: context,
      barrierDismissible: false,
      builder: (_) =>
          _EditDoorDialog(repository: widget.repository, door: _door!),
    );
    if (updated != null && mounted) setState(() => _door = updated);
  }

  Future<void> _openDevice(DeviceView device) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => DeviceDetailScreen(
          repository: widget.repository,
          doorId: widget.doorId,
          deviceId: device.id,
          onSignInRequested: widget.onSignInRequested,
        ),
      ),
    );
    if (mounted) await _load();
  }

  void _history() => Navigator.of(context).push<void>(
    MaterialPageRoute(
      builder: (_) => LifecycleHistoryScreen(
        repository: widget.repository,
        doorId: widget.doorId,
        devices: _devices,
        onSignInRequested: widget.onSignInRequested,
      ),
    ),
  );

  void _open(Widget screen) =>
      Navigator.of(context)
          .push<void>(MaterialPageRoute(builder: (_) => screen));

  void _signIn() => widget.onSignInRequested?.call();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(_door?.name ?? 'Chi tiết cửa'),
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
                  padding: const EdgeInsets.all(16),
                  children: [
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    'Thông tin cửa',
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge,
                                  ),
                                ),
                                IconButton(
                                  tooltip: 'Sửa tên và vị trí',
                                  onPressed: _edit,
                                  icon: const Icon(Icons.edit_outlined),
                                ),
                              ],
                            ),
                            DetailLine('Tên cửa', _door!.name),
                            DetailLine(
                              'Vị trí',
                              _door!.location?.isNotEmpty == true
                                  ? _door!.location!
                                  : 'Chưa đặt vị trí',
                            ),
                            const SizedBox(height: 8),
                            StatusPill(_door!.status),
                            if (_door!.status == 'SETUP_PENDING') ...[
                              const SizedBox(height: 12),
                              const Text(
                                'Cửa đã thuộc tài khoản của bạn. '
                                'Các Node cần đáp ứng điều kiện thiết lập '
                                'trước khi được kích hoạt.',
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    if (widget.credentialRepository != null ||
                        widget.settingsRepository != null ||
                        widget.alertRepository != null) ...[
                      Card(
                        child: Column(
                          children: [
                            if (widget.credentialRepository != null) ...[
                              ListTile(
                                leading: const Icon(Icons.people_outline),
                                title: const Text('Người giữ thẻ'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _open(
                                  CardholderScreen(
                                    repository: widget.credentialRepository!,
                                    onSignInRequested: _signIn,
                                  ),
                                ),
                              ),
                              ListTile(
                                leading: const Icon(Icons.credit_card),
                                title: const Text('Thẻ RFID của cửa'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _open(
                                  RfidScreen(
                                    repository: widget.credentialRepository!,
                                    doorId: widget.doorId,
                                    doorName: _door!.name,
                                    onSignInRequested: _signIn,
                                  ),
                                ),
                              ),
                              ListTile(
                                leading: const Icon(Icons.pin_outlined),
                                title: const Text('PIN của cửa'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _open(
                                  DoorPinScreen(
                                    repository: widget.credentialRepository!,
                                    doorId: widget.doorId,
                                    doorName: _door!.name,
                                    onSignInRequested: _signIn,
                                  ),
                                ),
                              ),
                            ],
                            if (widget.settingsRepository != null)
                              ListTile(
                                leading: const Icon(Icons.settings_outlined),
                                title: const Text('Cấu hình và đồng bộ'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _open(
                                  DoorSettingsScreen(
                                    repository: widget.settingsRepository!,
                                    doorId: widget.doorId,
                                    doorName: _door!.name,
                                    onSignInRequested: _signIn,
                                  ),
                                ),
                              ),
                            if (widget.alertRepository != null)
                              ListTile(
                                leading: const Icon(
                                  Icons.notifications_outlined,
                                ),
                                title: const Text('Cảnh báo của cửa'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _open(
                                  AlertListScreen(
                                    repository: widget.alertRepository!,
                                    doorId: widget.doorId,
                                    onSignInRequested: _signIn,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],
                    Text(
                      'Thiết bị của cửa',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Trạng thái kết nối được theo dõi riêng cho từng Node. '
                      'Thiết bị trực tuyến vẫn có thể đang bị vô hiệu hóa.',
                    ),
                    const SizedBox(height: 8),
                    for (final type in ['NODE_1', 'NODE_2']) ...[
                      Padding(
                        padding: const EdgeInsets.only(top: 12, bottom: 4),
                        child: Text(
                          nodeLabel(type),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                      if (!_devices.any((device) => device.nodeType == type))
                        const Padding(
                          padding: EdgeInsets.all(12),
                          child: Text('Chưa có thiết bị.'),
                        ),
                      for (final device in _devices.where(
                        (device) => device.nodeType == type,
                      ))
                        Card(
                          child: ListTile(
                            contentPadding: const EdgeInsets.all(16),
                            title: Text(device.deviceCode),
                            subtitle: Padding(
                              padding: const EdgeInsets.only(top: 8),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Wrap(
                                    spacing: 6,
                                    runSpacing: 6,
                                    children: [
                                      StatusPill(device.lifecycleStatus),
                                      StatusPill(device.connectionStatus),
                                    ],
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    'Heartbeat: ${displayTime(device.lastHeartbeatAt)}',
                                  ),
                                ],
                              ),
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => _openDevice(device),
                          ),
                        ),
                    ],
                    const SizedBox(height: 20),
                    OutlinedButton.icon(
                      onPressed: _history,
                      icon: const Icon(Icons.history),
                      label: const Text('Lịch sử vòng đời thiết bị'),
                    ),
                  ],
                ),
              ),
      ),
    ),
  );
}

class _EditDoorDialog extends StatefulWidget {
  const _EditDoorDialog({required this.repository, required this.door});

  final DoorRepository repository;
  final DoorView door;

  @override
  State<_EditDoorDialog> createState() => _EditDoorDialogState();
}

class _EditDoorDialogState extends State<_EditDoorDialog> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.door.name);
  late final _location = TextEditingController(
    text: widget.door.location ?? '',
  );
  bool _saving = false;
  Object? _error;

  @override
  void dispose() {
    _name.dispose();
    _location.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final result = await widget.repository.updateDoor(
        widget.door.id,
        _name.text,
        _location.text.trim().isEmpty ? null : _location.text,
      );
      if (mounted) Navigator.of(context).pop(result);
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
      title: const Text('Sửa thông tin cửa'),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                key: const Key('doorName'),
                controller: _name,
                enabled: !_saving,
                maxLength: 100,
                decoration: const InputDecoration(labelText: 'Tên cửa'),
                validator: (value) => value == null || value.trim().isEmpty
                    ? 'Vui lòng nhập tên cửa.'
                    : null,
              ),
              TextFormField(
                key: const Key('doorLocation'),
                controller: _location,
                enabled: !_saving,
                maxLength: 255,
                decoration: const InputDecoration(labelText: 'Vị trí'),
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
          onPressed: _saving ? null : _save,
          child: Text(_saving ? 'Đang lưu…' : 'Lưu'),
        ),
      ],
    ),
  );
}
