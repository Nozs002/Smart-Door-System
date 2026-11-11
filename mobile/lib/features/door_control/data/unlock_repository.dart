import '../../../core/api_client.dart';

class UnlockCommand {
  const UnlockCommand({
    required this.id,
    required this.status,
    this.action = 'UNLOCK',
  });
  factory UnlockCommand.fromJson(Map<String, dynamic> json) {
    final action = json['action'] as String? ?? 'UNLOCK';
    if (action != 'UNLOCK' && action != 'CLOSE') {
      throw const FormatException('Hành động điều khiển không hợp lệ.');
    }
    return UnlockCommand(
      id: json['id'] as String,
      status: json['status'] as String,
      action: action,
    );
  }
  final String id;
  final String status;
  final String action;
  bool get pending => ['PENDING', 'SENT', 'UNKNOWN'].contains(status);
}

abstract interface class UnlockRepository {
  Future<UnlockCommand?> latest(int doorId);
  Future<UnlockCommand> unlock(int doorId, String requestId);
  Future<UnlockCommand> closeDoor(int doorId, String requestId);
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
      _send(doorId, requestId, 'UNLOCK');
  @override
  Future<UnlockCommand> closeDoor(int doorId, String requestId) async =>
      _send(doorId, requestId, 'CLOSE');
  Future<UnlockCommand> _send(
    int doorId,
    String requestId,
    String action,
  ) async => UnlockCommand.fromJson(
    await client.request(
      'POST',
      _path(doorId),
      body: {'requestId': requestId, 'action': action},
    ) as Map<String, dynamic>,
  );
  @override
  Future<UnlockCommand> get(int doorId, String commandId) async =>
      UnlockCommand.fromJson(
        await client.request('GET', '${_path(doorId)}/$commandId')
            as Map<String, dynamic>,
      );
}
