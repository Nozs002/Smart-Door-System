import 'package:smart_door_mobile/core/storage/session_store.dart';
import 'package:smart_door_mobile/features/authentication/account_models.dart';
import 'package:smart_door_mobile/features/authentication/auth_api.dart';
import 'package:smart_door_mobile/features/authentication/auth_session.dart';

class MemorySessionStore implements SessionStore {
  String? value;
  @override
  Future<String?> read() async => value;
  @override
  Future<void> write(String value) async {
    this.value = value;
  }

  @override
  Future<void> clear() async {
    value = null;
  }
}

class FakeAuthApi implements AuthApi {
  Object? failure;
  String? registeredName;
  String? registeredUsername;
  String? receivedToken;
  int logoutCalls = 0;
  int passwordChangeCalls = 0;

  @override
  Future<void> logout(String token) async {
    receivedToken = token;
    logoutCalls++;
    if (failure != null) throw failure!;
  }

  @override
  Future<void> changePassword(
    String token,
    String currentPassword,
    String newPassword,
  ) async {
    receivedToken = token;
    passwordChangeCalls++;
    if (failure != null) throw failure!;
  }

  @override
  Future<void> register(
    String displayName,
    String username,
    String password,
  ) async {
    if (failure != null) throw failure!;
    registeredName = displayName;
    registeredUsername = username;
  }

  @override
  Future<LoginSession> login(String username, String password) async {
    if (failure != null) throw failure!;
    return LoginSession(
      'test-token',
      DateTime.now().toUtc().add(const Duration(minutes: 15)),
    );
  }

  @override
  Future<AccountProfile> profile(String token) async {
    receivedToken = token;
    if (failure != null) throw failure!;
    return AccountProfile(
      id: 1,
      username: registeredUsername ?? 'customer',
      displayName: registeredName ?? 'Khách hàng',
      status: 'ACTIVE',
      createdAt: DateTime.utc(2026, 10, 14),
    );
  }
}

AuthSession testSession({FakeAuthApi? api, MemorySessionStore? store}) =>
    AuthSession(
      api: api ?? FakeAuthApi(),
      store: store ?? MemorySessionStore(),
    );
