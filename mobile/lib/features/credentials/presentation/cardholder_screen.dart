import 'package:flutter/material.dart';

import '../../doors/presentation/door_widgets.dart';
import '../data/credential_models.dart';
import '../data/credential_repository.dart';

class CardholderScreen extends StatefulWidget {
  const CardholderScreen({
    required this.repository,
    required this.onSignInRequested,
    super.key,
  });
  final CredentialRepository repository;
  final VoidCallback onSignInRequested;
  @override
  State<CardholderScreen> createState() => _CardholderScreenState();
}

class _CardholderScreenState extends State<CardholderScreen> {
  final List<CardholderView> _items = [];
  Object? _error;
  bool _loading = true;
  bool _more = false;
  int _page = -1;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool append = false}) async {
    final request = ++_request;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await widget.repository.listCardholders(
        page: append ? _page + 1 : 0,
      );
      if (!mounted || request != _request) return;
      setState(() {
        if (!append) _items.clear();
        _items.addAll(result.items);
        _page = result.page;
        _more = result.hasNext;
      });
    } catch (error) {
      if (mounted && request == _request) setState(() => _error = error);
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _edit([CardholderView? existing]) async {
    final changed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) =>
          _CardholderDialog(repository: widget.repository, existing: existing),
    );
    if (changed == true && mounted) await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Người giữ thẻ'),
      actions: [
        IconButton(
          tooltip: 'Thêm người giữ thẻ',
          onPressed: _loading ? null : () => _edit(),
          icon: const Icon(Icons.person_add_outlined),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Người giữ thẻ là hồ sơ người được cấp RFID, không phải tài khoản đăng nhập App.',
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _loading ? null : () => _edit(),
            icon: const Icon(Icons.add),
            label: const Text('Thêm người giữ thẻ'),
          ),
          if (_loading)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (_error != null)
            FailurePanel(
              error: _error!,
              onRetry: _load,
              onSignInRequested: widget.onSignInRequested,
            ),
          if (!_loading && _items.isEmpty && _error == null)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Text('Bạn chưa tạo người giữ thẻ nào.'),
            ),
          for (final item in _items)
            Card(
              child: ListTile(
                leading: Icon(
                  item.status == 'ACTIVE'
                      ? Icons.person_outline
                      : Icons.person_off_outlined,
                ),
                title: Text(item.displayName),
                subtitle: Text(
                  item.status == 'ACTIVE' ? 'Đang hoạt động' : 'Đã vô hiệu hóa',
                ),
                trailing: const Icon(Icons.edit_outlined),
                onTap: _loading ? null : () => _edit(item),
              ),
            ),
          if (_more && !_loading)
            OutlinedButton(
              onPressed: () => _load(append: true),
              child: const Text('Xem thêm người giữ thẻ'),
            ),
        ],
      ),
    ),
  );
}

class _CardholderDialog extends StatefulWidget {
  const _CardholderDialog({required this.repository, this.existing});
  final CredentialRepository repository;
  final CardholderView? existing;
  @override
  State<_CardholderDialog> createState() => _CardholderDialogState();
}

class _CardholderDialogState extends State<_CardholderDialog> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.existing?.displayName);
  late String _status = widget.existing?.status ?? 'ACTIVE';
  bool _saving = false;
  Object? _error;
  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      if (widget.existing == null) {
        await widget.repository.createCardholder(_name.text);
      } else {
        await widget.repository.updateCardholder(
          widget.existing!.id,
          _name.text,
          _status,
        );
      }
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !_saving,
    child: AlertDialog(
      title: Text(
        widget.existing == null ? 'Thêm người giữ thẻ' : 'Sửa người giữ thẻ',
      ),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                key: const Key('cardholderName'),
                controller: _name,
                enabled: !_saving,
                maxLength: 100,
                decoration: const InputDecoration(labelText: 'Tên hiển thị'),
                validator: (value) => value == null || value.trim().isEmpty
                    ? 'Nhập tên người giữ thẻ.'
                    : null,
              ),
              if (widget.existing != null) ...[
                DropdownButtonFormField<String>(
                  key: const Key('cardholderStatus'),
                  initialValue: _status,
                  decoration: const InputDecoration(labelText: 'Trạng thái'),
                  items: const [
                    DropdownMenuItem(
                      value: 'ACTIVE',
                      child: Text('Đang hoạt động'),
                    ),
                    DropdownMenuItem(
                      value: 'DISABLED',
                      child: Text('Vô hiệu hóa'),
                    ),
                  ],
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _status = value!),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Vô hiệu hóa sẽ thu hồi các thẻ của người này. Kích hoạt lại hồ sơ không tự cấp lại thẻ. Theo dõi đồng bộ tại từng cửa.',
                ),
              ],
              if (_error != null)
                Text(
                  errorMessage(_error!),
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: Text(_saving ? 'Đang lưu…' : 'Lưu'),
        ),
      ],
    ),
  );
}
