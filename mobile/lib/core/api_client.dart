import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'access_token_provider.dart';
import 'config/api_config.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode, this.errorCode});

  final String message;
  final int? statusCode;
  final String? errorCode;

  bool get authenticationRequired => statusCode == 401;

  @override
  String toString() => message;
}

/// Android REST client. No token is persisted or printed by this feature.
class ApiClient {
  ApiClient({
    required this.tokenProvider,
    String baseUrl = ApiConfig.baseUrl,
    HttpClient? httpClient,
    this.timeout = const Duration(seconds: 20),
    this.onUnauthorized,
  }) : baseUri = Uri.parse(baseUrl),
       _httpClient = httpClient ?? HttpClient();

  final AccessTokenProvider tokenProvider;
  final Uri baseUri;
  final Duration timeout;
  final Future<void> Function()? onUnauthorized;
  final HttpClient _httpClient;

  Future<dynamic> request(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
  }) async {
    final token = await tokenProvider.accessToken();
    if (token == null || token.trim().isEmpty) {
      throw const ApiException(
        'Vui lòng đăng nhập để quản lý cửa.',
        statusCode: 401,
      );
    }
    HttpClientRequest? request;
    try {
      final uri = baseUri.resolve(path).replace(queryParameters: query);
      request = await _httpClient.openUrl(method, uri).timeout(timeout);
      request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
      request.headers.set(HttpHeaders.acceptHeader, 'application/json');
      request.followRedirects = false;
      if (body != null) {
        request.headers.contentType = ContentType.json;
        request.write(jsonEncode(body));
      }
      final response = await request.close().timeout(timeout);
      final content = await utf8.decoder.bind(response).join().timeout(timeout);
      if (response.statusCode == 401 &&
          await tokenProvider.accessToken() == token) {
        await onUnauthorized?.call();
      }
      dynamic decoded;
      try {
        decoded = content.isEmpty ? null : jsonDecode(content);
      } on FormatException {
        throw ApiException(
          response.statusCode == 401
              ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
              : 'Máy chủ trả về dữ liệu không hợp lệ.',
          statusCode: response.statusCode,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        final problem = decoded is Map<String, dynamic> ? decoded : null;
        throw ApiException(
          response.statusCode == 401
              ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
              : problem?['detail'] as String? ??
                    problem?['message'] as String? ??
                    'Không thể hoàn tất yêu cầu (${response.statusCode}).',
          statusCode: response.statusCode,
          errorCode: problem?['errorCode'] as String?,
        );
      }
      if (decoded is Map<String, dynamic> && decoded.containsKey('data')) {
        return decoded['data'];
      }
      throw const ApiException('Phản hồi không đúng định dạng của hệ thống.');
    } on TimeoutException {
      request?.abort();
      throw const ApiException('Máy chủ phản hồi quá lâu. Vui lòng thử lại.');
    } on SocketException {
      throw const ApiException(
        'Không kết nối được máy chủ. Kiểm tra mạng và thử lại.',
      );
    } on HttpException {
      throw const ApiException('Kết nối bị gián đoạn. Vui lòng thử lại.');
    }
  }

  void close() => _httpClient.close(force: true);
}
