import 'package:flutter/material.dart';

import '../../../core/api_client.dart';

String statusLabel(String value) =>
    const {
      'UNCLAIMED': 'Chưa nhận quyền sở hữu',
      'SETUP_PENDING': 'Chờ thiết lập',
      'ACTIVE': 'Đang hoạt động',
      'DISABLED': 'Đã vô hiệu hóa',
      'PROVISIONED': 'Đã đăng ký',
      'RETIRED': 'Ngừng sử dụng',
      'ONLINE': 'Trực tuyến',
      'OFFLINE': 'Ngoại tuyến',
      'UNKNOWN': 'Chưa có heartbeat',
      'ACTIVATED': 'Kích hoạt',
      'REACTIVATED': 'Kích hoạt lại',
      'REPLACED': 'Thay thế',
    }[value] ??
    value;

String nodeLabel(String value) => switch (value) {
  'NODE_1' => 'Node 1 · Đọc thẻ và PIN',
  'NODE_2' => 'Node 2 · Điều khiển khóa',
  _ => value,
};

String displayTime(DateTime? value) {
  if (value == null) return 'Chưa có';
  final local = value.toLocal();
  String two(int part) => part.toString().padLeft(2, '0');
  return '${two(local.day)}/${two(local.month)}/${local.year} '
      '${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
}

String errorMessage(Object error) => error is ApiException
    ? error.message
    : 'Không tải được dữ liệu. Vui lòng thử lại.';

class StatusPill extends StatelessWidget {
  const StatusPill(this.status, {super.key});

  final String status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final Color foreground;
    final Color background;
    switch (status) {
      case 'ONLINE':
      case 'ACTIVE':
        foreground = const Color(0xff166534);
        background = const Color(0xffdcfce7);
      case 'OFFLINE':
      case 'DISABLED':
        foreground = scheme.error;
        background = scheme.errorContainer;
      default:
        foreground = scheme.onSurfaceVariant;
        background = scheme.surfaceContainerHighest;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Text(
        statusLabel(status),
        style: TextStyle(color: foreground, fontSize: 12),
      ),
    );
  }
}

class FailurePanel extends StatelessWidget {
  const FailurePanel({
    required this.error,
    required this.onRetry,
    this.onSignInRequested,
    super.key,
  });

  final Object error;
  final VoidCallback onRetry;
  final VoidCallback? onSignInRequested;

  @override
  Widget build(BuildContext context) {
    final needsLogin =
        error is ApiException && (error as ApiException).authenticationRequired;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              needsLogin ? Icons.lock_outline : Icons.cloud_off_outlined,
              size: 44,
            ),
            const SizedBox(height: 16),
            Text(errorMessage(error), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            if (needsLogin && onSignInRequested != null)
              FilledButton(
                onPressed: onSignInRequested,
                child: const Text('Đăng nhập'),
              ),
            TextButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }
}

class DetailLine extends StatelessWidget {
  const DetailLine(this.label, this.value, {super.key});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 7),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelMedium),
        const SizedBox(height: 3),
        SelectableText(value),
      ],
    ),
  );
}

class ContentWidth extends StatelessWidget {
  const ContentWidth({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 720),
      child: child,
    ),
  );
}
