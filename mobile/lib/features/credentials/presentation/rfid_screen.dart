import 'package:flutter/material.dart';

import '../../doors/presentation/door_widgets.dart';
import '../data/credential_models.dart';
import '../data/credential_repository.dart';

class RfidScreen extends StatefulWidget {
  const RfidScreen({
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
  State<RfidScreen> createState() => _RfidScreenState();
}

class _RfidScreenState extends State<RfidScreen> {
  final List<RfidCredentialView> _items = [];
  bool _loading = true;
  bool _more = false;
  int _page = -1;
  int _request = 0;
  Object? _error;
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
      final page = await widget.repository.listRfid(
        widget.doorId,
        page: append ? _page + 1 : 0,
      );
      if (!mounted || request != _request) return;
      setState(() {
        if (!append) _items.clear();
        _items.addAll(page.items);
        _page = page.page;
        _more = page.hasNext;
      });
    } catch (error) {
      if (mounted && request == _request) setState(() => _error = error);
    } finally {
      if (mounted && request == _request) setState(() => _loading = false);
    }
  }

  Future<void> _issue() async {
    final changed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _IssueRfidDialog(
        repository: widget.repository,
        doorId: widget.doorId,
      ),
    );
    if (changed == true && mounted) await _load();
  }

  Future<void> _revoke(RfidCredentialView card) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Thu hồi thẻ RFID?'),
        content: Text(
          'Thu hồi thẻ ${card.uid} tại ${widget.doorName}. Thay đổi cần được thiết bị xác nhận đồng bộ.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Thu hồi'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _loading = true);
    try {
      await widget.repository.revokeRfid(widget.doorId, card.id);
      if (mounted) await _load();
    } catch (error) {
      if (mounted) {
        setState(() => _loading = false);
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(errorMessage(error))));
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('Thẻ RFID · ${widget.doorName}')),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Thẻ được cấp và thu hồi riêng tại cửa đang chọn. Người giữ thẻ phải thuộc tài khoản của bạn.',
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _loading ? null : _issue,
            icon: const Icon(Icons.add_card),
            label: const Text('Cấp thẻ RFID'),
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
              child: Text('Cửa chưa có thẻ RFID được cấp.'),
            ),
          for (final card in _items)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      card.cardholderName,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 6),
                    SelectableText('UID: ${card.uid}'),
                    Text(
                      'Trạng thái: ${switch (card.status) {
                        'ACTIVE' => 'Có hiệu lực',
                        'REVOKED' => 'Đã thu hồi',
                        'EXPIRED' => 'Hết hạn',
                        _ => card.status,
                      }}',
                    ),
                    if (card.expiresAt != null)
                      Text('Hết hạn: ${displayTime(card.expiresAt)}'),
                    if (card.status == 'ACTIVE')
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton.icon(
                          onPressed: _loading ? null : () => _revoke(card),
                          icon: const Icon(Icons.block),
                          label: const Text('Thu hồi thẻ'),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          if (_more && !_loading)
            OutlinedButton(
              onPressed: () => _load(append: true),
              child: const Text('Xem thêm thẻ'),
            ),
        ],
      ),
    ),
  );
}

class _IssueRfidDialog extends StatefulWidget {
  const _IssueRfidDialog({required this.repository, required this.doorId});
  final CredentialRepository repository;
  final int doorId;
  @override
  State<_IssueRfidDialog> createState() => _IssueRfidDialogState();
}

class _IssueRfidDialogState extends State<_IssueRfidDialog> {
  final _form = GlobalKey<FormState>();
  final _uid = TextEditingController();
  final List<CardholderView> _people = [];
  int? _selected;
  DateTime? _expires;
  bool _loading = true;
  bool _saving = false;
  Object? _error;
  @override
  void initState() {
    super.initState();
    _loadPeople();
  }

  @override
  void dispose() {
    _uid.dispose();
    super.dispose();
  }

  Future<void> _loadPeople() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final people = <CardholderView>[];
      var page = 0;
      while (true) {
        final result = await widget.repository.listCardholders(page: page++);
        people.addAll(result.items.where((p) => p.status == 'ACTIVE'));
        if (!result.hasNext) break;
      }
      if (mounted) {
        setState(() {
          _people
            ..clear()
            ..addAll(people);
        });
      }
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickExpiry() async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: _expires ?? now.add(const Duration(days: 1)),
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 10),
    );
    if (date != null && mounted) {
      setState(
        () => _expires = DateTime(date.year, date.month, date.day, 23, 59, 59),
      );
    }
  }

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.repository.issueRfid(
        widget.doorId,
        _selected!,
        _uid.text,
        _expires,
      );
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
      title: const Text('Cấp thẻ RFID'),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (_loading)
                const Center(child: CircularProgressIndicator())
              else if (_people.isEmpty)
                const Text(
                  'Chưa có người giữ thẻ đang hoạt động. Hãy tạo hồ sơ trong mục Người giữ thẻ trước.',
                ),
              if (_people.isNotEmpty) ...[
                DropdownButtonFormField<int>(
                  key: const Key('rfidCardholder'),
                  initialValue: _selected,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Người giữ thẻ'),
                  items: [
                    for (final person in _people)
                      DropdownMenuItem(
                        value: person.id,
                        child: Text(person.displayName),
                      ),
                  ],
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _selected = value),
                  validator: (value) =>
                      value == null ? 'Chọn người giữ thẻ.' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  key: const Key('rfidUid'),
                  controller: _uid,
                  enabled: !_saving,
                  decoration: const InputDecoration(
                    labelText: 'UID thẻ',
                    hintText: 'A1B2C3D4',
                  ),
                  validator: (value) => validRfidUid(value ?? '')
                      ? null
                      : 'UID cần 8, 14 hoặc 20 ký tự hex.',
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: _saving ? null : _pickExpiry,
                  icon: const Icon(Icons.event),
                  label: Text(
                    _expires == null
                        ? 'Không giới hạn ngày hết hạn'
                        : 'Hết hạn: ${displayTime(_expires)}',
                  ),
                ),
                if (_expires != null)
                  TextButton(
                    onPressed: _saving
                        ? null
                        : () => setState(() => _expires = null),
                    child: const Text('Bỏ ngày hết hạn'),
                  ),
                const Text(
                  'Sau khi cấp, kiểm tra trạng thái đồng bộ PIN/RFID của cửa.',
                ),
              ],
              if (_error != null) ...[
                Text(
                  errorMessage(_error!),
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
                if (_people.isEmpty)
                  TextButton(
                    onPressed: _loading ? null : _loadPeople,
                    child: const Text('Thử tải lại'),
                  ),
              ],
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
          onPressed: _saving || _loading || _people.isEmpty ? null : _save,
          child: Text(_saving ? 'Đang cấp…' : 'Cấp thẻ'),
        ),
      ],
    ),
  );
}
