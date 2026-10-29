import 'package:smart_door_mobile/features/credentials/data/credential_models.dart';
import 'package:smart_door_mobile/features/credentials/data/credential_repository.dart';
import 'package:smart_door_mobile/features/doors/data/door_models.dart';

PageResult<T> testPage<T>(List<T> items, {int page = 0, int totalPages = 1}) =>
    PageResult(
      items: items,
      page: page,
      size: 20,
      totalElements: items.length,
      totalPages: totalPages,
    );

class FakeCredentialRepository implements CredentialRepository {
  final people = <CardholderView>[
    const CardholderView(id: 7, displayName: 'An', status: 'ACTIVE'),
  ];
  final cards = <RfidCredentialView>[];
  final pinChanges = <(int, String)>[];
  Object? pinFailure;
  final revoked = <(int, int)>[];
  final issued = <(int, int, String)>[];
  @override
  Future<PageResult<CardholderView>> listCardholders({int page = 0}) async =>
      testPage(List.of(people), page: page);
  @override
  Future<CardholderView> createCardholder(String displayName) async {
    final value = CardholderView(
      id: people.length + 8,
      displayName: displayName,
      status: 'ACTIVE',
    );
    people.add(value);
    return value;
  }

  @override
  Future<CardholderView> updateCardholder(
    int id,
    String displayName,
    String status,
  ) async {
    final value = CardholderView(
      id: id,
      displayName: displayName,
      status: status,
    );
    people[people.indexWhere((p) => p.id == id)] = value;
    return value;
  }

  @override
  Future<PageResult<RfidCredentialView>> listRfid(
    int doorId, {
    int page = 0,
  }) async => testPage(
    cards.where((card) => card.doorId == doorId).toList(),
    page: page,
  );
  @override
  Future<RfidCredentialView> issueRfid(
    int doorId,
    int cardholderId,
    String uid,
    DateTime? expiresAt,
  ) async {
    issued.add((doorId, cardholderId, uid));
    final value = RfidCredentialView(
      id: 31,
      doorId: doorId,
      cardholderId: cardholderId,
      cardholderName: people
          .firstWhere((p) => p.id == cardholderId)
          .displayName,
      uid: normalizeRfidUid(uid),
      status: 'ACTIVE',
      expiresAt: expiresAt,
    );
    cards.add(value);
    return value;
  }

  @override
  Future<RfidCredentialView> revokeRfid(int doorId, int credentialId) async {
    revoked.add((doorId, credentialId));
    final index = cards.indexWhere(
      (c) => c.id == credentialId && c.doorId == doorId,
    );
    final old = cards[index];
    final value = RfidCredentialView(
      id: old.id,
      doorId: old.doorId,
      cardholderId: old.cardholderId,
      cardholderName: old.cardholderName,
      uid: old.uid,
      status: 'REVOKED',
    );
    cards[index] = value;
    return value;
  }

  @override
  Future<DoorPinStatus> getPinStatus(int doorId) async => DoorPinStatus(
    doorId: doorId,
    configured: pinChanges.isNotEmpty,
    status: pinChanges.isEmpty ? 'NOT_CONFIGURED' : 'ACTIVE',
    version: pinChanges.length,
  );
  @override
  Future<DoorPinStatus> changePin(int doorId, String pin) async {
    if (pinFailure != null) throw pinFailure!;
    pinChanges.add((doorId, pin));
    return getPinStatus(doorId);
  }
}
