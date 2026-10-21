import 'package:flutter/material.dart';

import '../authentication/auth_session.dart';
import '../authentication/profile_screen.dart';
import '../doors/data/door_models.dart';
import '../doors/data/door_repository.dart';
import '../doors/presentation/door_detail_screen.dart';
import '../doors/presentation/door_list_screen.dart';
import '../doors/presentation/door_widgets.dart';
import '../doors/presentation/lifecycle_history_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({
    super.key,
    required this.session,
    required this.repository,
    required this.onToggleTheme,
  });
  final AuthSession session;
  final DoorRepository repository;
  final VoidCallback onToggleTheme;
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  List<DoorView> _doors = [];
  List<DeviceView> _devices = [];
  int? _selectedId;
  bool _loading = true;
  bool _loadingDevices = false;
  Object? _error;
  Object? _deviceError;
  int _request = 0;
  DoorView? get _selected =>
      _doors.where((d) => d.id == _selectedId).firstOrNull;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final request = ++_request;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final doors = <DoorView>[];
      var page = 0;
      while (true) {
        final result = await widget.repository.listDoors(page: page++);
        doors.addAll(result.items);
        if (!result.hasNext) break;
      }
      if (!mounted || request != _request) return;
      setState(() {
        _doors = doors;
        if (!doors.any((d) => d.id == _selectedId)) {
          _selectedId = doors.firstOrNull?.id;
        }
      });
      if (_selectedId != null) await _loadDevices(_selectedId!);
    } catch (error) {
      if (mounted && request == _request) setState(() => _error = error);
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _loadDevices(int id) async {
    setState(() {
      _loadingDevices = true;
      _deviceError = null;
      _devices = [];
    });
    try {
      final devices = await widget.repository.listDevices(id);
      if (mounted && _selectedId == id) setState(() => _devices = devices);
    } catch (error) {
      if (mounted && _selectedId == id) setState(() => _deviceError = error);
    } finally {
      if (mounted && _selectedId == id) setState(() => _loadingDevices = false);
    }
  }

  void _signIn() {
    Navigator.of(context).popUntil((r) => r.isFirst);
    widget.session.expire();
  }

  Future<void> _open(Widget screen) async {
    await Navigator.of(context)
        .push<void>(MaterialPageRoute(builder: (_) => screen));
    if (mounted && widget.session.signedIn) await _load();
  }

  Future<void> _manage() => _open(
    DoorListScreen(repository: widget.repository, onSignInRequested: _signIn),
  );
  Future<void> _detail() => _open(
    DoorDetailScreen(
      repository: widget.repository,
      doorId: _selectedId!,
      onSignInRequested: _signIn,
    ),
  );

  Widget _section(String title) => Padding(
    padding: const EdgeInsets.only(top: 26, bottom: 14),
    child: Text(
      title.toUpperCase(),
      style: const TextStyle(
        color: Colors.white,
        fontSize: 13,
        fontWeight: FontWeight.w700,
        letterSpacing: 1,
        shadows: [Shadow(blurRadius: 8, color: Colors.black54)],
      ),
    ),
  );
  Widget _panel(Widget child) => Card(
    child: Padding(padding: const EdgeInsets.all(20), child: child),
  );
  Widget _metric(String title) => Expanded(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 5),
        const Text(
          'Chưa có dữ liệu',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
      ],
    ),
  );
  Widget _unavailable(IconData icon, String title) => _panel(
    Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 25),
        const SizedBox(height: 22),
        Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        const SizedBox(height: 4),
        Text('Chưa khả dụng', style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
  );
  Widget _config(IconData icon, String title) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 10),
    child: Row(
      children: [
        Icon(icon),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
              Text(
                'Chưa có dữ liệu',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
        const Tooltip(
          message: 'Chưa có API cấu hình cửa',
          child: Icon(Icons.lock_outline, size: 18),
        ),
      ],
    ),
  );

  Widget _overview(DoorView door) => Card(
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: _detail,
      child: Column(
        children: [
          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 210),
            child: Stack(
              children: [
                Positioned.fill(
                  child: Image.asset(
                    'assets/images/door_lock.webp',
                    fit: BoxFit.cover,
                    alignment: Alignment.centerRight,
                  ),
                ),
                Positioned.fill(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Theme.of(context).colorScheme.surface,
                          Theme.of(context).colorScheme.surface
                              .withValues(alpha: .05),
                        ],
                        stops: const [.15, 1],
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(22),
                  child: FractionallySizedBox(
                    widthFactor: .62,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          door.name,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 10),
                        StatusPill(door.status),
                        const SizedBox(height: 36),
                        Text(
                          door.location ?? 'Chưa đặt vị trí',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                const Row(
                  children: [
                    Icon(Icons.info_outline, size: 16),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Trạng thái khóa vật lý chưa được xác nhận',
                        style: TextStyle(fontSize: 12),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(children: [_metric('Nhiệt độ'), _metric('Độ ẩm')]),
              ],
            ),
          ),
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final door = _selected;
    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(
            child: Image.asset(
              'assets/images/auth_background.jpg',
              fit: BoxFit.cover,
            ),
          ),
          Positioned.fill(
            child: ColoredBox(color: Colors.black.withValues(alpha: .25)),
          ),
          SafeArea(
            child: RefreshIndicator(
              onRefresh: _load,
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 560),
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
                    children: [
                      Row(
                        children: [
                          IconButton.filledTonal(
                            tooltip: 'Hồ sơ tài khoản',
                            icon: const Icon(Icons.person_outline),
                            onPressed: () => _open(
                              ProfileScreen(
                                session: widget.session,
                                doorRepository: widget.repository,
                              ),
                            ),
                          ),
                          const Spacer(),
                          IconButton.filledTonal(
                            tooltip: 'Làm mới dashboard',
                            onPressed: _loading ? null : _load,
                            icon: const Icon(Icons.refresh),
                          ),
                          const SizedBox(width: 8),
                          IconButton.filledTonal(
                            tooltip: 'Chuyển giao diện',
                            onPressed: widget.onToggleTheme,
                            icon: Icon(
                              Theme.of(context).brightness == Brightness.dark
                                  ? Icons.light_mode_outlined
                                  : Icons.dark_mode_outlined,
                            ),
                          ),
                        ],
                      ),
                      _section('Tổng quan cửa'),
                      if (_loading)
                        _panel(const Center(child: CircularProgressIndicator()))
                      else if (_error != null)
                        _panel(
                          FailurePanel(
                            error: _error!,
                            onRetry: _load,
                            onSignInRequested: _signIn,
                          ),
                        )
                      else if (_doors.isEmpty)
                        _panel(
                          Column(
                            children: [
                              const Icon(
                                Icons.door_front_door_outlined,
                                size: 48,
                              ),
                              const SizedBox(height: 12),
                              const Text('Bạn chưa có cửa nào'),
                              const SizedBox(height: 12),
                              FilledButton.icon(
                                onPressed: _manage,
                                icon: const Icon(Icons.add),
                                label: const Text('Thêm cửa'),
                              ),
                            ],
                          ),
                        )
                      else ...[
                        _panel(
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  const Expanded(
                                    child: Text(
                                      'Cửa của bạn',
                                      style: TextStyle(
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                  Text('${_doors.length} cửa'),
                                ],
                              ),
                              const SizedBox(height: 12),
                              DropdownButtonFormField<int>(
                                key: ValueKey(_selectedId),
                                initialValue: _selectedId,
                                isExpanded: true,
                                decoration: const InputDecoration(
                                  labelText: 'Chọn cửa',
                                ),
                                items: _doors
                                    .map(
                                      (d) => DropdownMenuItem(
                                        value: d.id,
                                        child: Text(
                                          d.name,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ),
                                    )
                                    .toList(),
                                onChanged: (id) {
                                  if (id == null) return;
                                  setState(() => _selectedId = id);
                                  _loadDevices(id);
                                },
                              ),
                              const SizedBox(height: 10),
                              Text(
                                'Đang xem: ${door?.name ?? ""}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                        if (door != null) ...[
                          const SizedBox(height: 16),
                          _overview(door),
                          const SizedBox(height: 16),
                          _panel(
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Icon(Icons.router_outlined),
                                    const SizedBox(width: 10),
                                    const Expanded(
                                      child: Text(
                                        'Thiết bị của cửa',
                                        style: TextStyle(
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                    ),
                                    TextButton(
                                      onPressed: _detail,
                                      child: const Text('Chi tiết'),
                                    ),
                                  ],
                                ),
                                if (_loadingDevices)
                                  const LinearProgressIndicator()
                                else if (_deviceError != null)
                                  FailurePanel(
                                    error: _deviceError!,
                                    onRetry: () => _loadDevices(door.id),
                                    onSignInRequested: _signIn,
                                  )
                                else if (_devices.isEmpty)
                                  const Text('Chưa có thiết bị')
                                else
                                  ..._devices.map(
                                    (d) => Padding(
                                      padding: const EdgeInsets.symmetric(
                                        vertical: 8,
                                      ),
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            d.deviceCode,
                                            style: const TextStyle(
                                              fontWeight: FontWeight.w600,
                                            ),
                                          ),
                                          const SizedBox(height: 6),
                                          Wrap(
                                            spacing: 8,
                                            runSpacing: 6,
                                            children: [
                                              StatusPill(d.lifecycleStatus),
                                              StatusPill(d.connectionStatus),
                                            ],
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                          _section('Quản lý truy cập'),
                          _unavailable(Icons.people_outline, 'Người truy cập'),
                          const SizedBox(height: 14),
                          LayoutBuilder(
                            builder: (context, c) {
                              final cards = [
                                _unavailable(
                                  Icons.contactless_outlined,
                                  'Thẻ từ',
                                ),
                                _unavailable(Icons.dialpad_outlined, 'Mã PIN'),
                              ];
                              if (c.maxWidth < 340 ||
                                  MediaQuery.textScalerOf(context).scale(1) >=
                                      1.4) {
                                return Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    cards[0],
                                    const SizedBox(height: 14),
                                    cards[1],
                                  ],
                                );
                              }
                              return Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Expanded(child: cards[0]),
                                  const SizedBox(width: 14),
                                  Expanded(child: cards[1]),
                                ],
                              );
                            },
                          ),
                          _section('Cấu hình cửa'),
                          _panel(
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Text(
                                  door.name,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 8),
                                _config(
                                  Icons.shield_outlined,
                                  'Chế độ bảo mật',
                                ),
                                _config(
                                  Icons.local_fire_department_outlined,
                                  'Báo cháy',
                                ),
                                _config(
                                  Icons.timer_outlined,
                                  'Cảnh báo cửa mở quá lâu',
                                ),
                                const Divider(height: 28),
                                OutlinedButton.icon(
                                  onPressed: _detail,
                                  icon: const Icon(Icons.settings_outlined),
                                  label: const Text('Thông tin và thiết bị'),
                                ),
                                TextButton.icon(
                                  onPressed:
                                      _loadingDevices || _deviceError != null
                                      ? null
                                      : () => _open(
                                          LifecycleHistoryScreen(
                                            repository: widget.repository,
                                            doorId: door.id,
                                            devices: _devices,
                                          ),
                                        ),
                                  icon: const Icon(Icons.history),
                                  label: const Text('Lịch sử thiết bị'),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ],
                      const SizedBox(height: 20),
                      FilledButton.icon(
                        onPressed: _manage,
                        icon: const Icon(Icons.meeting_room_outlined),
                        label: const Text('Quản lý cửa'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
