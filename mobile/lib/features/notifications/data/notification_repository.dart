import '../../../core/api_client.dart';
import '../../doors/data/door_models.dart';

class OwnerNotification {
  const OwnerNotification({
    required this.id,
    required this.alertId,
    required this.doorId,
    required this.title,
    required this.body,
    required this.createdAt,
    this.readAt,
  });
  factory OwnerNotification.fromJson(Map<String, dynamic> json) =>
      OwnerNotification(
        id: (json['id'] as num).toInt(),
        alertId: (json['alertId'] as num).toInt(),
        doorId: (json['doorId'] as num).toInt(),
        title: json['title'] as String,
        body: json['body'] as String,
        createdAt: parseUtcTime(json['createdAt'])!,
        readAt: parseUtcTime(json['readAt']),
      );
  final int id;
  final int alertId;
  final int doorId;
  final String title;
  final String body;
  final DateTime createdAt;
  final DateTime? readAt;
  bool get unread => readAt == null;
}

abstract interface class NotificationRepository {
  Future<PageResult<OwnerNotification>> list({
    int page = 0,
    bool unread = false,
  });
  Future<OwnerNotification> markRead(int id);
}

class RestNotificationRepository implements NotificationRepository {
  RestNotificationRepository(this.client);
  final ApiClient client;
  @override
  Future<PageResult<OwnerNotification>> list({
    int page = 0,
    bool unread = false,
  }) async => PageResult.fromJson(
    await client.request(
      'GET',
      '/api/v1/notifications',
      query: {'page': '$page', 'size': '20', if (unread) 'unread': 'true'},
    ) as Map<String, dynamic>,
    OwnerNotification.fromJson,
  );
  @override
  Future<OwnerNotification> markRead(int id) async =>
      OwnerNotification.fromJson(
        await client.request('POST', '/api/v1/notifications/$id/read')
            as Map<String, dynamic>,
      );
}
