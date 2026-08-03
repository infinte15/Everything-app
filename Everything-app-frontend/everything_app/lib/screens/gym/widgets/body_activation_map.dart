import 'dart:math' as math;

import 'package:flutter/foundation.dart' show setEquals;
import 'package:flutter/material.dart';

import '../../../theme/lyfta_theme.dart';
import 'body_map_paths.dart';

/// Anatomische Körper-Grafik: jede Muskelfläche wird nach ihrer Belastung eingefärbt.
///
/// [activation] bildet den Muskel-Slug (`chest`, `lower back`, …) auf einen Wert
/// zwischen 0 und 1 ab - genau das Feld `share` aus `GET /api/sports/stats/muscles`.
/// Fehlende Muskeln gelten als untrainiert und bleiben grau.
class BodyActivationMap extends StatefulWidget {
  final Map<String, double> activation;
  final ValueChanged<String>? onMuscleTap;

  /// Startseite: Vorder- oder Rückansicht.
  final bool showBackInitially;

  /// Blendet die Umschalter aus, wenn beide Ansichten nebeneinander stehen.
  final bool showToggle;

  const BodyActivationMap({
    super.key,
    required this.activation,
    this.onMuscleTap,
    this.showBackInitially = false,
    this.showToggle = true,
  });

  @override
  State<BodyActivationMap> createState() => _BodyActivationMapState();
}

class _BodyActivationMapState extends State<BodyActivationMap> {
  late bool _showBack = widget.showBackInitially;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Expanded(
          child: _BodyView(
            activation: widget.activation,
            back: _showBack,
            onMuscleTap: widget.onMuscleTap,
          ),
        ),
        if (widget.showToggle) ...[
          const SizedBox(height: 12),
          _ViewToggle(
            showBack: _showBack,
            onChanged: (value) => setState(() => _showBack = value),
          ),
        ],
      ],
    );
  }
}

/// Eine einzelne Ansicht ohne Umschalter - für die Gegenüberstellung Vorne/Hinten.
class BodyActivationView extends StatelessWidget {
  final Map<String, double> activation;
  final bool back;
  final ValueChanged<String>? onMuscleTap;

  const BodyActivationView({
    super.key,
    required this.activation,
    required this.back,
    this.onMuscleTap,
  });

  @override
  Widget build(BuildContext context) => _BodyView(
        activation: activation,
        back: back,
        onMuscleTap: onMuscleTap,
      );
}

class _BodyView extends StatelessWidget {
  final Map<String, double> activation;
  final bool back;
  final ValueChanged<String>? onMuscleTap;

  const _BodyView({
    required this.activation,
    required this.back,
    this.onMuscleTap,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final size = fitBodySize(constraints);
        final painter = BodyMapPainter(activation: activation, back: back);

        return Center(
          child: SizedBox(
            width: size.width,
            height: size.height,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTapUp: onMuscleTap == null
                  ? null
                  : (details) {
                      final muscle = painter.muscleAt(details.localPosition, size);
                      if (muscle != null) onMuscleTap!(muscle);
                    },
              child: CustomPaint(
                painter: painter,
                size: size,
              ),
            ),
          ),
        );
      },
    );
  }

}

/// Hält das Seitenverhältnis der Figur, egal wie die Fläche aufgezogen wird.
Size fitBodySize(BoxConstraints constraints) {
  final maxHeight = constraints.hasBoundedHeight ? constraints.maxHeight : 320.0;
  final maxWidth = constraints.hasBoundedWidth ? constraints.maxWidth : 200.0;

  var height = maxHeight;
  var width = height * kBodyAspectRatio;
  if (width > maxWidth) {
    width = maxWidth;
    height = width / kBodyAspectRatio;
  }
  return Size(width, height);
}

class BodyMapPainter extends CustomPainter {
  final Map<String, double> activation;

  /// Wenn gesetzt, ersetzt die Zwei-Stufen-Einfärbung einer einzelnen Übung die
  /// Volumen-Rampe. Für die Miniatur-Figuren in Listen.
  final BodyHighlight? highlight;

  final bool back;

  /// Trennstriche zwischen den Flächen. Bei Miniaturen aus, dort frisst ein
  /// 1px-Strich eine 4px-Fläche auf.
  final bool outlined;

