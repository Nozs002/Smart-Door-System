import '../../../core/api_client.dart';

class UnlockCommand {
  const UnlockCommand({required this.id, required this.status});
  factory UnlockCommand.fromJson(Map<String, dynamic> json) =>
      UnlockCommand(id: json['id'] as String, status: json['status'] as String);
  final String id;
  final String status;
  bool get pending => ['PENDING', 'SENT', 'UNKNOWN'].contains(status);
}

abstract interface class UnlockRepository {
  Future<UnlockCommand?> latest(int doorId);
  Future<UnlockCommand> unlock(int doorId, String requestId);
  Future<UnlockCommand> get(int doorId, String commandId);
}

class RestUnlockRepository implements UnlockRepository {
  RestUnlockRepository(this.client);
  final ApiClient client;
  String _path(int id) => '/api/v1/doors/$id/commands';
  @override
  Future<UnlockCommand?> latest(int doorId) async {
    final json = await client.request('GET', '${_path(doorId)}/latest');
    return json == null
        ? null
        : UnlockCommand.fromJson(json as Map<String, dynamic>);
  }

  @override
  Future<UnlockCommand> unlock(int doorId, String requestId) async =>
      UnlockCommand.fromJson(
        await client.request(
          'POST',
          _path(doorId),
          body: {'requestId': requestId},
        ) as Map<String, dynamic>,
      );
  @override
  Future<UnlockCommand> get(int doorId, String commandId) async =>
      UnlockCommand.fromJson(
        await client.request('GET', '${_path(doorId)}/$commandId')
            as Map<String, dynamic>,
      );
}
