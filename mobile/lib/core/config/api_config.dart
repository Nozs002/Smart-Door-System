import 'package:flutter/foundation.dart';

class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: kDebugMode
        ? 'http://10.0.2.2:8080'
        : 'https://api.example.invalid',
  );
}