  final double smoothing;

  BodyMapPainter({
    this.activation = const {},
    this.highlight,
    required this.back,
    this.outlined = true,
    this.smoothing = kBodySmoothing,
  });

  static const Color _untrained = LyftaTheme.bodyBase;
  static const Color _neutral = LyftaTheme.bodyNeutral;

  Color get _untrainedColor => _untrained;

  List<BodyRegion> get _regions => back ? kBodyBackRegions : kBodyFrontRegions;

  /// Die Pfade sind jetzt kubisch und werden pro Anstrich *und* pro Treffertest
  /// gebraucht - einmal bauen reicht. Die Größe ist je Painter-Instanz fix.
  final Map<BodyRegion, List<Path>> _pathCache = {};
  Size? _cachedSize;

  @override
  void paint(Canvas canvas, Size size) {
    // Mittelgrau statt Hintergrundfarbe: der Körper ist hell, ein dunkler
    // Strich in Hintergrundfarbe würde ihn zerschneiden statt gliedern.
    final outline = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = LyftaTheme.bodyOutline;

    // Neutrale Teile zuerst, damit die Muskelflächen darüber liegen. Etwas
    // dunkler als die Muskeln - das gibt der Figur Tiefe.
    final neutralPaint = Paint()..color = _neutral;
    canvas.drawOval(
      Rect.fromCenter(
        center: Offset(kHeadCenter.dx * size.width, kHeadCenter.dy * size.height),
        width: kHeadRadius.dx * 2 * size.width,
        height: kHeadRadius.dy * 2 * size.height,
      ),
      neutralPaint,
    );
    for (final shape in kBodyNeutralShapes) {
      canvas.drawPath(_toPath(shape, size), neutralPaint);
    }

    for (final region in _regions) {
      final paint = Paint()..color = colorFor(region.muscle);
      for (final path in _pathsFor(region, size)) {
        canvas.drawPath(path, paint);
        if (outlined) canvas.drawPath(path, outline);
      }
    }
  }

  /// Für eine einzelne Übung: rot für beanspruchte, blau für unterstützende
  /// Muskeln. Sonst der helle Grundton bei 0 und volles Rot bei maximaler
  /// Belastung - schon ein einziger Satz hebt die Fläche sichtbar an, sonst
  /// wäre eine leichte Belastung nicht von "gar nicht" zu unterscheiden.
  Color colorFor(String muscle) {
    final marked = highlight;
    if (marked != null) {
      if (marked.primary.contains(muscle)) return LyftaTheme.musclePrimary;
      if (marked.secondary.contains(muscle)) return LyftaTheme.muscleSecondary;
      return _untrainedColor;
    }

    final share = (activation[muscle] ?? 0).clamp(0.0, 1.0);
    if (share <= 0) return _untrained;
    final t = 0.25 + 0.75 * share;
    return Color.lerp(_untrained, LyftaTheme.musclePrimary, t) ?? _untrained;
  }

  /// Welche Muskelfläche liegt unter dem Finger? Rückwärts geprüft, damit die
  /// zuletzt gezeichnete (oben liegende) Fläche gewinnt.
  String? muscleAt(Offset position, Size size) {
    for (final region in _regions.reversed) {
      for (final path in _pathsFor(region, size)) {
        if (path.contains(position)) return region.muscle;
      }
    }
    return null;
  }

  List<Path> _pathsFor(BodyRegion region, Size size) {
    if (_cachedSize != size) {
      _pathCache.clear();
      _cachedSize = size;
    }
    return _pathCache.putIfAbsent(region, () {
      final paths = <Path>[_toPath(region.points, size)];
      if (region.mirrored) {
        paths.add(_toPath(
          region.points.map((p) => Offset(1 - p.dx, p.dy)).toList(),
          size,
        ));
      }
      return paths;
    });
  }

