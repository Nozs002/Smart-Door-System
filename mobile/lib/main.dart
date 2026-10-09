import 'package:flutter/material.dart';

void main() => runApp(const SmartDoorApp());

class SmartDoorApp extends StatefulWidget {
  const SmartDoorApp({super.key});
  @override
  State<SmartDoorApp> createState() => _SmartDoorAppState();
}

class _SmartDoorAppState extends State<SmartDoorApp> {
  ThemeMode mode = ThemeMode.light;
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'SmartLock',
        debugShowCheckedModeBanner: false,
        themeMode: mode,
        theme: _theme(Brightness.light),
        darkTheme: _theme(Brightness.dark),
        home: AppShell(onThemeChanged: (dark) => setState(() => mode = dark ? ThemeMode.dark : ThemeMode.light)),
      );

  ThemeData _theme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(seedColor: const Color(0xff0099cc), brightness: brightness);
    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: dark ? const Color(0xff0a0f1e) : const Color(0xfff4f2ec),
      fontFamily: 'sans',
      cardTheme: CardThemeData(color: dark ? const Color(0xff111827) : Colors.white, elevation: 0, margin: EdgeInsets.zero),
      inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: dark ? const Color(0xff172033) : Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide.none)),
    );
  }
}

class AppShell extends StatefulWidget {
  const AppShell({super.key, required this.onThemeChanged});
  final ValueChanged<bool> onThemeChanged;
  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int index = 0;
  bool dark = false;
  bool security = true;
  bool alarm = true;
  bool unlocked = false;
  final pages = const [DashboardPage(), DoorPage(), AccessPage(), AlertsPage(), SettingsPage()];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(child: pages[index]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: 'Tổng quan'),
          NavigationDestination(icon: Icon(Icons.door_front_door_outlined), selectedIcon: Icon(Icons.door_front_door), label: 'Cửa'),
          NavigationDestination(icon: Icon(Icons.fingerprint), label: 'Truy cập'),
          NavigationDestination(icon: Icon(Icons.shield_outlined), selectedIcon: Icon(Icons.shield), label: 'Cảnh báo'),
          NavigationDestination(icon: Icon(Icons.tune), label: 'Cài đặt'),
        ],
      ),
    );
  }

  void showUnlock() => showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => UnlockSheet(unlocked: unlocked, onChanged: (value) => setState(() => unlocked = value)),
      );
}

class PageFrame extends StatelessWidget {
  const PageFrame({super.key, required this.title, required this.child, this.trailing});
  final String title;
  final Widget child;
  final Widget? trailing;
  @override
  Widget build(BuildContext context) => CustomScrollView(slivers: [
        SliverPadding(padding: const EdgeInsets.fromLTRB(20, 18, 20, 24), sliver: SliverToBoxAdapter(child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(title, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)), if (trailing != null) trailing!]))),
        SliverPadding(padding: const EdgeInsets.symmetric(horizontal: 20), sliver: SliverToBoxAdapter(child: child)),
      ]);
}

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key});
  @override
  Widget build(BuildContext context) => PageFrame(title: 'Xin chào, Admin', child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _StatusBanner(),
        const SizedBox(height: 18),
        Text('Thiết bị của bạn', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        _DoorCard(onTap: () => (context.findAncestorStateOfType<_AppShellState>())?.showUnlock()),
        const SizedBox(height: 18),
        Row(children: [Expanded(child: _MetricCard(icon: Icons.thermostat, label: 'Nhiệt độ', value: '27°C', color: Colors.orange)), const SizedBox(width: 12), Expanded(child: _MetricCard(icon: Icons.wifi, label: 'Kết nối', value: 'Ổn định', color: Colors.green))]),
        const SizedBox(height: 18),
        _ActionCard(icon: Icons.shield_outlined, title: 'Chế độ bảo mật', subtitle: 'Chỉ người có khóa được mở cửa', value: true),
        const SizedBox(height: 12),
        _ActionCard(icon: Icons.notifications_active_outlined, title: 'Báo động cạy phá', subtitle: 'Đang bật và sẵn sàng', value: true),
      ]));
}

