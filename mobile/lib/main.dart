import 'package:flutter/material.dart';

import 'core/api_client.dart';
import 'features/doors/data/door_repository.dart';
import 'features/authentication/login_screen.dart';
import 'core/storage/session_store.dart';
import 'features/authentication/auth_api.dart';
import 'features/authentication/auth_session.dart';
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
  });
  final DoorRepository? repository;
  final SettingsRepository? settingsRepository;
  final AlertRepository? alertRepository;
  final CredentialRepository? credentialRepository;
  final NotificationRepository? notificationRepository;
  final AuthSession? session;

  @override
  State<SmartDoorApp> createState() => _SmartDoorAppState();
}

class _SmartDoorAppState extends State<SmartDoorApp> {
  late final AuthSession session;
  ThemeMode _themeMode = ThemeMode.light;
  final _navigator = GlobalKey<NavigatorState>();
  ApiClient? _doorClient;
  late final DoorRepository doorRepository;
  SettingsRepository? settingsRepository;
  AlertRepository? alertRepository;
  CredentialRepository? credentialRepository;
  NotificationRepository? notificationRepository;
  @override
  void initState() {
    super.initState();
    session =
        widget.session ??
        AuthSession(api: HttpAuthApi(), store: SecureSessionStore());
    final client = _doorClient = ApiClient(
      tokenProvider: session,
      onUnauthorized: session.expire,
    );
    doorRepository = widget.repository ?? RestDoorRepository(client);
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
    _doorClient?.close();
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
            return DashboardScreen(
              session: session,
              repository: doorRepository,
              settingsRepository: settingsRepository,
              alertRepository: alertRepository,
              credentialRepository: credentialRepository,
              notificationRepository: notificationRepository,
              onToggleTheme: () => setState(() {
                _themeMode = _themeMode == ThemeMode.light
                    ? ThemeMode.dark
                    : ThemeMode.light;
              }),
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
