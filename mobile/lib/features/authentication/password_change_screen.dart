import 'dart:convert';

import 'package:flutter/material.dart';

import '../../core/network/api_exception.dart';
import 'auth_session.dart';

class PasswordChangeScreen extends StatefulWidget {
  const PasswordChangeScreen({required this.session, super.key});
  final AuthSession session;
  @override
  State<PasswordChangeScreen> createState() => _PasswordChangeScreenState();
}

class _PasswordChangeScreenState extends State<PasswordChangeScreen> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirmation = TextEditingController();
  bool _saving = false;
  String? _error;
  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_saving || !_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.session.changePassword(_current.text, _next.text);
      if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = error is AuthFormException
              ? error.message
              : 'Không thể đổi mật khẩu. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Đổi mật khẩu')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const Text(
              'Sau khi đổi mật khẩu, các phiên đăng nhập cũ sẽ kết thúc. Bạn cần đăng nhập lại.',
            ),
            const SizedBox(height: 20),
            Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    key: const Key('currentPassword'),
                    controller: _current,
                    obscureText: true,
                    enabled: !_saving,
                    enableSuggestions: false,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Mật khẩu hiện tại',
                    ),
                    validator: (value) => value == null || value.isEmpty
                        ? 'Nhập mật khẩu hiện tại.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    key: const Key('newPassword'),
                    controller: _next,
                    obscureText: true,
                    enabled: !_saving,
                    enableSuggestions: false,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Mật khẩu mới',
                    ),
                    validator: (value) =>
                        value == null ||
                            value.runes.length < 8 ||
                            utf8.encode(value).length > 72
                        ? 'Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    key: const Key('confirmPassword'),
                    controller: _confirmation,
                    obscureText: true,
                    enabled: !_saving,
                    enableSuggestions: false,
                    autocorrect: false,
                    decoration: const InputDecoration(
                      labelText: 'Nhập lại mật khẩu mới',
                    ),
                    validator: (value) => value != _next.text
                        ? 'Mật khẩu xác nhận chưa khớp.'
                        : null,
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _saving ? null : _submit,
                    child: Text(
                      _saving ? 'Đang cập nhật…' : 'Xác nhận đổi mật khẩu',
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