  /// Geschlossenes Polygon als weiche Kurve.
  ///
  /// Centripetal Catmull-Rom (alpha = 0.5), umgesetzt in kubische Béziers. Die
  /// Kurve läuft *durch* jeden Originalpunkt, damit die von Hand gesetzte
  /// Silhouette erhalten bleibt - ein Verfahren über Kantenmittelpunkte würde
  /// die Vierecke sichtbar schrumpfen lassen. Die zentripetale (statt
  /// uniformen) Parametrisierung verhindert Spitzen und Selbstüberschneidungen,
  /// was hier zählt, weil die Kantenlängen stark schwanken.
  ///
  /// [Path.contains] prüft die tatsächlich gefüllte Fläche inklusive Kurven,
  /// der Treffertest in [muscleAt] funktioniert also unverändert weiter.
  Path _toPath(List<Offset> points, Size size) {
    final scaled = [
      for (final p in points) Offset(p.dx * size.width, p.dy * size.height),
    ];
    final n = scaled.length;
    final path = Path()..moveTo(scaled[0].dx, scaled[0].dy);

    if (n < 3 || smoothing <= 0) {
      for (var i = 1; i < n; i++) {
        path.lineTo(scaled[i].dx, scaled[i].dy);
      }
      path.close();
      return path;
    }

    Offset at(int i) => scaled[(i % n + n) % n];

    for (var i = 0; i < n; i++) {
      final p0 = at(i - 1);
      final p1 = at(i);
      final p2 = at(i + 1);
      final p3 = at(i + 2);

      // Zentripetale Knotenabstände: |Δ|^0.5, gegen Null abgesichert.
      final d1 = math.sqrt((p1 - p0).distance);
      final d2 = math.sqrt((p2 - p1).distance);
      final d3 = math.sqrt((p3 - p2).distance);
      final e1 = d1 < 1e-6 ? 1e-6 : d1;
      final e2 = d2 < 1e-6 ? 1e-6 : d2;
      final e3 = d3 < 1e-6 ? 1e-6 : d3;

      // Tangenten der nicht-uniformen Catmull-Rom-Form, auf das Segment p1->p2
      // normiert und über [smoothing] gedämpft.
      final t1 = ((p2 - p1) / e2 - (p0 - p1) / e1 - (p2 - p0) / (e1 + e2)) *
          e2 *
          smoothing *
          (2 / 3);
      final t2 = ((p1 - p2) / e2 - (p3 - p2) / e3 - (p1 - p3) / (e2 + e3)) *
          e2 *
          smoothing *
          (2 / 3);

      final c1 = p1 + t1;
      final c2 = p2 + t2;
      path.cubicTo(c1.dx, c1.dy, c2.dx, c2.dy, p2.dx, p2.dy);
    }

    path.close();
    return path;
  }

  @override
  bool shouldRepaint(covariant BodyMapPainter oldDelegate) =>
      oldDelegate.back != back ||
      oldDelegate.outlined != outlined ||
      oldDelegate.smoothing != smoothing ||
      !_sameHighlight(oldDelegate.highlight) ||
      !_sameActivation(oldDelegate.activation);

  bool _sameHighlight(BodyHighlight? other) {
    if (other == null || highlight == null) return other == highlight;
    return setEquals(other.primary, highlight!.primary) &&
        setEquals(other.secondary, highlight!.secondary);
  }

  bool _sameActivation(Map<String, double> other) {
    if (other.length != activation.length) return false;
    for (final entry in activation.entries) {
      if (other[entry.key] != entry.value) return false;
    }
    return true;
  }
}

class _ViewToggle extends StatelessWidget {
  final bool showBack;
  final ValueChanged<bool> onChanged;

  const _ViewToggle({required this.showBack, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _segment('Vorne', !showBack, () => onChanged(false)),
          _segment('Hinten', showBack, () => onChanged(true)),
        ],
      ),
    );
  }

  Widget _segment(String label, bool selected, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? LyftaTheme.surfaceHighlight : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          label,
          style: LyftaTheme.caption.copyWith(
            color: selected ? LyftaTheme.textPrimary : LyftaTheme.textSecondary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

/// Farbverlauf-Legende unter der Grafik.
class BodyActivationLegend extends StatelessWidget {
  const BodyActivationLegend({super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text('wenig', style: LyftaTheme.label),
        const SizedBox(width: 8),
        Container(
          width: 84,
          height: 6,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(3),
            gradient: const LinearGradient(
              colors: [LyftaTheme.bodyBase, LyftaTheme.musclePrimary],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Text('viel', style: LyftaTheme.label),
      ],
    );
  }
}
