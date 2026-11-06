import 'package:flutter/material.dart';

import '../data/alert_models.dart';
import '../data/alert_repository.dart';

class AlertDetailScreen extends StatefulWidget {
  const AlertDetailScreen({
    super.key,
    required this.repository,
    required this.alert,
  });
  final AlertRepository repository;
  final AlertView alert;

  @override
  State<AlertDetailScreen> createState() => _AlertDetailScreenState();
}

class _AlertDetailScreenState extends State<AlertDetailScreen> {
  late AlertView _alert = widget.alert;
  bool _working = false;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    if (_working || _loading) return;
    setState(() => _loading = true);
    try {
      final value = await widget.repository.getAlert(_alert.id);
      if (mounted) setState(() => _alert = value);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể tải trạng thái mới nhất: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _change(String status) async {
    if (_working || _loading) return;
    setState(() => _working = true);
    try {
      final value = await widget.repository.changeStatus(_alert.id, status);
      if (mounted) setState(() => _alert = value);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Không thể cập nhật: $error')));
      }
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Chi tiết cảnh báo'),
      actions: [
        IconButton(
          tooltip: 'Làm mới cảnh báo',
          onPressed: _loading || _working ? null : _refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_loading) const LinearProgressIndicator(),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Icon(
                  _alert.type == 'HIGH_TEMPERATURE'
                      ? Icons.local_fire_department
                      : Icons.warning_amber_rounded,
                  size: 52,
                  color: _alert.severity == 'CRITICAL'
                      ? Colors.red
                      : Colors.orange,
                ),
                const SizedBox(height: 12),
                Text(
                  _alert.title,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 20),
                _row('Cửa', _alert.doorName),
                _row('Mức độ', _alert.severity),
                _row('Trạng thái', _status(_alert.status)),
                if (_alert.observedTemperature != null)
                  _row('Nhiệt độ ghi nhận', '${_alert.observedTemperature}°C'),
                _row('Phát sinh', _time(_alert.triggeredAt)),
                if (_alert.resolvedAt != null)
                  _row('Giải quyết', _time(_alert.resolvedAt!)),
                if (_alert.sourceDeviceId != null)
                  _row('Thiết bị nguồn', '#${_alert.sourceDeviceId}'),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (_alert.status == 'ACTIVE')
          OutlinedButton.icon(
            onPressed: _working || _loading
                ? null
                : () => _change('ACKNOWLEDGED'),
            icon: const Icon(Icons.visibility_outlined),
            label: const Text('Đánh dấu đã xem'),
          ),
        if (_alert.status != 'RESOLVED') ...[
          const SizedBox(height: 10),
          FilledButton.icon(
            onPressed: _working || _loading ? null : () => _change('RESOLVED'),
            icon: const Icon(Icons.task_alt),
            label: const Text('Đánh dấu đã giải quyết'),
          ),
        ],
      ],
    ),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: Text(label)),
        const SizedBox(width: 16),
        Flexible(child: Text(value, textAlign: TextAlign.end)),
      ],
    ),
  );

  String _status(String value) => switch (value) {
    'ACTIVE' => 'Mới',
    'ACKNOWLEDGED' => 'Đã xem',
    'RESOLVED' => 'Đã giải quyết',
    _ => value,
  };

  String _time(DateTime value) {
    final local = value.toLocal();
    String two(int value) => value.toString().padLeft(2, '0');
    return '${two(local.hour)}:${two(local.minute)} ${two(local.day)}/${two(local.month)}/${local.year}';
  }
}
