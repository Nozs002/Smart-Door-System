import 'package:flutter/material.dart';

import '../../../core/api_client.dart';
import '../../doors/presentation/door_widgets.dart';
import '../data/access_repository.dart';

class AccessLogScreen extends StatefulWidget {
  const AccessLogScreen({
    super.key,
    required this.repository,
    required this.doorId,
    required this.doorName,
    required this.onSignInRequested,
  });
  final AccessRepository repository;
  final int doorId;
  final String doorName;
  final VoidCallback onSignInRequested;
  @override
  State<AccessLogScreen> createState() => _AccessLogScreenState();
}

class _AccessLogScreenState extends State<AccessLogScreen> {
  List<AccessLogEntry> _items = [];
  String? _method, _result;
  Object? _error;
  bool _loading = false, _hasNext = false;
  int _page = 0, _generation = 0;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool next = false}) async {
    final generation = ++_generation;
    final page = next ? _page + 1 : 0;
    setState(() {
      _loading = true;
      _error = null;
      if (!next) _items = [];
    });
    try {
      final value = await widget.repository.logs(
        widget.doorId,
        page: page,
        method: _method,
        result: _result,
      );
      if (!mounted || generation != _generation) return;
      setState(() {
        _items = next ? [..._items, ...value.items] : value.items;
        _page = page;
        _hasNext = value.hasNext;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      if (error is ApiException && error.authenticationRequired) {
        widget.onSignInRequested();
        return;
      }
      setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Lịch sử ra vào')),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(widget.doorName, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text(
            'Xác thực hợp lệ và xác nhận thực thi được ghi riêng. Kết quả lệnh không thay thế cảm biến cửa.',
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _method ?? '',
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Phương thức'),
            items:
                [
                      '',
                      'RFID',
                      'PIN',
                      'APP',
                      'EXIT_BUTTON',
                      'AUTO_PROXIMITY',
                      'FIRE_EMERGENCY',
                    ]
                    .map(
                      (v) => DropdownMenuItem(
                        value: v,
                        child: Text(
                          v.isEmpty
                              ? 'Tất cả phương thức'
                              : accessMethodLabel(v),
                        ),
                      ),
                    )
                    .toList(),
            onChanged: _loading
                ? null
                : (v) {
                    _method = v == '' ? null : v;
                    _load();
                  },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _result ?? '',
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Kết quả'),
            items: ['', 'GRANTED', 'DENIED', 'EXECUTED', 'FAILED', 'UNKNOWN']
                .map(
                  (v) => DropdownMenuItem(
                    value: v,
                    child: Text(
                      v.isEmpty ? 'Tất cả kết quả' : accessResultLabel(v),
                    ),
                  ),
                )
                .toList(),
            onChanged: _loading
                ? null
                : (v) {
                    _result = v == '' ? null : v;
                    _load();
                  },
          ),
          const SizedBox(height: 16),
          if (_error != null) ...[
            Text(errorMessage(_error!)),
            TextButton(
              onPressed: () => _load(next: _items.isNotEmpty && _hasNext),
              child: const Text('Thử lại'),
            ),
          ],
          if (!_loading && _items.isEmpty && _error == null)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Text('Chưa có lịch sử phù hợp với bộ lọc.'),
            ),
          for (final item in _items)
            Card(
              child: ListTile(
                leading: Icon(
                  item.result == 'DENIED' || item.result == 'FAILED'
                      ? Icons.block
                      : Icons.history,
                ),
                title: Text(
                  '${accessMethodLabel(item.method)} · ${accessResultLabel(item.result)}',
                ),
                subtitle: Text(
                  [
                    if (item.cardholderName != null) item.cardholderName!,
                    accessReasonLabel(item.reason),
                    (item.occurredAt ?? item.receivedAt)
                            ?.toLocal()
                            .toString()
                            .split('.')
                            .first ??
                        '',
                  ].where((s) => s.isNotEmpty).join('\n'),
                ),
                isThreeLine: true,
              ),
            ),
          if (_loading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: CircularProgressIndicator(),
              ),
            ),
          if (!_loading && _hasNext)
            OutlinedButton(
              onPressed: () => _load(next: true),
              child: const Text('Tải thêm'),
            ),
        ],
      ),
    ),
  );
}
