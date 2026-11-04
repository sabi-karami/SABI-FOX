import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'sabi_theme.dart';

// Brand components ported from the Android app's ui/components/SabiBrand.kt:
// the fox mark, the wordmark, the aurora background and the connect orb.

/// The SABI-FOX fox mark, drawn from the same polygons as the Android vector
/// drawable res/drawable/ic_sabifox_mark.xml (56 x 58 viewport).
class SabiMark extends StatelessWidget {
  const SabiMark({super.key, this.size = 40});

  final double size;

  @override
  Widget build(BuildContext context) {
    return SizedBox.square(
      dimension: size,
      child: const CustomPaint(painter: _SabiMarkPainter()),
    );
  }
}

class _SabiMarkPainter extends CustomPainter {
  const _SabiMarkPainter();

  static const List<(Color, List<double>)> _polygons = [
    (SabiPalette.mint, [4.0, 2.0, 19.0, 17.0, 37.0, 17.0, 52.0, 2.0, 51.0, 32.0, 28.0, 56.0, 5.0, 32.0]),
    (SabiPalette.violet, [4.0, 2.0, 19.0, 17.0, 12.0, 24.0]),
    (SabiPalette.violet, [52.0, 2.0, 37.0, 17.0, 44.0, 24.0]),
    (SabiPalette.night, [9.0, 11.0, 16.0, 18.0, 11.0, 22.0]),
    (SabiPalette.night, [47.0, 11.0, 40.0, 18.0, 45.0, 22.0]),
    (SabiPalette.ivory, [8.0, 32.0, 28.0, 38.0, 48.0, 32.0, 28.0, 54.0]),
    (SabiPalette.violet, [28.0, 19.0, 32.5, 26.0, 28.0, 33.0, 23.5, 26.0]),
    (SabiPalette.night, [14.5, 28.0, 19.0, 24.5, 23.5, 28.0, 19.0, 30.5]),
    (SabiPalette.night, [32.5, 28.0, 37.0, 24.5, 41.5, 28.0, 37.0, 30.5]),
    (SabiPalette.night, [24.5, 46.0, 31.5, 46.0, 28.0, 50.5]),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final scale = math.min(size.width / 56, size.height / 58);
    final dx = (size.width - 56 * scale) / 2;
    final dy = (size.height - 58 * scale) / 2;
    final paint = Paint()..isAntiAlias = true;
    for (final (color, points) in _polygons) {
      final path = Path()
        ..moveTo(dx + points[0] * scale, dy + points[1] * scale);
      for (var i = 2; i + 1 < points.length; i += 2) {
        path.lineTo(dx + points[i] * scale, dy + points[i + 1] * scale);
      }
      path.close();
      paint.color = color;
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _SabiMarkPainter oldDelegate) => false;
}

/// The mark on a soft aurora glow, for the splash and sign-in screens.
class SabiGlowMark extends StatelessWidget {
  const SabiGlowMark({super.key, this.size = 84});

  final double size;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: size * 1.6,
      height: size * 1.6,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: RadialGradient(
          colors: [
            scheme.primary.withValues(alpha: 0.24),
            scheme.tertiary.withValues(alpha: 0.10),
            scheme.primary.withValues(alpha: 0),
          ],
          stops: const [0.0, 0.55, 1.0],
        ),
      ),
      child: SabiMark(size: size),
    );
  }
}

/// "SABI FOX" set like the Android top bar: heavy SABI, light FOX in the
/// primary colour. The name is Latin, so it stays left-to-right in Persian.
class SabiWordmark extends StatelessWidget {
  const SabiWordmark({
    super.key,
    this.fontSize = 19,
    this.markSize = 28,
    this.showMark = true,
  });

