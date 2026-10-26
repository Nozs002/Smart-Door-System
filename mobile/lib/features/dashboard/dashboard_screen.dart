import 'package:flutter/material.dart';

import 'door_overview_card.dart';
import 'dashboard_widgets.dart';

import '../authentication/auth_session.dart';
import '../authentication/profile_screen.dart';
import '../doors/data/door_models.dart';
import '../doors/data/door_repository.dart';
import '../doors/presentation/door_detail_screen.dart';
import '../doors/presentation/door_list_screen.dart';
import '../doors/presentation/door_widgets.dart';
import '../doors/presentation/lifecycle_history_screen.dart';
import '../settings/data/settings_models.dart';
import '../settings/data/settings_repository.dart';
import '../settings/presentation/door_settings_screen.dart';
import '../alerts/data/alert_repository.dart';
import '../alerts/presentation/alert_list_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({
    super.key,
    required this.session,
    required this.repository,
    required this.onToggleTheme,
    this.settingsRepository,
    this.alertRepository,
  });
  final AuthSession session;
  final DoorRepository repository;
  final SettingsRepository? settingsRepository;
  final AlertRepository? alertRepository;
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
  DoorConfiguration? _configurationValue;
  Object? _configurationError;
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
      if (_selectedId != null) {
        await Future.wait([
          _loadDevices(_selectedId!),
          _loadConfiguration(_selectedId!),
        ]);
      }
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

  Future<void> _loadConfiguration(int id) async {
    final repository = widget.settingsRepository;
    if (repository == null) return;
    setState(() {
      _configurationValue = null;
      _configurationError = null;
    });
    try {
      final value = await repository.getConfiguration(id);
      if (mounted && _selectedId == id) {
        setState(() => _configurationValue = value);
      }
    } catch (error) {
      if (mounted && _selectedId == id) {
        setState(() => _configurationError = error);
      }
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

  Future<void> _history() => _open(
    LifecycleHistoryScreen(
      repository: widget.repository,
      doorId: _selectedId!,
      devices: _devices,
    ),
  );

  Future<void> _settings() => _open(
    DoorSettingsScreen(
      repository: widget.settingsRepository!,
      doorId: _selectedId!,
      doorName: _selected!.name,
      onSignInRequested: _signIn,
    ),
  );

  Future<void> _alerts() => _open(
    AlertListScreen(
      repository: widget.alertRepository!,
      onSignInRequested: _signIn,
    ),
  );

  void _unavailable(String title) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              const Text(
                'Chức năng chưa khả dụng. Bạn vẫn có thể xem cửa, thiết bị và lịch sử thiết bị.',
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Đã hiểu'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _heading(String title, {String? doorName}) => Padding(
    padding: const EdgeInsets.only(top: 22, bottom: 14),
    child: Wrap(
      spacing: 12,
      runSpacing: 10,
      crossAxisAlignment: WrapCrossAlignment.center,
      alignment: WrapAlignment.spaceBetween,
      children: [
        Text(
          title.toUpperCase(),
          style: const TextStyle(
            color: Colors.white,
            fontSize: 13,
            fontWeight: FontWeight.w700,
            letterSpacing: .8,
            shadows: [Shadow(blurRadius: 8, color: Colors.black54)],
          ),
        ),
        if (doorName != null)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: Colors.black.withValues(alpha: .3),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white.withValues(alpha: .4)),
            ),
            child: Text(
              doorName,
              style: const TextStyle(color: Colors.white, fontSize: 12),
            ),
          ),
      ],
    ),
  );

  Widget _picker() => DashboardGlassPanel(
    radius: 22,
    padding: const EdgeInsets.fromLTRB(18, 8, 18, 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Expanded(
              child: Text(
                'Cửa của bạn',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
            ),
            Text(
              '${_doors.length} cửa',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            IconButton(
              tooltip: 'Làm mới dashboard',
              onPressed: _loading ? null : _load,
              icon: const Icon(Icons.refresh, size: 18),
            ),
          ],
        ),
        DropdownButtonFormField<int>(
          key: ValueKey(_selectedId),
          initialValue: _selectedId,
          isExpanded: true,
          decoration: InputDecoration(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 12,
            ),
            filled: false,
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(
                color: Theme.of(context).dividerColor.withValues(alpha: .25),
              ),
            ),
          ),
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurface,
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
          items: _doors
              .map(
                (d) => DropdownMenuItem(
                  value: d.id,
                  child: Text(d.name, overflow: TextOverflow.ellipsis),
                ),
              )
              .toList(),
          onChanged: (id) {
            if (id == null) return;
            setState(() => _selectedId = id);
            _loadDevices(id);
            _loadConfiguration(id);
          },
        ),
        const SizedBox(height: 10),
        Text(
          'Đang xem: ${_selected?.name ?? ""}',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    ),
  );

  String get _deviceSummary {
    if (_loadingDevices) return 'Đang tải thiết bị…';
    if (_deviceError != null) return 'Không tải được trạng thái thiết bị';
    if (_devices.isEmpty) return 'Chưa có thiết bị';
    if (_devices.every((d) => d.connectionStatus == 'UNKNOWN')) {
      return '${_devices.length} thiết bị · Chưa có heartbeat';
    }
    final online = _devices.where((d) => d.connectionStatus == 'ONLINE').length;
    return '${_devices.length} thiết bị · $online trực tuyến';
  }

  Widget _devicePill() => DashboardGlassPanel(
    radius: 30,
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
    onTap: _detail,
    child: Row(
      children: [
        CircleAvatar(
          radius: 20,
          backgroundColor: Theme.of(context).colorScheme.surface,
          foregroundColor: Theme.of(context).colorScheme.onSurface,
          child: const Icon(Icons.router_outlined, size: 20),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _deviceSummary,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 3),
              const Text(
                'Thiết bị của cửa',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        const Icon(Icons.chevron_right, size: 18),
      ],
    ),
  );

  Widget _access() => Column(
    children: [
      Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Người truy cập',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        CircleAvatar(
                          radius: 16,
                          backgroundColor: Theme.of(context)
                              .scaffoldBackgroundColor,
                          foregroundColor: Theme.of(context)
                              .colorScheme
                              .onSurface,
                          child: const Icon(Icons.people_outline, size: 18),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'Chưa có dữ liệu',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Quản lý người truy cập',
                style: IconButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.onSurface,
                  foregroundColor: Theme.of(context).colorScheme.surface,
                ),
                onPressed: () => _unavailable('Người truy cập'),
                icon: const Icon(Icons.add, size: 20),
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 16),
      LayoutBuilder(
        builder: (context, constraints) {
          final columns = MediaQuery.textScalerOf(context).scale(1) >= 1.4
              ? 1
              : 2;
          final width = (constraints.maxWidth - (columns - 1) * 16) / columns;
          final items = [
            DashboardFeatureCard(
              title: 'Thẻ từ',
              subtitle: 'Chưa khả dụng',
              icon: Icons.credit_card_outlined,
              onTap: () => _unavailable('Thẻ từ'),
            ),
            DashboardFeatureCard(
              title: 'Mã PIN',
              subtitle: 'Chưa khả dụng',
              icon: Icons.dialpad_outlined,
              onTap: () => _unavailable('Mã PIN'),
            ),
            DashboardFeatureCard(
              title: 'Người giữ thẻ',
              subtitle: 'Chưa khả dụng',
              icon: Icons.people_outline,
              onTap: () => _unavailable('Người giữ thẻ'),
            ),
            DashboardFeatureCard(
              title: 'Lịch sử thiết bị',
              subtitle: 'Theo cửa đang chọn',
              icon: Icons.history,
              onTap: _loadingDevices || _deviceError != null ? null : _history,
            ),
          ];
          return Wrap(
            spacing: 16,
            runSpacing: 16,
            children: items
                .map((item) => SizedBox(width: width, child: item))
                .toList(),
          );
        },
      ),
    ],
  );

  Widget _configuration() => DashboardGlassPanel(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const DashboardConfigurationRow(
          title: 'Chế độ bảo mật',
          icon: Icons.shield_outlined,
        ),
        const Divider(height: 1),
        const DashboardConfigurationRow(
          title: 'Báo cháy',
          icon: Icons.local_fire_department_outlined,
        ),
        const Divider(height: 1),
        const DashboardConfigurationRow(
          title: 'Cảnh báo cửa mở quá lâu',
          icon: Icons.timer_outlined,
        ),
        const Divider(height: 1),
        _configValue(
          'Giới hạn xác thực sai',
          _configurationValue == null
              ? null
              : '${_configurationValue!.maxFailedAttempts} lần',
        ),
        const Divider(height: 1),
        _configValue(
          'Thời gian khóa tạm',
          _configurationValue == null
              ? null
              : '${_configurationValue!.lockoutSeconds} giây',
        ),
        const Divider(height: 1),
        _configValue(
          'Ngưỡng nhiệt độ',
          _configurationValue == null
              ? null
              : '${_configurationValue!.temperatureThreshold.toStringAsFixed(1)}°C',
        ),
        const SizedBox(height: 12),
        FilledButton(
          onPressed: widget.settingsRepository == null ? _detail : _settings,
          child: const Row(
            children: [
              Expanded(child: Text('Cài đặt cửa')),
              Icon(Icons.chevron_right, size: 18),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Text(
          _configurationError != null
              ? 'Không tải được cấu hình. Chạm Cài đặt cửa để thử lại.'
              : _configurationValue == null
                  ? 'Đang tải cấu hình riêng của cửa đang chọn…'
                  : 'Phiên bản ${_configurationValue!.version} · '
                      '${_configurationValue!.devices.where((d) => d.configState == 'SYNCED').length}/'
                      '${_configurationValue!.devices.length} thiết bị đã xác nhận.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    ),
  );

  Widget _configValue(String label, String? value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: Text(label, style: const TextStyle(fontSize: 13))),
        const SizedBox(width: 12),
        Tooltip(
          message: value == null
              ? 'Chưa có dữ liệu'
              : 'Cấu hình đang lưu trên máy chủ',
          child: Text(
            value ?? '—',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    ),
  );

  Widget _nodes() => DashboardGlassPanel(
    padding: EdgeInsets.zero,
    child: ExpansionTile(
      key: ValueKey('nodes-$_selectedId'),
      title: const Text('Trạng thái thiết bị', style: TextStyle(fontSize: 15)),
      leading: const Icon(Icons.router_outlined, size: 20),
      tilePadding: const EdgeInsets.symmetric(horizontal: 20),
      childrenPadding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      shape: const Border(),
      collapsedShape: const Border(),
      children: [
        if (_loadingDevices)
          const LinearProgressIndicator()
        else if (_deviceError != null)
          FailurePanel(
            error: _deviceError!,
            onRetry: () => _loadDevices(_selectedId!),
            onSignInRequested: _signIn,
          )
        else if (_devices.isEmpty)
          const Text('Chưa có thiết bị')
        else
          ..._devices.map(
            (d) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    d.deviceCode,
                    style: const TextStyle(fontWeight: FontWeight.w600),
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
  );

  @override
  Widget build(BuildContext context) {
    final door = _selected;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(
            child: Image.asset(
              'assets/images/auth_background.jpg',
              fit: BoxFit.cover,
            ),
          ),
          if (dark)
            Positioned.fill(
              child: ColoredBox(color: Colors.black.withValues(alpha: .3)),
            ),
          SafeArea(
            child: RefreshIndicator(
              onRefresh: _load,
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 430),
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 32),
                    children: [
                      Row(
                        children: [
                          DashboardRoundAction(
                            label: 'Hồ sơ tài khoản',
                            icon: Icons.person_outline,
                            onPressed: () => _open(
                              ProfileScreen(
                                session: widget.session,
                                doorRepository: widget.repository,
                              ),
                            ),
                          ),
                          const Spacer(),
                          DashboardRoundAction(
                            label: 'Thông báo',
                            icon: Icons.notifications_none_rounded,
                            onPressed: widget.alertRepository == null
                                ? () => _unavailable('Thông báo')
                                : _alerts,
                          ),
                          const SizedBox(width: 10),
                          DashboardRoundAction(
                            label: 'Chuyển giao diện',
                            icon: dark
                                ? Icons.light_mode_outlined
                                : Icons.dark_mode_outlined,
                            onPressed: widget.onToggleTheme,
                          ),
                        ],
                      ),
                      _heading('Tổng quan cửa'),
                      if (_loading)
                        const DashboardGlassPanel(
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (_error != null)
                        DashboardGlassPanel(
                          child: FailurePanel(
                            error: _error!,
                            onRetry: _load,
                            onSignInRequested: _signIn,
                          ),
                        )
                      else if (_doors.isEmpty)
                        DashboardGlassPanel(
                          child: Column(
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
                      else if (door != null) ...[
                        _picker(),
                        const SizedBox(height: 14),
                        DoorOverviewCard(door: door, onTap: _detail),
                        const SizedBox(height: 14),
                        _devicePill(),
                        _heading('Quản lý truy cập'),
                        _access(),
                        _heading('Cấu hình cửa', doorName: door.name),
                        _configuration(),
                        const SizedBox(height: 16),
                        _nodes(),
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
