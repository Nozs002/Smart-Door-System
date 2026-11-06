import '../../../core/api_client.dart';
import '../../doors/data/door_models.dart';
import 'credential_models.dart';

abstract interface class CredentialRepository {
  Future<PageResult<CardholderView>> listCardholders({int page = 0});
  Future<CardholderView> createCardholder(String displayName);
  Future<CardholderView> updateCardholder(
    int id,
    String displayName,
    String status,
  );
  Future<PageResult<RfidCredentialView>> listRfid(int doorId, {int page = 0});
  Future<RfidCredentialView> issueRfid(
    int doorId,
    int cardholderId,
    String uid,
    DateTime? expiresAt,
  );
  Future<RfidCredentialView> revokeRfid(int doorId, int credentialId);
  Future<DoorPinStatus> getPinStatus(int doorId);
  Future<DoorPinStatus> changePin(int doorId, String pin);
}

class RestCredentialRepository implements CredentialRepository {
  RestCredentialRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<CardholderView>> listCardholders({int page = 0}) async =>
      PageResult.fromJson(
        await client.request(
          'GET',
          '/api/v1/cardholders',
          query: {'page': '$page', 'size': '20'},
        ) as Map<String, dynamic>,
        CardholderView.fromJson,
      );

  @override
  Future<CardholderView> createCardholder(String displayName) async =>
      CardholderView.fromJson(
        await client.request(
          'POST',
          '/api/v1/cardholders',
          body: {'displayName': displayName.trim()},
        ) as Map<String, dynamic>,
      );

  @override
  Future<CardholderView> updateCardholder(
    int id,
    String displayName,
    String status,
  ) async => CardholderView.fromJson(
    await client.request(
      'PATCH',
      '/api/v1/cardholders/$id',
      body: {'displayName': displayName.trim(), 'status': status},
    ) as Map<String, dynamic>,
  );

  @override
  Future<PageResult<RfidCredentialView>> listRfid(
    int doorId, {
    int page = 0,
  }) async => PageResult.fromJson(
    await client.request(
      'GET',
      '/api/v1/doors/$doorId/rfid-credentials',
      query: {'page': '$page', 'size': '20'},
    ) as Map<String, dynamic>,
    RfidCredentialView.fromJson,
  );

  @override
  Future<RfidCredentialView> issueRfid(
    int doorId,
    int cardholderId,
    String uid,
    DateTime? expiresAt,
  ) async => RfidCredentialView.fromJson(
    await client.request(
      'POST',
      '/api/v1/doors/$doorId/rfid-credentials',
      body: {
        'cardholderId': cardholderId,
        'uid': normalizeRfidUid(uid),
        if (expiresAt != null) 'expiresAt': expiresAt.toUtc().toIso8601String(),
      },
    ) as Map<String, dynamic>,
  );

  @override
  Future<RfidCredentialView> revokeRfid(int doorId, int credentialId) async =>
      RfidCredentialView.fromJson(
        await client.request(
          'POST',
          '/api/v1/doors/$doorId/rfid-credentials/$credentialId/revoke',
        ) as Map<String, dynamic>,
      );

  @override
  Future<DoorPinStatus> getPinStatus(int doorId) async =>
      DoorPinStatus.fromJson(
        await client.request('GET', '/api/v1/doors/$doorId/pin')
            as Map<String, dynamic>,
      );

  @override
  Future<DoorPinStatus> changePin(int doorId, String pin) async =>
      DoorPinStatus.fromJson(
        await client.request(
          'PUT',
          '/api/v1/doors/$doorId/pin',
          body: {'pin': pin},
        ) as Map<String, dynamic>,
      );
}
