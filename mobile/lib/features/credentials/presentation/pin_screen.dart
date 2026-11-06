import 'package:flutter/material.dart';

import '../../doors/presentation/door_widgets.dart';
import '../data/credential_models.dart';
import '../data/credential_repository.dart';

class DoorPinScreen extends StatefulWidget {
  const DoorPinScreen({
    required this.repository,
    required this.doorId,
    required this.doorName,
    required this.onSignInRequested,
    super.key,
  });
  final CredentialRepository repository;
  final int doorId;
  final String doorName;
  final VoidCallback onSignInRequested;
  @override
  State<DoorPinScreen> createState() => _DoorPinScreenState();
}

class _DoorPinScreenState extends State<DoorPinScreen> {
  final _form = GlobalKey<FormState>();
  final _pin = TextEditingController();
  final _confirm = TextEditingController();
  DoorPinStatus? _status;
  Object? _error;
  bool _loading = true;
  bool _saving = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _pin.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final value = await widget.repository.getPinStatus(widget.doorId);
      if (mounted) setState(() => _status = value);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() => _saving = true);
    try {
      await widget.repository.changePin(widget.doorId, _pin.text);
      if (!mounted) return;
      _pin.clear();
      _confirm.clear();
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text(
              'Đã lưu PIN mới. Kiểm tra trạng thái đồng bộ trước khi sử dụng tại thiết bị.',
            ),
          ),
        );
      Navigator.of(context).pop();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(errorMessage(error))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('PIN · ${widget.doorName}')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (_loading)
              const Center(child: CircularProgressIndicator())
            else if (_error != null)
              FailurePanel(
                error: _error!,
                onRetry: _load,
                onSignInRequested: widget.onSignInRequested,
              )
            else ...[
              Text(
                _status!.configured
                    ? 'Đã thiết lập PIN · phiên bản ${_status!.version}'
                    : 'Cửa chưa có PIN',
              ),
              const SizedBox(height: 12),
              const Text(
                'PIN dùng chung riêng cho cửa này. PIN hiện tại không được hiển thị lại.',
              ),
              const SizedBox(height: 20),
              Form(
                key: _form,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      key: const Key('doorPin'),
                      controller: _pin,
                      obscureText: true,
                      enabled: !_saving,
                      enableSuggestions: false,
                      autocorrect: false,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'PIN mới (4–8 chữ số)',
                      ),
                      validator: (value) => validDoorPin(value ?? '')
                          ? null
                          : 'Nhập PIN gồm 4–8 chữ số.',
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      key: const Key('confirmDoorPin'),
                      controller: _confirm,
                      obscureText: true,
                      enabled: !_saving,
                      enableSuggestions: false,
                      autocorrect: false,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Nhập lại PIN mới',
                      ),
                      validator: (value) =>
                          value == _pin.text ? null : 'PIN xác nhận chưa khớp.',
                    ),
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _saving ? null : _save,
                      child: Text(_saving ? 'Đang lưu…' : 'Lưu PIN mới'),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    ),
  );
}
