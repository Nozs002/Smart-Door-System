import 'package:flutter/material.dart';

import 'account_models.dart';
import 'auth_session.dart';
import 'auth_widgets.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.session});
  final AuthSession session;
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
  Widget build(BuildContext context) => AuthLayout(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(
          Icons.account_circle_outlined,
          size: 48,
          color: Color(0xff111111),
        ),
        const SizedBox(height: 16),
        const Text(
          'Hồ sơ tài khoản',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            color: Color(0xff111111),
          ),
        ),
        const SizedBox(height: 24),
        if (loading) const Center(child: CircularProgressIndicator()),
        if (profile != null && !loading) ...[
          _row('Tên hiển thị', profile!.displayName),
          _row('Tên đăng nhập', profile!.username),
          _row('Trạng thái', 'Đang hoạt động'),
          _row('Ngày tạo tài khoản', date(profile!.createdAt)),
        ],
        AuthError(message: error),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: loading || leaving ? null : load,
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Tải lại hồ sơ'),
        ),
        const SizedBox(height: 12),
        AuthButton(label: 'Đăng xuất', loading: leaving, onPressed: logout),
      ],
    ),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: Color(0xff555555)),
        ),
        const SizedBox(height: 6),
        SelectableText(
          value,
          style: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w600,
            color: Color(0xff111111),
          ),
        ),
      ],
    ),
  );
}
