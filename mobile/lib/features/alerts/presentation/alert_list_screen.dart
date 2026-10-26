import 'package:flutter/material.dart';

import '../../doors/presentation/door_widgets.dart';
import '../data/alert_models.dart';
import '../data/alert_repository.dart';
import 'alert_detail_screen.dart';

class AlertListScreen extends StatefulWidget {
  const AlertListScreen({
    super.key,
    required this.repository,
    required this.onSignInRequested,
    this.doorId,
  });
  final AlertRepository repository;
  final VoidCallback onSignInRequested;
  final int? doorId;

  @override
  State<AlertListScreen> createState() => _AlertListScreenState();
}

class _AlertListScreenState extends State<AlertListScreen> {
  List<AlertView> _items = [];
  Object? _error;
  bool _loading = true;
  String _status = 'ALL';

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
      final items = <AlertView>[];
      var page = 0;
      while (true) {
        final result = await widget.repository.listAlerts(
          page: page++,
          doorId: widget.doorId,
          status: _status == 'ALL' ? null : _status,
        );
        items.addAll(result.items);
        if (!result.hasNext) break;
      }
      if (mounted) setState(() => _items = items);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _open(AlertView alert) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => AlertDetailScreen(
          repository: widget.repository,
          alert: alert,
        ),
      ),
    );
    if (mounted) await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Cảnh báo')),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'ALL', label: Text('Tất cả')),
              ButtonSegment(value: 'ACTIVE', label: Text('Mới')),
              ButtonSegment(value: 'RESOLVED', label: Text('Đã xử lý')),
            ],
            selected: {_status},
            onSelectionChanged: (values) {
              setState(() => _status = values.first);
              _load();
            },
          ),
          const SizedBox(height: 16),
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else if (_error != null)
            FailurePanel(
              error: _error!,
              onRetry: _load,
              onSignInRequested: widget.onSignInRequested,
            )
          else if (_items.isEmpty)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  children: [
                    Icon(Icons.notifications_off_outlined, size: 44),
                    SizedBox(height: 12),
                    Text('Không có cảnh báo trong bộ lọc này.'),
                  ],
                ),
              ),
            )
          else
            ..._items.map((alert) => Card(
              child: ListTile(
                onTap: () => _open(alert),
                leading: CircleAvatar(
                  foregroundColor: alert.severity == 'CRITICAL' ? Colors.red : Colors.orange,
                  child: Icon(alert.type == 'HIGH_TEMPERATURE'
                      ? Icons.local_fire_department : Icons.warning_amber_rounded),
                ),
                title: Text(alert.title),
                subtitle: Text('${alert.doorName} · ${_statusLabel(alert.status)}'),
                trailing: const Icon(Icons.chevron_right),
              ),
            )),
        ],
      ),
    ),
  );

  String _statusLabel(String value) => switch (value) {
    'ACTIVE' => 'Mới',
    'ACKNOWLEDGED' => 'Đã xem',
    'RESOLVED' => 'Đã giải quyết',
    _ => value,
  };
}
