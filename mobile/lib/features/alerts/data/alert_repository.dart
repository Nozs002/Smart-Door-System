import '../../../core/api_client.dart';
import '../../doors/data/door_models.dart';
import 'alert_models.dart';

abstract interface class AlertRepository {
  Future<PageResult<AlertView>> listAlerts({
    int page = 0,
    int? doorId,
    String? status,
  });
  Future<AlertView> getAlert(int alertId);
  Future<AlertView> changeStatus(int alertId, String status);
}

class RestAlertRepository implements AlertRepository {
  RestAlertRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<AlertView>> listAlerts({
    int page = 0,
    int? doorId,
    String? status,
  }) async => PageResult.fromJson(
    await client.request(
          'GET',
          '/api/v1/alerts',
          query: {
            'page': '$page',
            'size': '20',
            if (doorId != null) 'doorId': '$doorId',
            if (status != null) 'status': status,
          },
        )
        as Map<String, dynamic>,
    AlertView.fromJson,
  );

  @override
  Future<AlertView> getAlert(int alertId) async => AlertView.fromJson(
    await client.request('GET', '/api/v1/alerts/$alertId')
        as Map<String, dynamic>,
  );

  @override
  Future<AlertView> changeStatus(int alertId, String status) async =>
      AlertView.fromJson(
        await client.request(
              'PATCH',
              '/api/v1/alerts/$alertId/status',
              body: {'status': status},
            )
            as Map<String, dynamic>,
      );
}
