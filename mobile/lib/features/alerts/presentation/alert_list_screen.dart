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
  int _page = -1;
  bool _more = false;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool append = false}) async {
    final request = ++_request;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.repository.listAlerts(
        page: append ? _page + 1 : 0,
        doorId: widget.doorId,
        status: _status == 'ALL' ? null : _status,
      );
      if (mounted && request == _request) {
        setState(() {
          _items = append ? [..._items, ...result.items] : result.items;
          _page = result.page;
          _more = result.hasNext;
        });
      }
    } catch (error) {
      if (mounted && request == _request) setState(() => _error = error);
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _open(AlertView alert) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) =>
            AlertDetailScreen(repository: widget.repository, alert: alert),
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
          DropdownButtonFormField<String>(
            key: const Key('alertStatusFilter'),
            initialValue: _status,
            decoration: const InputDecoration(labelText: 'Trạng thái cảnh báo'),
            items: const [
              DropdownMenuItem(value: 'ALL', child: Text('Tất cả')),
              DropdownMenuItem(value: 'ACTIVE', child: Text('Mới')),
              DropdownMenuItem(value: 'ACKNOWLEDGED', child: Text('Đã xem')),
              DropdownMenuItem(value: 'RESOLVED', child: Text('Đã xử lý')),
            ],
            onChanged: _loading
                ? null
                : (value) {
                    setState(() => _status = value!);
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
            ..._items.map(
              (alert) => Card(
                child: ListTile(
                  onTap: () => _open(alert),
                  leading: CircleAvatar(
                    foregroundColor: alert.severity == 'CRITICAL'
                        ? Colors.red
                        : Colors.orange,
                    child: Icon(
                      alert.type == 'HIGH_TEMPERATURE'
                          ? Icons.local_fire_department
                          : Icons.warning_amber_rounded,
                    ),
                  ),
                  title: Text(alert.title),
                  subtitle: Text(
                    '${alert.doorName} · ${_statusLabel(alert.status)}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                ),
              ),
            ),
          if (_more && !_loading)
            OutlinedButton(
              onPressed: () => _load(append: true),
              child: const Text('Xem thêm cảnh báo'),
            ),
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