class DoorPage extends StatelessWidget {
  const DoorPage({super.key});
  @override
  Widget build(BuildContext context) => PageFrame(title: 'Điều khiển cửa', trailing: IconButton(onPressed: () {}, icon: const Icon(Icons.more_horiz)), child: Column(children: [
        _DoorCard(onTap: () => (context.findAncestorStateOfType<_AppShellState>())?.showUnlock(), large: true),
        const SizedBox(height: 18),
        Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Hoạt động gần đây', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)), const SizedBox(height: 12), const _HistoryRow(name: 'Nguyễn Minh', method: 'Vân tay #01', time: '07:32', ok: true), const _HistoryRow(name: 'Không xác định', method: 'Mã PIN sai', time: '06:41', ok: false)]))),
      ]));
}

class AccessPage extends StatefulWidget { const AccessPage({super.key}); @override State<AccessPage> createState() => _AccessPageState(); }
class _AccessPageState extends State<AccessPage> {
  int tab = 0;
  @override Widget build(BuildContext context) => PageFrame(title: 'Quản lý truy cập', child: Column(children: [
    SegmentedButton<int>(segments: const [ButtonSegment(value: 0, label: Text('Vân tay'), icon: Icon(Icons.fingerprint)), ButtonSegment(value: 1, label: Text('Thẻ từ'), icon: Icon(Icons.credit_card)), ButtonSegment(value: 2, label: Text('PIN'), icon: Icon(Icons.password))], selected: {tab}, onSelectionChanged: (v) => setState(() => tab = v.first)),
    const SizedBox(height: 18), if (tab == 0) ...[_ListHeader(text: '4 vân tay đã đăng ký'), const _AccessRow(number: '01', name: 'Nguyễn Minh (Bố)', detail: 'Thêm: 12/06/2025'), const _AccessRow(number: '02', name: 'Trần Lan (Mẹ)', detail: 'Thêm: 12/06/2025')] else if (tab == 1) ...[_ListHeader(text: '2 thẻ đã đăng ký'), const _AccessRow(icon: Icons.credit_card, name: 'Thẻ chủ — Nguyễn Minh', detail: 'A3F2:B5C1')] else [Card(child: Padding(padding: const EdgeInsets.all(20), child: Column(children: [const Text('Mã PIN hiện tại', style: TextStyle(fontWeight: FontWeight.bold)), const SizedBox(height: 12), Text('• • • • • •', style: Theme.of(context).textTheme.headlineSmall), const SizedBox(height: 16), OutlinedButton(onPressed: () {}, child: const Text('Đổi mã PIN từ xa'))])))]));
}

class AlertsPage extends StatefulWidget { const AlertsPage({super.key}); @override State<AlertsPage> createState() => _AlertsPageState(); }
class _AlertsPageState extends State<AlertsPage> { int tab = 0; @override Widget build(BuildContext context) => PageFrame(title: 'Cảnh báo & nhật ký', child: Column(children: [SegmentedButton<int>(segments: const [ButtonSegment(value: 0, label: Text('Cảnh báo')), ButtonSegment(value: 1, label: Text('Lịch sử'))], selected: {tab}, onSelectionChanged: (v) => setState(() => tab = v.first)), const SizedBox(height: 18), if (tab == 0) ...[const _AlertRow(icon: Icons.local_fire_department, title: 'Cảnh báo cháy!', detail: 'Nhiệt độ vượt 52°C — chốt tự động mở', time: '14:22 hôm nay', color: Colors.red), const _AlertRow(icon: Icons.warning_amber, title: 'Nhập mật khẩu sai liên tục', detail: '5 lần sai — keypad bị khóa 60s', time: '06:41 hôm nay', color: Colors.orange)] else ...[const _HistoryRow(name: 'Nguyễn Minh', method: 'Vân tay #01', time: '07:32', ok: true), const _HistoryRow(name: 'Không xác định', method: 'Mã PIN (Sai)', time: '06:41', ok: false)]])); }

class SettingsPage extends StatefulWidget { const SettingsPage({super.key}); @override State<SettingsPage> createState() => _SettingsPageState(); }
class _SettingsPageState extends State<SettingsPage> { bool fire = true, tamper = true, dark = false; double temp = 50, attempts = 5; @override Widget build(BuildContext context) => PageFrame(title: 'Cài đặt', child: Column(children: [Card(child: Column(children: [_SettingSwitch(title: 'Thông báo cháy nổ', subtitle: 'Khi nhiệt độ vượt ngưỡng', value: fire, onChanged: (v) => setState(() => fire = v)), _SettingSwitch(title: 'Báo động cạy phá', value: tamper, onChanged: (v) => setState(() => tamper = v))])), const SizedBox(height: 14), Card(child: Padding(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('THÔNG SỐ CẢM BIẾN', style: Theme.of(context).textTheme.labelMedium?.copyWith(letterSpacing: 1, color: Theme.of(context).colorScheme.primary)), const SizedBox(height: 14), _SliderSetting(title: 'Ngưỡng nhiệt độ báo cháy', value: temp, suffix: '°C', min: 30, max: 80, onChanged: (v) => setState(() => temp = v)), _SliderSetting(title: 'Giới hạn nhập sai PIN', value: attempts, suffix: ' lần', min: 1, max: 10, onChanged: (v) => setState(() => attempts = v))]))), const SizedBox(height: 14), Card(child: SwitchListTile(title: const Text('Giao diện tối'), subtitle: const Text('Lưu lựa chọn trên thiết bị'), value: dark, onChanged: (v) { setState(() => dark = v); (context.findAncestorStateOfType<_AppShellState>())?.widget.onThemeChanged(v); }) )])); }
}

