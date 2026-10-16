import 'package:flutter/material.dart';

import 'core/access_token_provider.dart';
import 'core/api_client.dart';
import 'features/doors/data/door_repository.dart';
import 'features/doors/presentation/door_list_screen.dart';

void main() {
  runApp(const SmartDoorApp());
}

class SmartDoorApp extends StatefulWidget {
  const SmartDoorApp({
    this.tokenProvider = const SignedOutTokenProvider(),
    this.repository,
    this.onSignInRequested,
    super.key,
  });

  final AccessTokenProvider tokenProvider;
  final DoorRepository? repository;
  final VoidCallback? onSignInRequested;

  @override
  State<SmartDoorApp> createState() => _SmartDoorAppState();
}

class _SmartDoorAppState extends State<SmartDoorApp> {
  ApiClient? _client;
  late final DoorRepository _repository =
      widget.repository ??
      RestDoorRepository(
        _client = ApiClient(tokenProvider: widget.tokenProvider),
      );

  @override
  void dispose() {
    _client?.close();
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
      home: DoorListScreen(
        repository: _repository,
        onSignInRequested: widget.onSignInRequested,
      ),
    );
  }
}