  final double fontSize;
  final double markSize;
  final bool showMark;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Semantics(
        label: 'SABI-FOX',
        excludeSemantics: true,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (showMark) ...[
              SabiMark(size: markSize),
              SizedBox(width: markSize * 0.32),
            ],
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: 'SABI',
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      color: scheme.onSurface,
                    ),
                  ),
                  TextSpan(
                    text: '\u2009FOX',
                    style: TextStyle(
                      fontWeight: FontWeight.w400,
                      color: scheme.primary,
                    ),
                  ),
                ],
              ),
              maxLines: 1,
              style: TextStyle(
                fontFamily: sabiFontFamily,
                fontSize: fontSize,
                letterSpacing: 3,
                height: 1.15,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Night-sky gradient with two aurora bands, like the Android launcher
/// background; dark mode adds a faint, fixed star field.
class SabiBackdrop extends StatelessWidget {
  const SabiBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;
    final base = scheme.surface;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color.lerp(base, scheme.tertiary, 0.16) ?? base,
            Color.lerp(base, scheme.primary, 0.07) ?? base,
            base,
          ],
          stops: const [0.0, 0.38, 1.0],
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: IgnorePointer(
              child: RepaintBoundary(
                child: CustomPaint(
                  painter: _AuroraPainter(
                    violet: scheme.tertiary,
                    mint: scheme.primary,
                    stars: dark ? scheme.onSurface : null,
                    strength: dark ? 1.0 : 0.55,
                  ),
                ),
              ),
            ),
          ),
          child,
        ],
      ),
    );
  }
}

class _AuroraPainter extends CustomPainter {
  _AuroraPainter({
    required this.violet,
    required this.mint,
    required this.stars,
    required this.strength,
  });

  final Color violet;
  final Color mint;
  final Color? stars;
  final double strength;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    const bleed = 80.0;
    final band = Paint()
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 34);

    final top = Path()
      ..moveTo(-bleed, -bleed)
      ..lineTo(w + bleed, -bleed)
      ..lineTo(w + bleed, h * 0.30)
      ..cubicTo(w * 0.74, h * 0.21, w * 0.28, h * 0.40, -bleed, h * 0.33)
      ..close();
    band.color = violet.withValues(alpha: 0.17 * strength);
    canvas.drawPath(top, band);

    final bottom = Path()
      ..moveTo(-bleed, h + bleed)
      ..lineTo(w + bleed, h + bleed)
      ..lineTo(w + bleed, h * 0.80)
      ..cubicTo(w * 0.70, h * 0.89, w * 0.26, h * 0.71, -bleed, h * 0.85)
      ..close();
    band.color = mint.withValues(alpha: 0.11 * strength);
    canvas.drawPath(bottom, band);

    final starColor = stars;
    if (starColor == null) return;
    // A fixed seed keeps the sky identical between frames and launches.
    var seed = 0x5AB1F0;
    double next() {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed / 0x7fffffff;
    }

    final dot = Paint();
    for (var i = 0; i < 64; i++) {
      final x = next() * w;
      final y = next() * h * 0.62;
      final radius = 0.35 + next() * 0.9;
      dot.color = starColor.withValues(alpha: 0.10 + next() * 0.40);
      canvas.drawCircle(Offset(x, y), radius, dot);
    }
  }

  @override
  bool shouldRepaint(covariant _AuroraPainter oldDelegate) {
    return oldDelegate.violet != violet ||
        oldDelegate.mint != mint ||
        oldDelegate.stars != stars ||
        oldDelegate.strength != strength;
  }
}

/// Translucent "glass" card used across the redesigned screens.
class SabiCard extends StatelessWidget {
  const SabiCard({
    super.key,
    this.padding = const EdgeInsets.all(16),
    this.onTap,
    this.accent,
    required this.child,
  });

  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;

  /// Tints the fill and border, e.g. the error colour for a warning.
  final Color? accent;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final dark = theme.brightness == Brightness.dark;
    final base = dark ? scheme.surfaceContainer : scheme.surfaceContainerLowest;
    final tone = accent;
    final fill = tone == null
        ? base
        : Color.alphaBlend(tone.withValues(alpha: dark ? 0.12 : 0.08), base);
    final border = tone == null
        ? scheme.outlineVariant.withValues(alpha: dark ? 0.85 : 1.0)
        : tone.withValues(alpha: 0.45);
    final content = Padding(padding: padding, child: child);
    final tap = onTap;
    return Material(
      color: fill.withValues(alpha: dark ? 0.78 : 0.9),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: BorderSide(color: border),
      ),
      clipBehavior: Clip.antiAlias,
      child: tap == null ? content : InkWell(onTap: tap, child: content),
    );
  }
}

