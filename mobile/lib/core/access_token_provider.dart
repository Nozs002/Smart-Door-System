/// Integration boundary owned by the account/login feature.
///
/// Return a valid access token, refreshing it inside the account feature when
/// necessary. Never use an account ID supplied by a form as authentication.
abstract interface class AccessTokenProvider {
  Future<String?> accessToken();
}

class SignedOutTokenProvider implements AccessTokenProvider {
  const SignedOutTokenProvider();

  @override
  Future<String?> accessToken() async => null;
}
