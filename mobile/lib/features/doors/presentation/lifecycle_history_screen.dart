import 'package:flutter/material.dart';

import '../../../core/api_client.dart';
import '../data/door_models.dart';
import '../data/door_repository.dart';
import 'door_widgets.dart';

class LifecycleHistoryScreen extends StatefulWidget {
  const LifecycleHistoryScreen({
    required this.repository,
    required this.doorId,
    required this.devices,
    this.initialDeviceId,
    this.onSignInRequested,
    super.key,
  });

  final DoorRepository repository;
  final int doorId;
  final List<DeviceView> devices;
  final int? initialDeviceId;
  final VoidCallback? onSignInRequested;

  @override
  State<LifecycleHistoryScreen> createState() => _LifecycleHistoryScreenState();
}

class _LifecycleHistoryScreenState extends State<LifecycleHistoryScreen> {
  final List<LifecycleEvent> _events = [];
  late int? _deviceId = widget.initialDeviceId;
  bool _loading = true;
  bool _loadingMore = false;
  bool _hasNext = false;
  int _page = 0;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool more = false}) async {
    if (more && _loadingMore) return;
    setState(() {
      _error = null;
      if (more) {
        _loadingMore = true;
      } else {
        _loading = true;
        _events.clear();
      }
    });
    try {
      final page = await widget.repository.lifecycleEvents(
        widget.doorId,
        page: more ? _page + 1 : 0,
        deviceId: _deviceId,
      );
      if (!mounted) return;
      setState(() {
        _events.addAll(page.items);
        _page = page.page;
        _hasNext = page.hasNext;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
          _loadingMore = false;
        });
      }
    }
  }

  String _deviceCode(int id) =>
      widget.devices
          .where((device) => device.id == id)
          .map((device) => device.deviceCode)
          .firstOrNull ??
      'Thiết bị #$id';

  @override
  Widget build(BuildContext context) {
    final needsLogin =
        _error is ApiException &&
        (_error as ApiException).authenticationRequired;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Lịch sử vòng đời'),
        actions: [
          IconButton(
            tooltip: 'Làm mới',
            onPressed: _loading || _loadingMore ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: ContentWidth(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: DropdownButtonFormField<int>(
                  key: const Key('historyDeviceFilter'),
                  initialValue: _deviceId ?? 0,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Lọc theo thiết bị',
                    border: OutlineInputBorder(),
                  ),
                  items: [
                    const DropdownMenuItem(
                      value: 0,
                      child: Text('Tất cả thiết bị'),
                    ),
                    for (final device in widget.devices)
                      DropdownMenuItem(
                        value: device.id,
                        child: Text(device.deviceCode),
                      ),
                  ],
                  onChanged: _loading || _loadingMore
                      ? null
                      : (value) {
                          setState(() => _deviceId = value == 0 ? null : value);
                          _load();
                        },
                ),
              ),
              Expanded(
                child: _loading
                    ? const Center(child: CircularProgressIndicator())
                    : _error != null && (_events.isEmpty || needsLogin)
                    ? FailurePanel(
                        error: _error!,
                        onRetry: _load,
                        onSignInRequested: widget.onSignInRequested,
                      )
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                          children: [
                            if (_events.isEmpty)
                              const Padding(
                                padding: EdgeInsets.all(48),
                                child: Text(
                                  'Chưa có lịch sử vòng đời thiết bị.',
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            for (final event in _events)
                              Card(
                                child: Padding(
                                  padding: const EdgeInsets.all(16),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        statusLabel(event.eventType),
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleMedium,
                                      ),
                                      const SizedBox(height: 8),
                                      Text(_deviceCode(event.deviceId)),
                                      Text(displayTime(event.occurredAt)),
                                      const SizedBox(height: 8),
                                      Text(
                                        '${event.fromStatus == null ? 'Mới' : statusLabel(event.fromStatus!)}'
                                        ' → ${statusLabel(event.toStatus)}',
                                      ),
                                      if (event.reason?.isNotEmpty == true) ...[
                                        const SizedBox(height: 8),
                                        Text('Lý do: ${event.reason}'),
                                      ],
                                      if (event.replacementDeviceId != null)
                                        Text(
                                          'Thay bằng: ${_deviceCode(event.replacementDeviceId!)}',
                                        ),
                                      const SizedBox(height: 8),
                                      Text(
                                        event.actorAccountId == null
                                            ? 'Thực hiện: Kỹ thuật viên / hệ thống'
                                            : 'Thực hiện: Tài khoản #${event.actorAccountId}',
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall,
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            if (_error != null)
                              FailurePanel(
                                error: _error!,
                                onRetry: () => _load(more: true),
                              ),
                            if (_hasNext && _error == null)
                              TextButton(
                                onPressed: _loadingMore
                                    ? null
                                    : () => _load(more: true),
                                child: Text(
                                  _loadingMore
                                      ? 'Đang tải…'
                                      : 'Xem thêm lịch sử',
                                ),
                              ),
                          ],
                        ),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
