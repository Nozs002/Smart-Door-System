import 'package:flutter/material.dart';

import 'core/api_client.dart';
import 'features/doors/data/door_repository.dart';
import 'features/authentication/login_screen.dart';
import 'core/storage/session_store.dart';
import 'features/authentication/auth_api.dart';
import 'features/authentication/auth_session.dart';
import 'features/dashboard/dashboard_screen.dart';
import 'core/theme/app_theme.dart';

void main() {
  runApp(const SmartDoorApp());
}

class SmartDoorApp extends StatefulWidget {
  const SmartDoorApp({super.key, this.session, this.repository});
  final DoorRepository? repository;
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
  @override
  void initState() {
    super.initState();
    session =
        widget.session ??
        AuthSession(api: HttpAuthApi(), store: SecureSessionStore());
    doorRepository =
        widget.repository ??
        RestDoorRepository(_doorClient = ApiClient(tokenProvider: session));
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
