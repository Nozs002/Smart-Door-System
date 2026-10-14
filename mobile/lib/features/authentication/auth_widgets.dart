import 'dart:ui';

import 'package:flutter/material.dart';

/// Safe, user-facing error supplied by the future authentication adapter.
class AuthFormException implements Exception {
  const AuthFormException(this.message);
  final String message;
}

class AuthLayout extends StatelessWidget {
  const AuthLayout({super.key, required this.child, this.canGoBack = false});
  final Widget child;
  final bool canGoBack;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(
            child: Image.asset(
              'assets/images/auth_background.jpg',
              fit: BoxFit.cover,
            ),
          ),
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withValues(alpha: .28),
                    Colors.black.withValues(alpha: .08),
                  ],
                ),
              ),
            ),
          ),
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                return SingleChildScrollView(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 28,
                  ),
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight: (constraints.maxHeight - 56)
                          .clamp(0.0, double.infinity)
                          .toDouble(),
                    ),
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 430),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (canGoBack)
                              Align(
                                alignment: Alignment.centerLeft,
                                child: IconButton.filledTonal(
                                  onPressed: () => Navigator.maybePop(context),
                                  tooltip: 'Quay lại đăng nhập',
                                  style: IconButton.styleFrom(
                                    backgroundColor: Colors.white.withValues(
                                      alpha: .7,
                                    ),
                                  ),
                                  icon: const Icon(Icons.arrow_back_rounded),
                                ),
                              ),
                            const _Brand(),
                            const SizedBox(height: 32),
                            GlassPanel(child: child),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _Brand extends StatelessWidget {
  const _Brand();
  @override
  Widget build(BuildContext context) => Column(
    children: [
      GlassPanel(
        radius: 24,
        padding: const EdgeInsets.all(18),
        opacity: .2,
        child: const Icon(
          Icons.lock_outline_rounded,
          size: 36,
          color: Colors.white,
        ),
      ),
      const SizedBox(height: 16),
      const Text(
        'SmartLock',
        style: TextStyle(
          fontSize: 32,
          fontWeight: FontWeight.w800,
          color: Colors.white,
          letterSpacing: -.5,
          shadows: [Shadow(blurRadius: 10, color: Colors.black38)],
        ),
      ),
      const SizedBox(height: 8),
      const Text(
        'Hệ thống khóa thông minh',
        textAlign: TextAlign.center,
        style: TextStyle(
          color: Colors.white,
          fontSize: 15,
          shadows: [Shadow(blurRadius: 8, color: Colors.black38)],
        ),
      ),
    ],
  );
}

class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.radius = 36,
    this.padding = const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
    this.opacity = .7,
  });
  final Widget child;
  final double radius;
  final EdgeInsets padding;
  final double opacity;
  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(radius),
      boxShadow: const [
        BoxShadow(color: Colors.black12, blurRadius: 40, offset: Offset(0, 10)),
      ],
    ),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: opacity),
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(color: Colors.white.withValues(alpha: .4)),
          ),
          child: child,
        ),
      ),
    ),
  );
}

class AuthField extends StatefulWidget {
  const AuthField({
    super.key,
    required this.label,
    required this.controller,
    required this.validator,
    this.password = false,
    this.autofillHints,
    this.action = TextInputAction.next,
    this.onSubmitted,
    this.enabled = true,
  });
  final String label;
  final TextEditingController controller;
  final String? Function(String?) validator;
  final bool password;
  final Iterable<String>? autofillHints;
  final TextInputAction action;
  final VoidCallback? onSubmitted;
  final bool enabled;
  @override
  State<AuthField> createState() => _AuthFieldState();
}

class _AuthFieldState extends State<AuthField> {
  bool hidden = true;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 8, bottom: 8),
          child: Text(
            widget.label,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Color(0xff333333),
            ),
          ),
        ),
        TextFormField(
          controller: widget.controller,
          validator: widget.validator,
          enabled: widget.enabled,
          obscureText: widget.password && hidden,
          autocorrect: false,
          enableSuggestions: !widget.password,
          autofillHints: widget.autofillHints,
          textInputAction: widget.action,
          onFieldSubmitted: (_) => widget.onSubmitted?.call(),
          style: const TextStyle(fontSize: 15, color: Color(0xff111111)),
          decoration: InputDecoration(
            hintText: 'Nhập ${widget.label.toLowerCase()}',
            filled: true,
            fillColor: Colors.white.withValues(alpha: .6),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 20,
              vertical: 16,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: BorderSide(color: Colors.white.withValues(alpha: .8)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(20),
              borderSide: const BorderSide(color: Color(0xff111111)),
            ),
            errorMaxLines: 3,
            suffixIcon: widget.password
                ? IconButton(
                    tooltip: hidden
                        ? 'Hiện ${widget.label.toLowerCase()}'
                        : 'Ẩn ${widget.label.toLowerCase()}',
                    onPressed: widget.enabled
                        ? () => setState(() => hidden = !hidden)
                        : null,
                    icon: Icon(
                      hidden
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                      size: 20,
                    ),
                  )
                : null,
          ),
        ),
      ],
    ),
  );
}

class AuthButton extends StatelessWidget {
  const AuthButton({
    super.key,
    required this.label,
    required this.onPressed,
    required this.loading,
  });
  final String label;
  final VoidCallback onPressed;
  final bool loading;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: double.infinity,
    child: FilledButton(
      onPressed: loading ? null : onPressed,
      style: FilledButton.styleFrom(
        backgroundColor: const Color(0xff111111),
        foregroundColor: Colors.white,
        disabledBackgroundColor: const Color(0xff444444),
        disabledForegroundColor: Colors.white,
        padding: const EdgeInsets.all(18),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        elevation: 5,
        shadowColor: Colors.black26,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          if (loading) ...[
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 10),
          ],
          Flexible(
            child: Text(
              loading ? 'Đang xử lý…' : label,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
            ),
          ),
          if (!loading) ...[
            const SizedBox(width: 10),
            const Icon(Icons.arrow_forward_rounded, size: 20),
          ],
        ],
      ),
    ),
  );
}

class AuthError extends StatelessWidget {
  const AuthError({super.key, required this.message});
  final String? message;
  @override
  Widget build(BuildContext context) => message == null
      ? const SizedBox.shrink()
      : Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Semantics(
            liveRegion: true,
            child: Text(
              message!,
              style: const TextStyle(color: Color(0xff9b1c1c), fontSize: 13),
            ),
          ),
        );
}
