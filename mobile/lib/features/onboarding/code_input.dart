import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class CodeField extends StatelessWidget {
  const CodeField({
    super.key,
    required this.controller,
    required this.label,
    this.enabled = true,
  });

  final TextEditingController controller;
  final String label;
  final bool enabled;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: TextFormField(
      controller: controller,
      enabled: enabled,
      autocorrect: false,
      enableSuggestions: false,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      validator: (value) =>
          value == null || value.trim().isEmpty ? 'Vui lòng nhập mã.' : null,
    ),
  );
}

class CopyCodeButton extends StatelessWidget {
  const CopyCodeButton({super.key, required this.code, required this.label});

  final String code;
  final String label;

  @override
  Widget build(BuildContext context) => TextButton.icon(
    icon: const Icon(Icons.copy_outlined),
    label: Text(label),
    onPressed: () async {
      await Clipboard.setData(ClipboardData(text: code));
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Đã sao chép mã.')));
    },
  );
}

Future<void> showCode(
  BuildContext context, {
  required String title,
  required String code,
  required String detail,
}) => showDialog<void>(
  context: context,
  builder: (dialogContext) => AlertDialog(
    title: Text(title),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(code),
          const SizedBox(height: 8),
          Text(detail),
        ],
      ),
    ),
    actions: [
      CopyCodeButton(code: code, label: 'Sao chép mã'),
      TextButton(
        onPressed: () => Navigator.pop(dialogContext),
        child: const Text('Đóng'),
      ),
    ],
  ),
);
