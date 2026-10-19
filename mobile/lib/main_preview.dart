import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'preview/preview_app.dart';

/// Manual UI preview only: no login, HTTP requests or persistent database.
void main() {
  if (!kDebugMode) {
    throw StateError('Preview chỉ được chạy ở chế độ debug.');
  }
  runApp(const PreviewApp());
}
