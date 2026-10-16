import 'package:flutter/material.dart';

import '../../../core/api_client.dart';
import '../data/door_models.dart';
import '../data/door_repository.dart';
import 'claim_door_screen.dart';
import 'door_detail_screen.dart';
import 'door_widgets.dart';

class DoorListScreen extends StatefulWidget {
  const DoorListScreen({
    required this.repository,
    this.onSignInRequested,
    super.key,
  });

  final DoorRepository repository;
  final VoidCallback? onSignInRequested;

  @override
  State<DoorListScreen> createState() => _DoorListScreenState();
}

class _DoorListScreenState extends State<DoorListScreen> {
  final List<DoorView> _doors = [];
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
        _doors.clear();
      }
    });
    try {
      final result = await widget.repository.listDoors(
        page: more ? _page + 1 : 0,
      );
      if (!mounted) return;
      setState(() {
        _doors.addAll(result.items);
        _page = result.page;
        _hasNext = result.hasNext;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
          _loadingMore = false;
        });
      }
    }
  }

  Future<void> _openDoor(DoorView door) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => DoorDetailScreen(
          repository: widget.repository,
          doorId: door.id,
          onSignInRequested: widget.onSignInRequested,
        ),
      ),
    );
    if (mounted) await _load();
  }

  Future<void> _claim() async {
    final door = await Navigator.of(context).push<DoorView>(
      MaterialPageRoute(
        builder: (_) => ClaimDoorScreen(repository: widget.repository),
      ),
    );
    if (!mounted || door == null) return;
    await _openDoor(door);
  }

  @override
  Widget build(BuildContext context) {
    final needsLogin =
        _error is ApiException &&
        (_error as ApiException).authenticationRequired;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cửa của bạn'),
        actions: [
          IconButton(
            tooltip: 'Làm mới',
            onPressed: _loading || _loadingMore ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      floatingActionButton: needsLogin
          ? null
          : FloatingActionButton.extended(
              onPressed: _claim,
              icon: const Icon(Icons.add),
              label: const Text('Thêm cửa'),
            ),
      body: SafeArea(
        child: ContentWidth(
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null && (_doors.isEmpty || needsLogin)
              ? FailurePanel(
                  error: _error!,
                  onRetry: _load,
                  onSignInRequested: widget.onSignInRequested,
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 100),
                    children: [
                      if (_doors.isEmpty)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 64),
                          child: Column(
                            children: [
                              const Icon(
                                Icons.door_front_door_outlined,
                                size: 64,
                              ),
                              const SizedBox(height: 20),
                              Text(
                                'Bạn chưa có cửa nào',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'Chọn Thêm cửa và nhập mã kích hoạt\n'
                                'được bàn giao cùng bộ thiết bị.',
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ),
                        ),
                      for (final door in _doors)
                        Card(
                          child: ListTile(
                            contentPadding: const EdgeInsets.all(16),
                            leading: const Icon(Icons.door_front_door_outlined),
                            title: Text(door.name),
                            subtitle: Padding(
                              padding: const EdgeInsets.only(top: 8),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    door.location?.isNotEmpty == true
                                        ? door.location!
                                        : 'Chưa đặt vị trí',
                                  ),
                                  const SizedBox(height: 8),
                                  StatusPill(door.status),
                                ],
                              ),
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => _openDoor(door),
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
                            _loadingMore ? 'Đang tải…' : 'Xem thêm cửa',
                          ),
                        ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }
}
