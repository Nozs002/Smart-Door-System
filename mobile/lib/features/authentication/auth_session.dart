import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../../core/access_token_provider.dart';
import '../../core/network/api_exception.dart';
import '../../core/storage/session_store.dart';
import 'account_models.dart';
import 'auth_api.dart';

class AuthSession extends ChangeNotifier implements AccessTokenProvider {
  AuthSession({required this.api, required this.store});
  final AuthApi api;
  final SessionStore store;
  LoginSession? _session;
  Timer? _expiry;
  bool _disposed = false;
  bool restoring = true;
  String? notice;
  bool get signedIn => _session != null;

  @override
  Future<String?> accessToken() async {
    final current = _session;
    if (current == null) return null;
    if (!current.expiresAt.isAfter(DateTime.now())) {
      await expire();
      return null;
    }
    return current.token;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> restore() async {
    try {
      final saved = await store.read();
      if (_disposed) return;
      if (saved != null) {
        final json = jsonDecode(saved);
        if (json is! Map<String, dynamic> ||
            json['token'] is! String ||
            (json['token'] as String).isEmpty ||
            json['expiresAt'] is! String) {
          throw const FormatException('Invalid session');
        }
        final restored = LoginSession(
          json['token'] as String,
          DateTime.parse(json['expiresAt'] as String),
        );
        if (restored.expiresAt.isAfter(DateTime.now())) {
          _session = restored;
          _scheduleExpiry();
        } else {
          await expire();
        }
      }
    } catch (_) {
      _session = null;
      notice = 'Không thể khôi phục phiên an toàn. Vui lòng đăng nhập lại.';
      try {
        await store.clear();
      } catch (_) {
        /* Never fall back to plaintext. */
      }
    } finally {
      restoring = false;
      _notify();
    }
  }

  Future<void> register(String name, String username, String password) =>
      api.register(name, username, password);

  Future<void> login(String username, String password) async {
    final result = await api.login(username, password);
    if (_disposed) return;
    if (!result.expiresAt.isAfter(DateTime.now())) {
      throw const AuthFormException(
        'Phiên đăng nhập đã hết hạn. Vui lòng thử lại.',
      );
    }
    try {
      await store.write(
        jsonEncode({
          'token': result.token,
          'expiresAt': result.expiresAt.toIso8601String(),
        }),
      );
    } catch (_) {
      throw const AuthFormException(
        'Không thể lưu phiên an toàn trên thiết bị. Vui lòng thử lại.',
      );
    }
    if (_disposed) return;
    _session = result;
    notice = null;
    _scheduleExpiry();
    _notify();
  }

  Future<AccountProfile> profile() async {
    final current = _session;
    if (current == null || !current.expiresAt.isAfter(DateTime.now())) {
      await expire();
      throw const ApiException(
        'Phiên đã hết hạn. Vui lòng đăng nhập lại.',
        statusCode: 401,
      );
    }
    try {
      return await api.profile(current.token);
    } on ApiException catch (error) {
      if (error.unauthorized && identical(current, _session)) await expire();
      rethrow;
    }
  }

  Future<void> logout() async {
    String? warning;
    final current = _session;
    if (current != null && current.expiresAt.isAfter(DateTime.now())) {
      try {
        await api.logout(current.token);
      } on ApiException catch (error) {
        if (!error.unauthorized) {
          warning = 'Đã đăng xuất trên thiết bị. Máy chủ chưa xác nhận thu hồi phiên; phiên cũ sẽ hết hạn theo quy định.';
        }
      } catch (_) {
        warning =
            'Đã đăng xuất trên thiết bị. Máy chủ chưa xác nhận thu hồi phiên.';
      }
    }
    try {
      await store.clear();
    } catch (_) {
      throw const AuthFormException(
        'Không thể xóa phiên trên thiết bị. Vui lòng thử lại.',
      );
    }
    _expiry?.cancel();
    _session = null;
    notice = warning;
    _notify();
  }

  Future<void> changePassword(
    String currentPassword,
    String newPassword,
  ) async {
    final token = await accessToken();
    if (token == null) {
      throw const ApiException(
        'Phiên đã hết hạn. Vui lòng đăng nhập lại.',
        statusCode: 401,
      );
    }
    try {
      await api.changePassword(token, currentPassword, newPassword);
    } on ApiException catch (error) {
      if (error.unauthorized) await expire();
      rethrow;
    }
    await expire();
    notice = 'Đã đổi mật khẩu và kết thúc các phiên cũ. Vui lòng đăng nhập bằng mật khẩu mới.';
    _notify();
  }

  Future<void> expire() async {
    _expiry?.cancel();
    _session = null;
    notice = 'Phiên đã hết hạn hoặc không còn hợp lệ. Vui lòng đăng nhập lại.';
    try {
      await store.clear();
    } catch (_) {
      /* Expired sessions are rejected on restore. */
    }
    _notify();
  }

  void _scheduleExpiry() {
    _expiry?.cancel();
    final delay = _session!.expiresAt.difference(DateTime.now());
    _expiry = Timer(delay.isNegative ? Duration.zero : delay, () {
      unawaited(expire());
    });
  }

  @override
  void dispose() {
    _disposed = true;
    _expiry?.cancel();
    super.dispose();
  }
}
