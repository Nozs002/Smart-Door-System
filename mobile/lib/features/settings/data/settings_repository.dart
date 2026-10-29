import '../../../core/api_client.dart';
import 'settings_models.dart';

abstract interface class SettingsRepository {
  Future<DoorConfiguration> getConfiguration(int doorId);
  Future<DoorConfiguration> retrySync(int doorId);
  Future<DoorConfiguration> updateConfiguration(
    int doorId, {
    required int maxFailedAttempts,
    required int lockoutSeconds,
    required double temperatureThreshold,
    int? openTimeoutSeconds,
  });
}

class RestSettingsRepository implements SettingsRepository {
  RestSettingsRepository(this.client);
  final ApiClient client;

  @override
  Future<DoorConfiguration> getConfiguration(int doorId) async =>
      DoorConfiguration.fromJson(
        await client.request('GET', '/api/v1/doors/$doorId/configuration')
            as Map<String, dynamic>,
      );

  @override
  Future<DoorConfiguration> retrySync(int doorId) async =>
      DoorConfiguration.fromJson(
        await client.request('POST', '/api/v1/doors/$doorId/sync/retry')
            as Map<String, dynamic>,
      );

  @override
  Future<DoorConfiguration> updateConfiguration(
    int doorId, {
    required int maxFailedAttempts,
    required int lockoutSeconds,
    required double temperatureThreshold,
    int? openTimeoutSeconds,
  }) async => DoorConfiguration.fromJson(
    await client.request(
      'PUT',
      '/api/v1/doors/$doorId/configuration',
      body: {
        'maxFailedAttempts': maxFailedAttempts,
        'lockoutSeconds': lockoutSeconds,
        'temperatureThreshold': temperatureThreshold,
        'openTimeoutSeconds': openTimeoutSeconds,
      },
    ) as Map<String, dynamic>,
  );
}
