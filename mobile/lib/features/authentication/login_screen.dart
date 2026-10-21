import 'dart:convert';

import 'package:flutter/material.dart';

import 'auth_widgets.dart';
import 'register_screen.dart';

typedef LoginSubmit = Future<void> Function(String username, String password);

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, this.onSubmit, this.onRegister, this.notice});
  final LoginSubmit? onSubmit;
  final RegisterSubmit? onRegister;
  final String? notice;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final form = GlobalKey<FormState>();
  final username = TextEditingController();
  final password = TextEditingController();
  bool loading = false;
  String? error;

  @override
  void dispose() {
    username.dispose();
    password.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    if (loading || !form.currentState!.validate()) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      error = null;
      loading = true;
    });
    try {
      if (widget.onSubmit == null) {
        throw const AuthFormException(
          'Đăng nhập chưa khả dụng. Vui lòng thử lại sau.',
        );
      }
      await widget.onSubmit!(username.text, password.text);
      if (!mounted) {
        return;
      }
      password.clear();
    } on AuthFormException catch (failure) {
      if (mounted) {
        setState(() => error = failure.message);
      }
    } catch (_) {
      if (mounted) {
        setState(() => error = 'Không thể đăng nhập. Vui lòng thử lại.');
      }
    } finally {
      if (mounted) {
        setState(() => loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthLayout(
    child: AutofillGroup(
      child: Form(
        key: form,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Đăng nhập',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w700,
                color: Color(0xff111111),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Chào mừng bạn trở lại.',
              style: TextStyle(fontSize: 14, color: Color(0xff555555)),
            ),
            const SizedBox(height: 24),
            AuthField(
              label: 'Tên đăng nhập',
              controller: username,
              enabled: !loading,
              autofillHints: const [AutofillHints.username],
              validator: (value) {
                if (value == null || value.trim().isEmpty) {
                  return 'Vui lòng nhập tên đăng nhập.';
                }
                if (value.runes.length > 100) {
                  return 'Tên đăng nhập tối đa 100 ký tự.';
                }
                return null;
              },
            ),
            AuthField(
              label: 'Mật khẩu',
              controller: password,
              password: true,
              enabled: !loading,
              action: TextInputAction.done,
              onSubmitted: submit,
              autofillHints: const [AutofillHints.password],
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Vui lòng nhập mật khẩu.';
                }
                if (utf8.encode(value).length > 72) {
                  return 'Mật khẩu tối đa 72 byte UTF-8.';
                }
                return null;
              },
            ),
            AuthError(message: error ?? widget.notice),
            AuthButton(label: 'Đăng nhập', loading: loading, onPressed: submit),
            const SizedBox(height: 16),
            Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text(
                  'Chưa có tài khoản?',
                  style: TextStyle(color: Color(0xff333333)),
                ),
                TextButton(
                  onPressed: loading
                      ? null
                      : () async {
                          password.clear();
                          final registered = await Navigator.of(context)
                              .push<bool>(
                                MaterialPageRoute(
                                  builder: (_) => RegisterScreen(
                                    onSubmit: widget.onRegister,
                                  ),
                                ),
                              );
                          if (context.mounted && registered == true) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text(
                                  'Tạo tài khoản thành công. Vui lòng đăng nhập.',
                                ),
                              ),
                            );
                          }
                        },
                  child: const Text(
                    'Đăng ký',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: Color(0xff111111),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}
