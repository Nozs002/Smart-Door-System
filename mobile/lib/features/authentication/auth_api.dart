import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../../core/config/api_config.dart';
import '../../core/network/api_exception.dart';
import 'account_models.dart';

abstract interface class AuthApi {
  Future<void> register(String displayName, String username, String password);
  Future<LoginSession> login(String username, String password);
  Future<AccountProfile> profile(String token);
}

class HttpAuthApi implements AuthApi {
  HttpAuthApi({Uri? baseUri})
    : baseUri = baseUri ?? Uri.parse(ApiConfig.baseUrl);
  final Uri baseUri;

  @override
  Future<void> register(
    String displayName,
    String username,
    String password,
  ) async {
    final data = await _request(
      'POST',
      '/api/v1/auth/register',
      body: {
        'displayName': displayName,
        'username': username,
        'password': password,
      },
      expectedStatus: 201,
    );
    try {
      AccountProfile.fromJson(data);
    } on FormatException {
      throw const ApiException(
        'Phản hồi đăng ký không hợp lệ. Vui lòng đăng nhập để kiểm tra tài khoản.',
      );
    }
  }

  @override
  Future<LoginSession> login(String username, String password) async {
    final started = DateTime.now().toUtc();
    final data = await _request(
      'POST',
      '/api/v1/auth/login',
      body: {'username': username, 'password': password},
      expectedStatus: 200,
    );
    try {
      return LoginSession.fromJson(data, started);
    } on FormatException {
      throw const ApiException('Phản hồi đăng nhập không hợp lệ.');
    }
  }

  @override
  Future<AccountProfile> profile(String token) async {
    final data = await _request(
      'GET',
      '/api/v1/accounts/me',
      token: token,
      expectedStatus: 200,
    );
    try {
      return AccountProfile.fromJson(data);
    } on FormatException {
      throw const ApiException('Không thể đọc hồ sơ từ máy chủ.');
    }
  }

  Future<Map<String, dynamic>> _request(
    String method,
    String path, {
    Map<String, String>? body,
    String? token,
    required int expectedStatus,
  }) async {
    final localHost = const [
      '10.0.2.2',
      'localhost',
      '127.0.0.1',
    ].contains(baseUri.host);
    if (!baseUri.hasAuthority ||
        baseUri.userInfo.isNotEmpty ||
        (baseUri.scheme != 'https' &&
            !(kDebugMode && baseUri.scheme == 'http' && localHost))) {
      throw const ApiException('Địa chỉ máy chủ chưa được cấu hình an toàn.');
    }
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 10);
    Future<Map<String, dynamic>> send() async {
      final request = await client.openUrl(method, baseUri.resolve(path));
      request.followRedirects = false;
      request.headers.set(
        HttpHeaders.acceptHeader,
        'application/json, application/problem+json',
      );
      if (token != null) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
      }
      if (body != null) {
        request.headers.contentType = ContentType.json;
        request.add(utf8.encode(jsonEncode(body)));
      }
      final response = await request.close();
      final bytes = <int>[];
      await for (final chunk in response) {
        if (bytes.length + chunk.length > 65536) {
          throw const FormatException('Response too large');
        }
        bytes.addAll(chunk);
      }
      if (response.statusCode != expectedStatus) {
        String? code;
        try {
          final problem = jsonDecode(utf8.decode(bytes));
          if (problem is Map<String, dynamic> &&
              problem['errorCode'] is String) {
            code = problem['errorCode'] as String;
          }
        } on FormatException {
          /* Status still determines the safe error. */
        }
        throw ApiException(
          _message(
            response.statusCode,
            method == 'POST' && path.endsWith('register'),
          ),
          statusCode: response.statusCode,
          errorCode: code,
        );
      }
      final envelope = jsonDecode(utf8.decode(bytes));
      if (envelope is! Map<String, dynamic> ||
          envelope['data'] is! Map<String, dynamic>) {
        throw const FormatException('Invalid API response');
      }
      return envelope['data'] as Map<String, dynamic>;
    }

    try {
      return await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const ApiException(
        'Máy chủ phản hồi chậm. Kiểm tra kết nối rồi thử lại. Nếu vừa đăng ký, hãy thử đăng nhập trước.',
      );
    } on IOException {
      throw const ApiException(
        'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.',
      );
    } on FormatException {
      throw const ApiException(
        'Phản hồi máy chủ không hợp lệ. Vui lòng thử lại.',
      );
    } finally {
      client.close(force: true);
    }
  }

  String _message(int status, bool registering) => switch (status) {
    400 => 'Dữ liệu không hợp lệ. Vui lòng kiểm tra các trường đã nhập.',
    401 => 'Thông tin đăng nhập không hợp lệ hoặc phiên đã hết hạn.',
    409 when registering => 'Tên đăng nhập đã được sử dụng.',
    429 => 'Quá nhiều yêu cầu. Vui lòng chờ trước khi thử lại.',
    _ => 'Hệ thống chưa thể xử lý yêu cầu. Vui lòng thử lại.',
  };
}
