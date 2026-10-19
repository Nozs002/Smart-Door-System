import 'package:flutter/material.dart';

import 'core/api_client.dart';
import 'features/doors/data/door_repository.dart';
import 'features/authentication/login_screen.dart';
import 'core/storage/session_store.dart';
import 'features/authentication/auth_api.dart';
import 'features/authentication/auth_session.dart';
import 'features/authentication/profile_screen.dart';

void main() {
  runApp(const SmartDoorApp());
}

class SmartDoorApp extends StatefulWidget {
  const SmartDoorApp({super.key, this.session});
  final AuthSession? session;

  @override
  State<SmartDoorApp> createState() => _SmartDoorAppState();
}

class _SmartDoorAppState extends State<SmartDoorApp> {
  late final AuthSession session;
  ApiClient? _doorClient;
  late final DoorRepository doorRepository;
  @override
  void initState() {
    super.initState();
    session =
        widget.session ??
        AuthSession(api: HttpAuthApi(), store: SecureSessionStore());
    doorRepository = RestDoorRepository(
      _doorClient = ApiClient(tokenProvider: session),
    );
    session.restore();
  }

  @override
  void dispose() {
    if (widget.session == null) session.dispose();
    _doorClient?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart Door',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: ListenableBuilder(
        listenable: session,
        builder: (context, _) {
          if (session.restoring) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          if (session.signedIn) {
            return ProfileScreen(
              session: session,
              doorRepository: doorRepository,
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