class UnlockSheet extends StatefulWidget { const UnlockSheet({super.key, required this.unlocked, required this.onChanged}); final bool unlocked; final ValueChanged<bool> onChanged; @override State<UnlockSheet> createState() => _UnlockSheetState(); }
class _UnlockSheetState extends State<UnlockSheet> { late bool open = widget.unlocked; @override Widget build(BuildContext context) => Container(padding: const EdgeInsets.fromLTRB(24, 14, 24, 28), decoration: BoxDecoration(color: Theme.of(context).colorScheme.surface, borderRadius: const BorderRadius.vertical(top: Radius.circular(30))), child: Column(mainAxisSize: MainAxisSize.min, children: [Container(width: 42, height: 4, decoration: BoxDecoration(color: Theme.of(context).dividerColor, borderRadius: BorderRadius.circular(4))), const SizedBox(height: 22), Icon(open ? Icons.lock_open_rounded : Icons.lock_rounded, size: 56, color: open ? Colors.green : Theme.of(context).colorScheme.primary), const SizedBox(height: 12), Text(open ? 'Đã mở khóa' : 'Đang khóa', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)), Text(open ? 'Cửa sẽ tự khóa sau 30 giây' : 'Xác thực để mở cửa', style: Theme.of(context).textTheme.bodyMedium), const SizedBox(height: 24), FilledButton.icon(onPressed: () { setState(() => open = !open); widget.onChanged(open); }, icon: Icon(open ? Icons.lock : Icons.lock_open), label: Text(open ? 'Khóa cửa' : 'Vuốt để mở khóa'), style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(54))), const SizedBox(height: 10), TextButton(onPressed: () => Navigator.pop(context), child: const Text('Đóng'))])); }

