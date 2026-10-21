import 'package:flutter/material.dart';

import '../doors/data/door_repository.dart';
import '../doors/presentation/door_list_screen.dart';
import 'account_models.dart';
import 'auth_session.dart';
import 'auth_widgets.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.session, this.doorRepository});
  final AuthSession session;
  final DoorRepository? doorRepository;
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  AccountProfile? profile;
  String? error;
  bool loading = true;
  bool leaving = false;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await widget.session.profile();
      if (mounted) setState(() => profile = result);
    } on AuthFormException catch (failure) {
      if (mounted) setState(() => error = failure.message);
    } catch (_) {
      if (mounted) {
        setState(() => error = 'Không thể tải hồ sơ. Vui lòng thử lại.');
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> logout() async {
    if (leaving) return;
    setState(() => leaving = true);
    try {
      await widget.session.logout();
    } on AuthFormException catch (failure) {
      if (mounted) setState(() => error = failure.message);
    } finally {
      if (mounted) setState(() => leaving = false);
    }
  }

  String date(DateTime value) {
    final local = value.toLocal();
    return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year}';
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Hồ sơ tài khoản')),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                'Thông tin tài khoản của bạn',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      CircleAvatar(
                        radius: 40,
                        backgroundColor: Theme.of(context)
                            .colorScheme
                            .surfaceContainerHighest,
                        child: const Icon(Icons.person_outline, size: 44),
                      ),
                      const SizedBox(height: 16),
                      if (loading) const CircularProgressIndicator(),
                      if (profile != null && !loading) ...[
                        Text(
                          profile!.displayName,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 10),
                        const Chip(
                          avatar: Icon(Icons.check_circle_outline, size: 18),
                          label: Text('Đang hoạt động'),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              if (profile != null && !loading)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Text(
                          'Thông tin tài khoản',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 24),
                        _row('Tên đăng nhập', profile!.username),
                        _row('Mã tài khoản', '#${profile!.id}'),
                        _row('Ngày tạo tài khoản', date(profile!.createdAt)),
                      ],
                    ),
                  ),
                ),
              AuthError(message: error),
              const SizedBox(height: 16),
              if (widget.doorRepository != null) ...[
                Card(
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 10,
                    ),
                    leading: const Icon(Icons.door_front_door_outlined),
                    title: const Text('Quản lý cửa'),
                    subtitle: const Text('Xem các cửa thuộc tài khoản'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (_) => DoorListScreen(
                          repository: widget.doorRepository!,
                          onSignInRequested: () {
                            Navigator.of(context)
                                .popUntil((route) => route.isFirst);
                            widget.session.expire();
                          },
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
              ],
              OutlinedButton.icon(
                onPressed: loading || leaving ? null : load,
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('Tải lại hồ sơ'),
              ),
              const SizedBox(height: 12),
              AuthButton(
                label: 'Đăng xuất',
                loading: leaving,
                onPressed: logout,
              ),
            ],
          ),
        ),
      ),
    ),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 6),
        SelectableText(
          value,
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
      ],
    ),
  );
}