/// Small rounded label, e.g. the preview badge.
class SabiPill extends StatelessWidget {
  const SabiPill({super.key, required this.label, this.icon, this.color});

  final String label;
  final IconData? icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final tone = color ?? Theme.of(context).colorScheme.primary;
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: tone.withValues(alpha: 0.12),
        shape: StadiumBorder(
          side: BorderSide(color: tone.withValues(alpha: 0.45)),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 14, color: tone),
              const SizedBox(width: 6),
            ],
            Flexible(
              child: Text(
                label,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontFamily: sabiFontFamily,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  height: 1.3,
                  color: tone,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Rounded square icon tile used as a leading badge in cards.
class SabiIconBadge extends StatelessWidget {
  const SabiIconBadge({
    super.key,
    required this.icon,
    this.color,
    this.size = 38,
  });

  final IconData icon;
  final Color? color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final tone = color ?? Theme.of(context).colorScheme.primary;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: tone.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(size * 0.34),
        border: Border.all(color: tone.withValues(alpha: 0.28)),
      ),
      child: Icon(icon, size: size * 0.52, color: tone),
    );
  }
}

/// Two-letter country code tile. Windows does not render flag emoji and
/// only a few flags are bundled, so every location gets the same badge.
class SabiCountryBadge extends StatelessWidget {
  const SabiCountryBadge({super.key, this.countryCode, this.size = 42});

  final String? countryCode;
  final double size;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final code = (countryCode ?? '').trim().toUpperCase();
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(size * 0.34),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            scheme.primary.withValues(alpha: 0.24),
            scheme.tertiary.withValues(alpha: 0.24),
          ],
        ),
        border: Border.all(color: scheme.primary.withValues(alpha: 0.35)),
      ),
      child: code.length == 2
          ? Text(
              code,
              textDirection: TextDirection.ltr,
              style: TextStyle(
                fontFamily: sabiFontFamily,
                fontSize: size * 0.32,
                fontWeight: FontWeight.w800,
                letterSpacing: 0.5,
                color: scheme.onSurface,
              ),
            )
          : Icon(Icons.public_rounded, size: size * 0.5, color: scheme.primary),
    );
  }
}

/// Animated connect control ported from the Android SabiConnectOrb: a tick
/// dial, a rotating mint-to-violet ring, a pulsing halo and the power icon.
///
/// The ticker only runs while connecting or connected, and never when the
/// platform asks for reduced motion.
class SabiConnectOrb extends StatefulWidget {
  const SabiConnectOrb({
    super.key,
    required this.connected,
    required this.connecting,
    required this.ringColor,
    required this.label,
    required this.onPressed,
    this.size = 212,
  });

  final bool connected;
  final bool connecting;
  final Color ringColor;

  /// Accessible name and tooltip, e.g. "Connect".
  final String label;
  final VoidCallback onPressed;
  final double size;

  @override
  State<SabiConnectOrb> createState() => _SabiConnectOrbState();
}

