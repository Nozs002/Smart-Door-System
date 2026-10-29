import 'package:flutter/material.dart';

import '../../doors/presentation/door_widgets.dart';
import '../data/settings_models.dart';
import '../data/settings_repository.dart';

class DoorSettingsScreen extends StatefulWidget {
  const DoorSettingsScreen({
    super.key,
    required this.repository,
    required this.doorId,
    required this.doorName,
    required this.onSignInRequested,
  });

  final SettingsRepository repository;
  final int doorId;
  final String doorName;
  final VoidCallback onSignInRequested;

  @override
  State<DoorSettingsScreen> createState() => _DoorSettingsScreenState();
}

class _DoorSettingsScreenState extends State<DoorSettingsScreen> {
  final _formKey = GlobalKey<FormState>();
  final _attempts = TextEditingController();
  final _lockout = TextEditingController();
  final _temperature = TextEditingController();
  final _openTimeout = TextEditingController();
  DoorConfiguration? _configuration;
  Object? _error;
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _attempts.dispose();
    _lockout.dispose();
    _temperature.dispose();
    _openTimeout.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (_saving) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final value = await widget.repository.getConfiguration(widget.doorId);
      if (!mounted) return;
      _setValue(value);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _setValue(DoorConfiguration value) {
    setState(() => _configuration = value);
    _attempts.text = '${value.maxFailedAttempts}';
    _lockout.text = '${value.lockoutSeconds}';
    _temperature.text = value.temperatureThreshold.toStringAsFixed(1);
    _openTimeout.text = value.openTimeoutSeconds?.toString() ?? '';
  }

  String? _integer(String? raw, int min, int max, String unit) {
    final value = int.tryParse(raw ?? '');
    if (value == null || value < min || value > max) {
      return 'Nhập từ $min đến $max $unit.';
    }
    return null;
  }

  Future<void> _save() async {
    if (_saving || !_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final value = await widget.repository.updateConfiguration(
        widget.doorId,
        maxFailedAttempts: int.parse(_attempts.text),
        lockoutSeconds: int.parse(_lockout.text),
        temperatureThreshold: double.parse(_temperature.text),
        openTimeoutSeconds: _openTimeout.text.trim().isEmpty
            ? null
            : int.parse(_openTimeout.text),
      );
      if (!mounted) return;
      _setValue(value);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Đã lưu và đưa vào hàng đợi đồng bộ. Chỉ hoàn tất khi thiết bị xác nhận.',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Không thể lưu: $error')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _retrySync() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      final value = await widget.repository.retrySync(widget.doorId);
      if (!mounted) return;
      _setValue(value);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Đã yêu cầu đồng bộ lại phiên bản hiện tại. Đang chờ xác nhận từ thiết bị.',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(errorMessage(error))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('Cấu hình ${widget.doorName}')),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else if (_error != null)
            FailurePanel(
              error: _error!,
              onRetry: _load,
              onSignInRequested: widget.onSignInRequested,
            )
          else ...[
            Form(
              key: _formKey,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        'Ngưỡng theo cửa',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        enabled: !_saving,
                        controller: _attempts,
                        decoration: const InputDecoration(
                          labelText: 'Số lần sai tối đa',
                        ),
                        keyboardType: TextInputType.number,
                        validator: (value) => _integer(value, 1, 10, 'lần'),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        enabled: !_saving,
                        controller: _lockout,
                        decoration: const InputDecoration(
                          labelText: 'Thời gian khóa (giây)',
                        ),
                        keyboardType: TextInputType.number,
                        validator: (value) => _integer(value, 10, 3600, 'giây'),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        enabled: !_saving,
                        controller: _temperature,
                        decoration: const InputDecoration(
                          labelText: 'Ngưỡng nhiệt độ (°C)',
                        ),
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                        ),
                        validator: (raw) {
                          final value = double.tryParse(raw ?? '');
                          return value == null ||
                                  !value.isFinite ||
                                  value < 30 ||
                                  value > 80
                              ? 'Nhập nhiệt độ từ 30 đến 80°C.'
                              : null;
                        },
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        enabled: !_saving,
                        controller: _openTimeout,
                        decoration: const InputDecoration(
                          labelText: 'Cửa mở quá lâu (giây, không bắt buộc)',
                        ),
                        keyboardType: TextInputType.number,
                        validator: (raw) => raw == null || raw.trim().isEmpty
                            ? null
                            : _integer(raw, 5, 3600, 'giây'),
                      ),
                      const SizedBox(height: 20),
                      FilledButton.icon(
                        onPressed: _saving ? null : _save,
                        icon: _saving
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.send_outlined),
                        label: const Text('Lưu và gửi đến thiết bị'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            _syncCard(context),
          ],
        ],
      ),
    ),
  );

  Widget _syncCard(BuildContext context) {
    final value = _configuration!;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.sync),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Trạng thái đồng bộ',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                Text('Cấu hình v${value.version}'),
              ],
            ),
            const SizedBox(height: 12),
            ...value.devices.map(
              (device) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  device.configState == 'SYNCED'
                      ? Icons.check_circle
                      : Icons.pending_outlined,
                  color: device.configState == 'SYNCED'
                      ? Colors.green
                      : Colors.orange,
                ),
                title: Text(device.deviceCode),
                subtitle: Text(
                  '${device.nodeType} · ${device.lifecycleStatus}\nCấu hình: ${_state(device.configState)} '
                  '(đã nhận ${device.appliedConfigVersion ?? '—'} / cần ${value.version})'
                  '${device.credentialsState == 'NOT_APPLICABLE' ? '' : ' · PIN/RFID: ${_state(device.credentialsState)}'}',
                ),
              ),
            ),
            const Divider(),
            Text('Phiên bản PIN/RFID cần áp dụng: ${value.credentialsVersion}'),
            const SizedBox(height: 6),
            const Text(
              'Chưa xác nhận không có nghĩa đã áp dụng. Khi mất kết nối, yêu cầu được giữ để gửi lại.',
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _saving ? null : _retrySync,
              icon: const Icon(Icons.refresh),
              label: const Text('Yêu cầu đồng bộ lại'),
            ),
            const SizedBox(height: 6),
            Text(
              'Kéo xuống để cập nhật ACK mới nhất từ thiết bị.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }

  String _state(String value) => switch (value) {
    'SYNCED' => 'Đã áp dụng',
    'PENDING' => 'Đang chờ',
    'UNKNOWN' => 'Chưa xác nhận',
    _ => 'Không áp dụng',
  };
}
