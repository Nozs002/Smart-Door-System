import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';

String readCode(String value, String kind) {
  final text = value.trim();
  final uri = Uri.tryParse(text);
  if (uri?.scheme == 'smartdoor') {
    if (uri!.host != kind || uri.pathSegments.length != 1) {
      throw const FormatException('QR không đúng loại mã cần nhập.');
    }
    return uri.pathSegments.single;
  }
  return text;
}

Future<String?> scanCode(BuildContext context) => Navigator.of(context)
    .push<String>(MaterialPageRoute(builder: (_) => const _ScannerScreen()));

class _ScannerScreen extends StatefulWidget {
  const _ScannerScreen();
  @override
  State<_ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<_ScannerScreen> {
  final controller = MobileScannerController(formats: [BarcodeFormat.qrCode]);
  bool finished = false;
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Quét QR')),
    body: Column(
      children: [
        Expanded(
          child: MobileScanner(
            controller: controller,
            errorBuilder: (_, _) => const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text(
                  'Không mở được camera. Quay lại để nhập mã thủ công.',
                ),
              ),
            ),
            onDetect: (capture) {
              final values = capture.barcodes
                  .map((b) => b.rawValue)
                  .whereType<String>();
              if (!finished && values.isNotEmpty) {
                finished = true;
                Navigator.pop(context, values.first);
              }
            },
          ),
        ),
        SafeArea(
          child: TextButton.icon(
            onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.keyboard),
            label: const Text('Nhập mã thủ công'),
          ),
        ),
      ],
    ),
  );
}

class CodeField extends StatelessWidget {
  const CodeField({
    super.key,
    required this.controller,
    required this.label,
    required this.kind,
    this.enabled = true,
  });
  final TextEditingController controller;
  final String label;
  final String kind;
  final bool enabled;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: TextFormField(
      controller: controller,
      enabled: enabled,
      autocorrect: false,
      decoration: InputDecoration(
        labelText: label,
        helperText: 'Quét QR hoặc nhập mã trên tem',
        border: const OutlineInputBorder(),
        suffixIcon: IconButton(
          tooltip: 'Quét QR',
          icon: const Icon(Icons.qr_code_scanner),
          onPressed: enabled
              ? () async {
                  final value = await scanCode(context);
                  if (value == null || !context.mounted) return;
                  try {
                    controller.text = readCode(value, kind);
                  } on FormatException catch (error) {
                    ScaffoldMessenger.of(context)
                        .showSnackBar(SnackBar(content: Text(error.message)));
                  }
                }
              : null,
        ),
      ),
      validator: (value) =>
          value == null || value.trim().isEmpty ? 'Vui lòng nhập mã.' : null,
    ),
  );
}

Future<void> showCode(
  BuildContext context,
  String title,
  String text,
  String qr,
) => showDialog<void>(
  context: context,
  builder: (_) => AlertDialog(
    title: Text(title),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          QrImageView(data: qr, size: 220),
          const SizedBox(height: 12),
          SelectableText(text),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Đóng'),
      ),
    ],
  ),
);
