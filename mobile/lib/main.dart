import 'package:flutter/material.dart';

import 'features/door_control/data/unlock_repository.dart';
import 'features/access_logs/data/access_repository.dart';

import 'core/api_client.dart';
import 'features/doors/data/door_repository.dart';
import 'features/authentication/login_screen.dart';
import 'core/storage/session_store.dart';
import 'features/authentication/auth_api.dart';
import 'features/authentication/auth_session.dart';
import 'features/authentication/account_models.dart';
import 'features/authentication/profile_screen.dart';
import 'features/onboarding/technician_screen.dart';
import 'features/dashboard/dashboard_screen.dart';
import 'core/theme/app_theme.dart';
import 'features/settings/data/settings_repository.dart';
import 'features/alerts/data/alert_repository.dart';
import 'features/credentials/data/credential_repository.dart';
import 'features/notifications/data/notification_repository.dart';

void main() {
  runApp(const SmartDoorApp());
}

class SmartDoorApp extends StatefulWidget {
  const SmartDoorApp({
    super.key,
    this.session,
    this.repository,
    this.settingsRepository,
    this.alertRepository,
    this.credentialRepository,
    this.notificationRepository,
    this.unlockRepository,
    this.accessRepository,
    this.apiClient,
  });
  final DoorRepository? repository;
  final SettingsRepository? settingsRepository;
  final AlertRepository? alertRepository;
  final CredentialRepository? credentialRepository;
  final NotificationRepository? notificationRepository;
  final UnlockRepository? unlockRepository;
  final AccessRepository? accessRepository;
  final AuthSession? session;
  final ApiClient? apiClient;

  @override
  State<SmartDoorApp> createState() => _SmartDoorAppState();
}

class _SmartDoorAppState extends State<SmartDoorApp> {
  late final AuthSession session;
  ThemeMode _themeMode = ThemeMode.light;
  final _navigator = GlobalKey<NavigatorState>();
  ApiClient? _doorClient;
  Future<AccountProfile>? _accountProfile;
  late final DoorRepository doorRepository;
  SettingsRepository? settingsRepository;
  AlertRepository? alertRepository;
  CredentialRepository? credentialRepository;
  NotificationRepository? notificationRepository;
  UnlockRepository? unlockRepository;
  AccessRepository? accessRepository;
  @override
  void initState() {
    super.initState();
    session =
        widget.session ??
        AuthSession(api: HttpAuthApi(), store: SecureSessionStore());
    final client = _doorClient =
        widget.apiClient ??
        ApiClient(tokenProvider: session, onUnauthorized: session.expire);
    doorRepository = widget.repository ?? RestDoorRepository(client);
    accessRepository =
        widget.accessRepository ??
        (widget.repository == null ? RestAccessRepository(client) : null);
    unlockRepository =
        widget.unlockRepository ??
        (widget.repository == null ? RestUnlockRepository(client) : null);
    settingsRepository =
        widget.settingsRepository ??
        (widget.repository == null ? RestSettingsRepository(client) : null);
    alertRepository =
        widget.alertRepository ??
        (widget.repository == null ? RestAlertRepository(client) : null);
    credentialRepository =
        widget.credentialRepository ??
        (widget.repository == null ? RestCredentialRepository(client) : null);
    notificationRepository =
        widget.notificationRepository ??
        (widget.repository == null ? RestNotificationRepository(client) : null);
    session.addListener(_sessionChanged);
    session.restore();
  }

  void _sessionChanged() {
    if (!session.signedIn) {
      _accountProfile = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _navigator.currentState?.popUntil((route) => route.isFirst);
        }
      });
    }
  }

  @override
  void dispose() {
    session.removeListener(_sessionChanged);
    if (widget.session == null) session.dispose();
    if (widget.apiClient == null) _doorClient?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigator,
      title: 'Smart Door',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.theme(Brightness.light),
      darkTheme: AppTheme.theme(Brightness.dark),
      themeMode: _themeMode,
      home: ListenableBuilder(
        listenable: session,
        builder: (context, _) {
          if (session.restoring) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          if (session.signedIn) {
            return FutureBuilder<AccountProfile>(
              future: _accountProfile ??= session.profile(),
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Scaffold(
                    body: Center(child: CircularProgressIndicator()),
                  );
                }
                final role = snapshot.data?.role;
                if (snapshot.hasError ||
                    (role != 'CUSTOMER' && role != 'TECHNICIAN')) {
                  return Scaffold(
                    body: SafeArea(
                      child: Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Text(
                                'Không tải được thông tin tài khoản. Vui lòng thử lại.',
                              ),
                              const SizedBox(height: 16),
                              FilledButton(
                                onPressed: () =>
                                    setState(() => _accountProfile = null),
                                child: const Text('Thử lại'),
                              ),
                              TextButton(
                                onPressed: () async {
                                  try {
                                    await session.logout();
                                  } catch (_) {
                                    if (context.mounted) {
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(
                                            const SnackBar(
                                              content: Text(
                                                'Chưa đăng xuất được. Vui lòng thử lại.',
                                              ),
                                            ),
                                          );
                                    }
                                  }
                                },
                                child: const Text('Đăng xuất'),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                }
                if (role == 'TECHNICIAN') {
                  return TechnicianScreen(
                    client: _doorClient!,
                    onOpenProfile: () => Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (_) => ProfileScreen(session: session),
                      ),
                    ),
                  );
                }
                return DashboardScreen(
                  session: session,
                  repository: doorRepository,
                  settingsRepository: settingsRepository,
                  alertRepository: alertRepository,
                  credentialRepository: credentialRepository,
                  notificationRepository: notificationRepository,
                  unlockRepository: unlockRepository,
                  accessRepository: accessRepository,
                  onToggleTheme: () => setState(() {
                    _themeMode = _themeMode == ThemeMode.light
                        ? ThemeMode.dark
                        : ThemeMode.light;
                  }),
                );
              },
            );
          }
          return LoginScreen(
            onSubmit: session.login,
            onRegister: session.register,
            notice: session.notice,
          );
        },
      ),
    );
  }
}
