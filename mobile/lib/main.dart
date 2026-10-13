import 'package:flutter/material.dart';

void main() {
  runApp(const SmartDoorApp());
}

class SmartDoorApp extends StatelessWidget {
  const SmartDoorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart Door',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const Scaffold(body: Center(child: Text('Smart Door'))),
    );
  }
}
