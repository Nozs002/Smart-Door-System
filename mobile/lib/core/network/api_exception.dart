/// Safe messages only; never include request bodies, tokens or server details.
class AuthFormException implements Exception {
  const AuthFormException(this.message);
  final String message;
}

class ApiException extends AuthFormException {
  const ApiException(super.message, {this.statusCode, this.errorCode});
  final int? statusCode;
  final String? errorCode;
  bool get unauthorized => statusCode == 401;
}
