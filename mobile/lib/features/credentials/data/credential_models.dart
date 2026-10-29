import '../../doors/data/door_models.dart';

class CardholderView {
  const CardholderView({
    required this.id,
    required this.displayName,
    required this.status,
    this.createdAt,
  });
  factory CardholderView.fromJson(Map<String, dynamic> json) => CardholderView(
    id: (json['id'] as num).toInt(),
    displayName: json['displayName'] as String,
    status: json['status'] as String,
    createdAt: parseUtcTime(json['createdAt']),
  );
  final int id;
  final String displayName;
  final String status;
  final DateTime? createdAt;
}

class RfidCredentialView {
  const RfidCredentialView({
    required this.id,
    required this.doorId,
    required this.cardholderId,
    required this.cardholderName,
    required this.uid,
    required this.status,
    this.issuedAt,
    this.expiresAt,
    this.revokedAt,
  });
  factory RfidCredentialView.fromJson(Map<String, dynamic> json) =>
      RfidCredentialView(
        id: (json['id'] as num).toInt(),
        doorId: (json['doorId'] as num).toInt(),
        cardholderId: (json['cardholderId'] as num).toInt(),
        cardholderName: json['cardholderName'] as String,
        uid: json['uid'] as String,
        status: json['status'] as String,
        issuedAt: parseUtcTime(json['issuedAt']),
        expiresAt: parseUtcTime(json['expiresAt']),
        revokedAt: parseUtcTime(json['revokedAt']),
      );
  final int id;
  final int doorId;
  final int cardholderId;
  final String cardholderName;
  final String uid;
  final String status;
  final DateTime? issuedAt;
  final DateTime? expiresAt;
  final DateTime? revokedAt;
}

class DoorPinStatus {
  const DoorPinStatus({
    required this.doorId,
    required this.configured,
    required this.status,
    required this.version,
    this.changedAt,
  });
  factory DoorPinStatus.fromJson(Map<String, dynamic> json) => DoorPinStatus(
    doorId: (json['doorId'] as num).toInt(),
    configured: json['configured'] as bool,
    status: json['status'] as String,
    version: (json['version'] as num).toInt(),
    changedAt: parseUtcTime(json['changedAt']),
  );
  final int doorId;
  final bool configured;
  final String status;
  final int version;
  final DateTime? changedAt;
}

String normalizeRfidUid(String value) =>
    value.replaceAll(RegExp(r'[\s:\-]'), '').toUpperCase();
bool validRfidUid(String value) {
  final normalized = normalizeRfidUid(value);
  return [8, 14, 20].contains(normalized.length) &&
      RegExp(r'^[0-9A-F]+$').hasMatch(normalized);
}

bool validDoorPin(String value) => RegExp(r'^\d{4,8}$').hasMatch(value);
