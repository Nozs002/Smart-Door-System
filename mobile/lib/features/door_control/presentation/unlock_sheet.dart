import 'dart:async';
import 'dart:math';
import 'dart:ui';

import 'package:flutter/material.dart';

import '../../../core/api_client.dart';
import '../../doors/data/door_models.dart';
import '../data/unlock_repository.dart';

class UnlockSheet extends StatefulWidget {
  const UnlockSheet({
    super.key,
    required this.door,
    required this.repository,
    required this.onSettings,
    required this.onSignIn,
    this.operatingMode,
  });
  final DoorView door;
  final UnlockRepository repository;
  final VoidCallback onSettings;
  final VoidCallback onSignIn;
  final String? operatingMode;
  @override
  State<UnlockSheet> createState() => _UnlockSheetState();
}

class _UnlockSheetState extends State<UnlockSheet> {
  UnlockCommand? _command;
  String? _unresolvedId;
  String _requestedAction = 'UNLOCK';
  String? _error;
  bool _loading = true;
  double _slide = 0;
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    _timer?.cancel();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = _unresolvedId != null
          ? await widget.repository.get(widget.door.id, _unresolvedId!)
          : await widget.repository.latest(widget.door.id);
      if (!mounted) return;
      setState(() {
        _command = result;
        _unresolvedId = null;
      });
      if (result?.pending == true) {
        _timer = Timer(const Duration(seconds: 1), _load);
      }
    } catch (error) {
      _handleError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _handleError(Object error) {
    if (!mounted) return;
    if (error is ApiException && error.authenticationRequired) {
      Navigator.pop(context);
      widget.onSignIn();
      return;
    }
    setState(() => _error = error.toString());
  }

  String _uuid() {
    final random = Random.secure();
    final bytes = List.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<void> _unlock() => _sendCommand('UNLOCK');

  Future<void> _closeDoor() async {
    if (widget.operatingMode != 'SECURITY') return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Đóng cửa?'),
        content: const Text(
          'Đảm bảo lối đi không có người hoặc vật cản trước khi đóng.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận đóng'),
          ),
        ],
      ),
    );
    if (!mounted || confirmed != true) return;
    await _sendCommand('CLOSE');
  }

  Future<void> _sendCommand(String action) async {
    if (widget.door.status != 'ACTIVE' ||
        widget.operatingMode == 'AUTOMATIC' ||
        (action == 'CLOSE' && widget.operatingMode != 'SECURITY') ||
        _loading ||
        _error != null ||
        _command?.pending == true ||
        _unresolvedId != null) {
      return;
    }
    final id = _uuid();
    setState(() {
      _loading = true;
      _slide = 0;
      _unresolvedId = id;
      _requestedAction = action;
    });
    try {
      final result = action == 'CLOSE'
          ? await widget.repository.closeDoor(widget.door.id, id)
          : await widget.repository.unlock(widget.door.id, id);
      if (!mounted) return;
      setState(() {
        _command = result;
        _unresolvedId = null;
      });
      if (result.pending) _timer = Timer(const Duration(seconds: 1), _load);
    } catch (error) {
      // A network failure may follow an accepted command. Query its ID; never resend automatically.
      if (error is ApiException &&
          error.statusCode != null &&
          error.statusCode! >= 400 &&
          error.statusCode! < 500) {
        _unresolvedId = null;
      }
      _handleError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String get _status => widget.operatingMode == 'AUTOMATIC'
      ? 'Đã chọn chế độ giữ mở'
      : _unresolvedId != null
      ? (_loading
            ? (_requestedAction == 'CLOSE'
                  ? 'Đang gửi lệnh đóng cửa…'
                  : 'Đang gửi lệnh mở khóa…')
            : 'Chưa rõ lệnh đã được nhận hay chưa')
      : switch (_command?.status) {
          'PENDING' => 'Đang chuẩn bị gửi lệnh',
          'SENT' => 'Đã gửi, chờ thiết bị xác nhận',
          'UNKNOWN' => 'Chưa xác định kết quả gửi lệnh',
          'EXECUTED' =>
            _command?.action == 'CLOSE'
                ? 'Thiết bị xác nhận lệnh đóng cửa'
                : 'Thiết bị xác nhận lệnh mở khóa',
          'REJECTED' => 'Thiết bị từ chối lệnh',
          'FAILED' =>
            _command?.action == 'CLOSE'
                ? 'Không thể gửi lệnh đóng cửa'
                : 'Không thể gửi lệnh mở khóa',
          'TIMED_OUT' => 'Hết thời gian chờ xác nhận',
          _ => 'Lệnh gần nhất: Chưa có dữ liệu',
        };

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final enabled =
        !_loading &&
        _error == null &&
        _unresolvedId == null &&
        _command?.pending != true &&
        widget.operatingMode != 'AUTOMATIC' &&
        widget.door.status == 'ACTIVE';
    return BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
                decoration: BoxDecoration(
                  color:
                      (dark ? const Color(0xff20242c) : const Color(0xffeeeeed))
                          .withValues(alpha: .96),
                  borderRadius: BorderRadius.circular(36),
                  border: Border.all(color: Colors.white.withValues(alpha: .5)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        const SizedBox(width: 40),
                        Expanded(
                          child: Text(
                            widget.door.name,
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                        ),
                        IconButton(
                          tooltip: 'Đóng bảng điều khiển',
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    ShaderMask(
                      blendMode: BlendMode.dstIn,
                      shaderCallback: (bounds) => const LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black,
                          Colors.black,
                          Colors.transparent,
                        ],
                        stops: [0, .75, 1],
                      ).createShader(bounds),
                      child: Image.asset(
                        'assets/images/door_lock.webp',
                        height: 220,
                        fit: BoxFit.contain,
                        excludeFromSemantics: true,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      _status,
                      style: const TextStyle(
                        fontSize: 25,
                        fontWeight: FontWeight.w600,
                      ),
                      semanticsLabel: _status,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      widget.operatingMode == 'AUTOMATIC'
                          ? 'Bật lại bảo mật để đóng cửa. Xem cảm biến để biết trạng thái cửa thực tế.'
                          : widget.door.status != 'ACTIVE'
                          ? 'Cửa chưa hoạt động'
                          : 'Xác nhận lệnh không thay thế cảm biến trạng thái cửa.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        _unresolvedId != null
                            ? 'Chưa rõ lệnh đã được nhận hay chưa. Kiểm tra kết quả trước khi gửi lệnh khác.\n$_error'
                            : _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                      TextButton(
                        onPressed: _loading ? null : _load,
                        child: const Text('Kiểm tra lại kết quả'),
                      ),
                    ],
                    const SizedBox(height: 24),
                    Text(
                      _command?.pending == true
                          ? 'Đang chờ phản hồi từ Node 2…'
                          : 'Vuốt để gửi lệnh mở khóa',
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      height: 56,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: dark
                              ? const Color(0xff292e37)
                              : const Color(0xfff8f9fa),
                          borderRadius: BorderRadius.circular(30),
                          border: Border.all(
                            color: dark
                                ? Colors.white12
                                : const Color(0xffeeefec),
                          ),
                        ),
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            const ExcludeSemantics(
                              child: Text(
                                '› › ›',
                                style: TextStyle(
                                  color: Color(0xffc8cbd0),
                                  fontSize: 19,
                                  fontWeight: FontWeight.w700,
                                  letterSpacing: 2,
                                ),
                              ),
                            ),
                            Positioned(
                              right: 10,
                              child: ExcludeSemantics(
                                child: Container(
                                  width: 36,
                                  height: 36,
                                  decoration: BoxDecoration(
                                    color: dark
                                        ? const Color(0xff393d42)
                                        : const Color(0xfff3f1ec),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.lock_open_outlined,
                                    size: 16,
                                    color: Color(0xff92959d),
                                  ),
                                ),
                              ),
                            ),
                            Positioned.fill(
                              child: SliderTheme(
                                data: SliderTheme.of(context).copyWith(
                                  trackHeight: 0,
                                  trackShape: const _UnlockTrack(),
                                  thumbShape: const _LockThumb(),
                                  overlayShape: const RoundSliderOverlayShape(
                                    overlayRadius: 24,
                                  ),
                                  overlayColor: Colors.transparent,
                                  thumbColor: const Color(0xffdcfc76),
                                  disabledThumbColor: dark
                                      ? const Color(0xff667345)
                                      : const Color(0xffe6edcf),
                                ),
                                child: Slider(
                                  key: const Key('unlockSlider'),
                                  padding: EdgeInsets.zero,
                                  allowedInteraction:
                                      SliderInteraction.slideThumb,
                                  label: 'Mở khóa cửa',
                                  semanticFormatterCallback: (value) =>
                                      'Xác nhận mở khóa ${(value * 100).round()} phần trăm',
                                  value: _slide,
                                  onChanged: enabled
                                      ? (value) =>
                                            setState(() => _slide = value)
                                      : null,
                                  onChangeEnd: enabled
                                      ? (value) {
                                          if (value >= .95) {
                                            _unlock();
                                          } else {
                                            setState(() => _slide = 0);
                                          }
                                        }
                                      : null,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    OutlinedButton.icon(
                      key: const Key('closeDoorButton'),
                      onPressed: enabled && widget.operatingMode == 'SECURITY'
                          ? _closeDoor
                          : null,
                      icon: const Icon(Icons.door_front_door_outlined),
                      label: const Text('Đóng cửa'),
                    ),
                    if (_loading)
                      const Padding(
                        padding: EdgeInsets.only(top: 12),
                        child: Center(
                          child: SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.tonal(
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.all(18),
                  ),
                  onPressed: () {
                    Navigator.pop(context);
                    widget.onSettings();
                  },
                  child: const Text('Nhiều cài đặt hơn'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UnlockTrack extends RoundedRectSliderTrackShape {
  const _UnlockTrack();
  @override
  Rect getPreferredRect({
    required RenderBox parentBox,
    Offset offset = Offset.zero,
    required SliderThemeData sliderTheme,
    bool isEnabled = false,
    bool isDiscrete = false,
  }) => Rect.fromLTWH(
    offset.dx + 28,
    offset.dy + parentBox.size.height / 2,
    (parentBox.size.width - 56).clamp(0.0, double.infinity),
    0,
  );
}

class _LockThumb extends SliderComponentShape {
  const _LockThumb();
  @override
  Size getPreferredSize(bool isEnabled, bool isDiscrete) => const Size(48, 48);
  @override
  void paint(
    PaintingContext context,
    Offset center, {
    required Animation<double> activationAnimation,
    required Animation<double> enableAnimation,
    required bool isDiscrete,
    required TextPainter labelPainter,
    required RenderBox parentBox,
    required SliderThemeData sliderTheme,
    required TextDirection textDirection,
    required double value,
    required double textScaleFactor,
    required Size sizeWithOverflow,
  }) {
    context.canvas.drawCircle(
      center,
      24,
      Paint()
        ..color = Color.lerp(
          sliderTheme.disabledThumbColor,
          sliderTheme.thumbColor,
          enableAnimation.value,
        )!,
    );
    final icon = Icons.lock_outline;
    final painter = TextPainter(
      textDirection: textDirection,
      text: TextSpan(
        text: String.fromCharCode(icon.codePoint),
        style: TextStyle(
          fontFamily: icon.fontFamily,
          package: icon.fontPackage,
          fontSize: 17,
          color: enableAnimation.value > 0
              ? const Color(0xff202618)
              : const Color(0xff858a79),
        ),
      ),
    )..layout();
    painter.paint(
      context.canvas,
      center - Offset(painter.width / 2, painter.height / 2),
    );
    painter.dispose();
  }
}