class _SabiConnectOrbState extends State<SabiConnectOrb>
    with SingleTickerProviderStateMixin {
  late final _ticker = createTicker(_onTick);
  Duration _lastTick = Duration.zero;
  double _rotation = 0;
  double _pulseClock = 0;

  bool get _active => widget.connected || widget.connecting;

  void _onTick(Duration elapsed) {
    final seconds = (elapsed - _lastTick).inMicroseconds / 1e6;
    _lastTick = elapsed;
    final period = widget.connecting ? 1.4 : 9.0;
    setState(() {
      _rotation = (_rotation + seconds * 2 * math.pi / period) % (2 * math.pi);
      _pulseClock = (_pulseClock + seconds) % 3.6;
    });
  }

  void _syncTicker() {
    final reduceMotion = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    final run = _active && !reduceMotion;
    if (run && !_ticker.isActive) {
      _lastTick = Duration.zero;
      _ticker.start();
    } else if (!run && _ticker.isActive) {
      _ticker.stop();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncTicker();
  }

  @override
  void didUpdateWidget(covariant SabiConnectOrb oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncTicker();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    // 0.8 -> 1 -> 0.8 over 3.6 s, the Android 1.8 s reverse-repeat pulse.
    final pulse =
        0.8 + 0.2 * (0.5 - 0.5 * math.cos(2 * math.pi * _pulseClock / 3.6));
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: widget.connected ? 1.0 : 0.92),
      duration: const Duration(milliseconds: 520),
      curve: Curves.easeOutBack,
      builder: (context, scale, _) {
        return TweenAnimationBuilder<Color?>(
          tween: ColorTween(end: widget.ringColor),
          duration: const Duration(milliseconds: 420),
          builder: (context, animatedRing, _) {
            final ring = animatedRing ?? widget.ringColor;
            return Semantics(
              button: true,
              label: widget.label,
              child: Tooltip(
                message: widget.label,
                excludeFromSemantics: true,
                child: SizedBox.square(
                  dimension: widget.size,
                  child: Material(
                    type: MaterialType.transparency,
                    shape: const CircleBorder(),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      focusColor: ring.withValues(alpha: 0.22),
                      hoverColor: ring.withValues(alpha: 0.10),
                      splashColor: ring.withValues(alpha: 0.24),
                      highlightColor: Colors.transparent,
                      onTap: widget.onPressed,
                      child: RepaintBoundary(
                        child: CustomPaint(
                          painter: _OrbPainter(
                            ring: ring,
                            mint: scheme.primary,
                            violet: scheme.tertiary,
                            core: scheme.surfaceContainerHigh,
                            active: _active,
                            rotation: _rotation,
                            pulse: pulse,
                            scale: scale,
                          ),
                          child: Center(
                            child: Icon(
                              Icons.power_settings_new_rounded,
                              size: 58,
                              color: ring,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }
}

class _OrbPainter extends CustomPainter {
  _OrbPainter({
    required this.ring,
    required this.mint,
    required this.violet,
    required this.core,
    required this.active,
    required this.rotation,
    required this.pulse,
    required this.scale,
  });

  final Color ring;
  final Color mint;
  final Color violet;
  final Color core;
  final bool active;
  final double rotation;
  final double pulse;
  final double scale;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide / 2;

    final haloAlpha = active ? 0.30 * pulse : 0.10;
    final halo = Paint()
      ..shader = RadialGradient(
        colors: [ring.withValues(alpha: haloAlpha), ring.withValues(alpha: 0)],
      ).createShader(Rect.fromCircle(center: center, radius: radius));
    canvas.drawCircle(center, radius, halo);

    final tick = Paint()
      ..strokeWidth = 1.5
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 60; i++) {
      final major = i % 5 == 0;
      final angle = i * 2 * math.pi / 60;
      final direction = Offset(math.sin(angle), -math.cos(angle));
      tick.color = ring.withValues(alpha: major ? 0.6 : 0.18);
      canvas.drawLine(
        center + direction * (radius * 0.95),
        center + direction * (radius * (major ? 0.88 : 0.91)),
        tick,
      );
    }

    final ringAlpha = active ? 1.0 : 0.4;
    final ringRadius = radius * 0.78 * scale;
    final sweep = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 7
      ..strokeCap = StrokeCap.round
      ..shader = SweepGradient(
        colors: [
          mint.withValues(alpha: ringAlpha),
          violet.withValues(alpha: ringAlpha),
          ring.withValues(alpha: ringAlpha),
          mint.withValues(alpha: ringAlpha),
        ],
        transform: GradientRotation(rotation),
      ).createShader(Rect.fromCircle(center: center, radius: ringRadius));
    canvas.drawCircle(center, ringRadius, sweep);

    final coreRadius = radius * 0.68 * scale;
    canvas.drawCircle(center, coreRadius, Paint()..color = core);
    final glow = Paint()
      ..shader = RadialGradient(
        colors: [ring.withValues(alpha: 0.24), ring.withValues(alpha: 0)],
      ).createShader(Rect.fromCircle(center: center, radius: coreRadius));
    canvas.drawCircle(center, coreRadius, glow);
  }

  @override
  bool shouldRepaint(covariant _OrbPainter oldDelegate) {
    return oldDelegate.ring != ring ||
        oldDelegate.mint != mint ||
        oldDelegate.violet != violet ||
        oldDelegate.core != core ||
        oldDelegate.active != active ||
        oldDelegate.rotation != rotation ||
        oldDelegate.pulse != pulse ||
        oldDelegate.scale != scale;
  }
}
