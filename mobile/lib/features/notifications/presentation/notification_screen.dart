import 'package:flutter/material.dart';

import '../../alerts/data/alert_repository.dart';
import '../../alerts/presentation/alert_detail_screen.dart';
import '../../alerts/presentation/alert_list_screen.dart';
import '../../doors/presentation/door_widgets.dart';
import '../data/notification_repository.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({
    required this.repository,
    required this.alerts,
    required this.onSignInRequested,
    super.key,
  });
  final NotificationRepository repository;
  final AlertRepository alerts;
  final VoidCallback onSignInRequested;
  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  final List<OwnerNotification> _items = [];
  int _page = -1;
  bool _loading = true;
  bool _more = false;
  bool _unreadOnly = false;
  bool _opening = false;
  int _request = 0;
  Object? _error;
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
      final result = await widget.repository.list(
        page: append ? _page + 1 : 0,
        unread: _unreadOnly,
      );
      if (!mounted || request != _request) return;
      setState(() {
        if (!append) _items.clear();
        _items.addAll(result.items);
        _page = result.page;
        _more = result.hasNext;
      });
    } catch (error) {
      if (mounted && request == _request) setState(() => _error = error);
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _open(OwnerNotification item) async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      await widget.repository.markRead(item.id);
      final alert = await widget.alerts.getAlert(item.alertId);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) =>
              AlertDetailScreen(repository: widget.alerts, alert: alert),
        ),
      );
      if (mounted) await _load();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(errorMessage(error))));
      }
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  Future<void> _allAlerts() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => AlertListScreen(
          repository: widget.alerts,
          onSignInRequested: widget.onSignInRequested,
        ),
      ),
    );
    if (mounted) await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Thông báo'),
      actions: [
        IconButton(
          tooltip: 'Tất cả cảnh báo',
          onPressed: _allAlerts,
          icon: const Icon(Icons.list_alt),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Thông báo được lưu cho tài khoản của bạn. Mở cảnh báo để xem và xử lý; đánh dấu đã đọc thông báo không tự giải quyết cảnh báo.',
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Chỉ thông báo chưa đọc'),
            value: _unreadOnly,
            onChanged: _loading
                ? null
                : (value) {
                    setState(() => _unreadOnly = value);
                    _load();
                  },
          ),
          if (_loading) const Center(child: CircularProgressIndicator()),
          if (_error != null)
            FailurePanel(
              error: _error!,
              onRetry: _load,
              onSignInRequested: widget.onSignInRequested,
            ),
          if (!_loading && _error == null && _items.isEmpty)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Text('Chưa có thông báo trong bộ lọc này.'),
            ),
          for (final item in _items)
            Card(
              child: ListTile(
                enabled: !_opening,
                onTap: () => _open(item),
                leading: Icon(
                  item.unread
                      ? Icons.mark_email_unread_outlined
                      : Icons.drafts_outlined,
                ),
                title: Text(
                  item.title,
                  style: TextStyle(
                    fontWeight: item.unread
                        ? FontWeight.bold
                        : FontWeight.normal,
                  ),
                ),
                subtitle: Text('${item.body}\n${displayTime(item.createdAt)}'),
                isThreeLine: true,
                trailing: const Icon(Icons.chevron_right),
              ),
            ),
          if (_more && !_loading)
            OutlinedButton(
              onPressed: () => _load(append: true),
              child: const Text('Xem thêm thông báo'),
            ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _allAlerts,
            icon: const Icon(Icons.warning_amber),
            label: const Text('Xem tất cả cảnh báo'),
          ),
        ],
      ),
    ),
  );
}
