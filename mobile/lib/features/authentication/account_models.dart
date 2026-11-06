class AccountProfile {
  const AccountProfile({
    required this.id,
    required this.username,
    required this.displayName,
    required this.status,
    required this.createdAt,
  });
  final int id;
  final String username;
  final String displayName;
  final String status;
  final DateTime createdAt;

  factory AccountProfile.fromJson(Map<String, dynamic> json) {
    final id = json['id'];
    final username = json['username'];
    final name = json['displayName'];
    final status = json['status'];
    final created = json['createdAt'];
    if (id is! int ||
        id <= 0 ||
        username is! String ||
        username.isEmpty ||
        name is! String ||
        name.isEmpty ||
        status != 'ACTIVE' ||
        created is! String ||
        !created.endsWith('Z')) {
      throw const FormatException('Invalid account response');
    }
    return AccountProfile(
      id: id,
      username: username,
      displayName: name,
      status: status as String,
      createdAt: DateTime.parse(created),
    );
  }
}

class LoginSession {
  const LoginSession(this.token, this.expiresAt);
  final String token;
  final DateTime expiresAt;

  factory LoginSession.fromJson(
    Map<String, dynamic> json,
    DateTime requestedAt,
  ) {
    final token = json['accessToken'];
    final seconds = json['expiresIn'];
    if (token is! String ||
        token.isEmpty ||
        token.length > 16384 ||
        json['tokenType'] != 'Bearer' ||
        seconds is! int ||
        seconds <= 0 ||
        seconds > 31536000) {
      throw const FormatException('Invalid login response');
    }
    // Count from request start, not response receipt, to avoid extending TTL.
    return LoginSession(token, requestedAt.add(Duration(seconds: seconds)));
  }

  @override
  String toString() => 'LoginSession[redacted]';
}
