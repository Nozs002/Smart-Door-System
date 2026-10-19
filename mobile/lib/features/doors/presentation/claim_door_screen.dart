import 'package:flutter/material.dart';

import '../data/door_models.dart';
import '../data/door_repository.dart';
import 'door_widgets.dart';

class ClaimDoorScreen extends StatefulWidget {
  const ClaimDoorScreen({required this.repository, super.key});

  final DoorRepository repository;

  @override
  State<ClaimDoorScreen> createState() => _ClaimDoorScreenState();
}

class _ClaimDoorScreenState extends State<ClaimDoorScreen> {
  final _form = GlobalKey<FormState>();
  final _code = TextEditingController();
  bool _saving = false;
  Object? _error;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_saving || !_form.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final door = await widget.repository.claimDoor(
        normalizeActivationCode(_code.text),
      );
      if (mounted) Navigator.of(context).pop(door);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Thêm cửa')),
    body: SafeArea(
      child: ContentWidth(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: _form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(Icons.key_outlined, size: 56),
                const SizedBox(height: 24),
                Text(
                  'Nhận quyền sở hữu cửa',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 12),
                const Text(
                  'Nhập mã kích hoạt được kỹ thuật viên bàn giao. '
                  'Mỗi mã dùng cho một bộ cửa và chỉ nhận quyền sở hữu một lần.',
                ),
                const SizedBox(height: 24),
                TextFormField(
                  key: const Key('activationCode'),
                  controller: _code,
                  enabled: !_saving,
                  autocorrect: false,
                  enableSuggestions: false,
                  textCapitalization: TextCapitalization.characters,
                  textInputAction: TextInputAction.done,
                  decoration: const InputDecoration(
                    labelText: 'Mã kích hoạt',
                    hintText: 'K7MP-4Q2D',
                    border: OutlineInputBorder(),
                  ),
                  validator: validateActivationCode,
                  onFieldSubmitted: (_) => _submit(),
                ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 16),
                    child: Text(
                      errorMessage(_error!),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                const SizedBox(height: 24),
                FilledButton.icon(
                  onPressed: _saving ? null : _submit,
                  icon: const Icon(Icons.add_home_outlined),
                  label: Text(_saving ? 'Đang xác nhận…' : 'Xác nhận thêm cửa'),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Nhận quyền sở hữu thành công chưa kích hoạt thiết bị. '
                  'Bạn có thể theo dõi quá trình thiết lập tại chi tiết cửa.',
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
