import 'package:flutter/material.dart';

import '../doors/data/door_models.dart';

/// Dashboard reference layout. Lifecycle never implies physical lock state.
class DoorOverviewCard extends StatelessWidget {
  const DoorOverviewCard({super.key, required this.door, required this.onTap});
  final DoorView door;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final foreground = dark ? const Color(0xffe2e8f0) : const Color(0xff1c1c1e);
    final muted = dark ? const Color(0xffa3adbd) : const Color(0xff8e8e93);
    return LayoutBuilder(
      builder: (context, constraints) {
        return Material(
          color: dark ? const Color(0xff111827) : const Color(0xffeeedeb),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
            side: BorderSide(
              color: Colors.white.withValues(alpha: dark ? .12 : .7),
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 240),
              child: Stack(
                fit: StackFit.passthrough,
                children: [
                  Positioned(
                    right: -30,
                    top: 0,
                    bottom: 0,
                    width: constraints.maxWidth * .65,
                    child: ShaderMask(
                      blendMode: BlendMode.dstIn,
                      shaderCallback: (bounds) => const LinearGradient(
                        colors: [Colors.transparent, Colors.black],
                        stops: [0, .4],
                      ).createShader(bounds),
                      child: Image.asset(
                        'assets/images/door_lock.webp',
                        fit: BoxFit.cover,
                        excludeFromSemantics: true,
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        FractionallySizedBox(
                          widthFactor: .76,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                door.name,
                                style: TextStyle(
                                  color: foreground,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w700,
                                  height: 1.2,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Padding(
                                    padding: const EdgeInsets.only(top: 2),
                                    child: Icon(
                                      Icons.lock_outline,
                                      size: 12,
                                      color: muted,
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  Expanded(
                                    child: Text(
                                      'Lệnh gần nhất: Chưa có dữ liệu',
                                      style: TextStyle(
                                        color: muted,
                                        fontSize: 13,
                                        height: 1.3,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 5),
                              Text(
                                'Trạng thái vật lý chưa được xác nhận',
                                style: TextStyle(
                                  color: muted,
                                  fontSize: 11,
                                  height: 1.35,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 48),
                        FractionallySizedBox(
                          widthFactor: .66,
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Expanded(
                                child: _Sensor(
                                  label: 'Nhiệt độ',
                                  color: foreground,
                                  muted: muted,
                                ),
                              ),
                              const SizedBox(width: 16),
                              Expanded(
                                child: _Sensor(
                                  label: 'Độ ẩm',
                                  color: foreground,
                                  muted: muted,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _Sensor extends StatelessWidget {
  const _Sensor({
    required this.label,
    required this.color,
    required this.muted,
  });
  final String label;
  final Color color;
  final Color muted;
  @override
  Widget build(BuildContext context) => Tooltip(
    message: 'Chưa có dữ liệu $label',
    child: Semantics(
      label: label,
      value: 'Chưa có dữ liệu',
      excludeSemantics: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: TextStyle(color: muted, fontSize: 11)),
          const SizedBox(height: 4),
          Text(
            '—',
            style: TextStyle(
              color: color,
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    ),
  );
}
