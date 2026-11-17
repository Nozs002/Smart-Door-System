import 'package:flutter/material.dart';

Future<bool> confirmSecurityMode(
  BuildContext context, {
  required bool enabled,
}) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          enabled ? 'Bật bảo mật và đóng cửa?' : 'Tắt bảo mật và giữ cửa mở?',
        ),
        content: Text(
          enabled
              ? 'Cửa sẽ đóng lại và yêu cầu thẻ/PIN. Đảm bảo lối đi trống. Báo nhiệt vẫn ưu tiên giữ cửa mở.'
              : 'Cửa sẽ mở và giữ mở, không tự đóng. Muốn đóng cửa, bạn cần bật lại chế độ bảo mật.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(enabled ? 'Bật bảo mật' : 'Giữ cửa mở'),
          ),
        ],
      ),
    ) ??
    false;
