import 'dart:convert';

import 'package:flutter/material.dart';

import 'auth_widgets.dart';

typedef RegisterSubmit = Future<void> Function(
  String displayName,
  String username,
  String password,
);

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key, this.onSubmit});
  final RegisterSubmit? onSubmit;
  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final form = GlobalKey<FormState>();
  final name = TextEditingController();
  final username = TextEditingController();
  final password = TextEditingController();
  final confirmation = TextEditingController();
  bool loading = false;
  String? error;
  @override
  void dispose() {
    name.dispose();
    username.dispose();
    password.dispose();
    confirmation.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    if (loading || !form.currentState!.validate()) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      loading = true;
      error = null;
    });
    try {
      if (widget.onSubmit == null) {
        throw const AuthFormException(
          'Đăng ký chưa khả dụng. Vui lòng thử lại sau.',
        );
      }
      await widget.onSubmit!(name.text, username.text, password.text);
      if (!mounted) {
        return;
      }
      password.clear();
      confirmation.clear();
      Navigator.of(context).pop(true);
    } on AuthFormException catch (failure) {
      if (mounted) {
        setState(() => error = failure.message);
      }
    } catch (_) {
      if (mounted) {
        setState(() => error = 'Không thể tạo tài khoản. Vui lòng thử lại.');
      }
    } finally {
      if (mounted) {
        setState(() => loading = false);
      }
    }
  }

  String? validateName(String? value, String label) {
    if (value == null || value.trim().isEmpty) {
      return 'Vui lòng nhập $label.';
    }
    if (value.runes.length > 100) {
      return 'Tối đa 100 ký tự.';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) => AuthLayout(
    canGoBack: !loading,
    child: PopScope(
      canPop: !loading,
      child: AutofillGroup(
        child: Form(
          key: form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Tạo tài khoản',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: Color(0xff111111),
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Quản lý các cửa của bạn với một tài khoản.',
                style: TextStyle(fontSize: 14, color: Color(0xff555555)),
              ),
              const SizedBox(height: 24),
              AuthField(
                label: 'Tên hiển thị',
                controller: name,
                enabled: !loading,
                autofillHints: const [AutofillHints.name],
                validator: (value) => validateName(value, 'tên hiển thị'),
              ),
              AuthField(
                label: 'Tên đăng nhập',
                controller: username,
                enabled: !loading,
                autofillHints: const [AutofillHints.newUsername],
                validator: (value) => validateName(value, 'tên đăng nhập'),
              ),
              AuthField(
                label: 'Mật khẩu',
                controller: password,
                password: true,
                enabled: !loading,
                autofillHints: const [AutofillHints.newPassword],
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Vui lòng nhập mật khẩu.';
                  }
                  if (value.runes.length < 8) {
                    return 'Mật khẩu cần ít nhất 8 ký tự.';
                  }
                  if (utf8.encode(value).length > 72) {
                    return 'Mật khẩu tối đa 72 byte UTF-8.';
                  }
                  return null;
                },
              ),
              AuthField(
                label: 'Xác nhận mật khẩu',
                controller: confirmation,
                password: true,
                enabled: !loading,
                action: TextInputAction.done,
                onSubmitted: submit,
                validator: (value) => value == null || value.isEmpty
                    ? 'Vui lòng xác nhận mật khẩu.'
                    : value != password.text
                    ? 'Mật khẩu xác nhận chưa khớp.'
                    : null,
              ),
              AuthError(message: error),
              AuthButton(label: 'Đăng ký', loading: loading, onPressed: submit),
              const SizedBox(height: 16),
              Wrap(
                alignment: WrapAlignment.center,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  const Text(
                    'Đã có tài khoản?',
                    style: TextStyle(color: Color(0xff333333)),
                  ),
                  TextButton(
                    onPressed: loading
                        ? null
                        : () => Navigator.of(context).pop(),
                    child: const Text(
                      'Đăng nhập',
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
    ),
  );
}