class _StatusBanner extends StatelessWidget { @override Widget build(BuildContext context) => Container(width: double.infinity, padding: const EdgeInsets.all(18), decoration: BoxDecoration(gradient: const LinearGradient(colors: [Color(0xff008bb9), Color(0xff00b4d8)]), borderRadius: BorderRadius.circular(22)), child: const Row(children: [Icon(Icons.verified_user, color: Colors.white, size: 32), SizedBox(width: 14), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Hệ thống an toàn', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)), Text('Tất cả cảm biến đang hoạt động bình thường', style: TextStyle(color: Colors.white70, fontSize: 12))]) ])); }
class _DoorCard extends StatelessWidget { const _DoorCard({required this.onTap, this.large = false}); final VoidCallback onTap; final bool large; @override Widget build(BuildContext context) => Card(child: InkWell(onTap: onTap, borderRadius: BorderRadius.circular(22), child: Padding(padding: EdgeInsets.all(large ? 26 : 20), child: Row(children: [Container(width: large ? 76 : 60, height: large ? 76 : 60, decoration: BoxDecoration(color: Colors.green.withOpacity(.12), shape: BoxShape.circle), child: Icon(Icons.lock, color: Colors.green, size: large ? 38 : 30)), const SizedBox(width: 16), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Cửa chính', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)), const SizedBox(height: 4), const Text('Đang khóa • Chạm để điều khiển', style: TextStyle(color: Colors.green, fontSize: 13))])), const Icon(Icons.chevron_right)]))); }
class _MetricCard extends StatelessWidget { const _MetricCard({required this.icon, required this.label, required this.value, required this.color}); final IconData icon; final String label, value; final Color color; @override Widget build(BuildContext context) => Card(child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [Icon(icon, color: color), const SizedBox(width: 10), Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(label, style: Theme.of(context).textTheme.bodySmall), Text(value, style: const TextStyle(fontWeight: FontWeight.bold))])]))); }
class _ActionCard extends StatelessWidget { const _ActionCard({required this.icon, required this.title, required this.subtitle, required this.value}); final IconData icon; final String title, subtitle; final bool value; @override Widget build(BuildContext context) => Card(child: ListTile(leading: Icon(icon, color: Theme.of(context).colorScheme.primary), title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)), subtitle: Text(subtitle), trailing: Switch(value: value, onChanged: (_) {}))); }
class _ListHeader extends StatelessWidget { const _ListHeader({required this.text}); final String text; @override Widget build(BuildContext context) => Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(text, style: Theme.of(context).textTheme.bodySmall), FilledButton.tonal(onPressed: () {}, child: const Text('+ Thêm'))]); }
class _AccessRow extends StatelessWidget { const _AccessRow({this.number, this.icon, required this.name, required this.detail}); final String? number, name, detail; final IconData? icon; @override Widget build(BuildContext context) => Card(child: ListTile(leading: CircleAvatar(backgroundColor: Theme.of(context).colorScheme.primaryContainer, child: number != null ? Text(number!) : Icon(icon)), title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)), subtitle: Text(detail))); }
class _HistoryRow extends StatelessWidget { const _HistoryRow({required this.name, required this.method, required this.time, required this.ok}); final String name, method, time; final bool ok; @override Widget build(BuildContext context) => ListTile(contentPadding: EdgeInsets.zero, leading: CircleAvatar(backgroundColor: (ok ? Colors.green : Colors.red).withOpacity(.12), child: Icon(ok ? Icons.check : Icons.close, color: ok ? Colors.green : Colors.red)), title: Text(name, style: const TextStyle(fontWeight: FontWeight.w600)), subtitle: Text(method), trailing: Text(time, style: Theme.of(context).textTheme.bodySmall)); }
class _AlertRow extends StatelessWidget { const _AlertRow({required this.icon, required this.title, required this.detail, required this.time, required this.color}); final IconData icon; final String title, detail, time; final Color color; @override Widget build(BuildContext context) => Card(child: ListTile(leading: CircleAvatar(backgroundColor: color.withOpacity(.12), child: Icon(icon, color: color)), title: Text(title, style: TextStyle(color: color, fontWeight: FontWeight.bold)), subtitle: Text('$detail\n$time'))); }
class _SettingSwitch extends StatelessWidget { const _SettingSwitch({required this.title, this.subtitle, required this.value, required this.onChanged}); final String title; final String? subtitle; final bool value; final ValueChanged<bool> onChanged; @override Widget build(BuildContext context) => SwitchListTile(title: Text(title), subtitle: subtitle == null ? null : Text(subtitle!), value: value, onChanged: onChanged); }
class _SliderSetting extends StatelessWidget { const _SliderSetting({required this.title, required this.value, required this.suffix, required this.min, required this.max, required this.onChanged}); final String title, suffix; final double value, min, max; final ValueChanged<double> onChanged; @override Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(title), Text('${value.round()}$suffix', style: TextStyle(color: Theme.of(context).colorScheme.primary, fontWeight: FontWeight.bold))]), Slider(value: value, min: min, max: max, divisions: (max - min).round(), onChanged: onChanged)]); }
