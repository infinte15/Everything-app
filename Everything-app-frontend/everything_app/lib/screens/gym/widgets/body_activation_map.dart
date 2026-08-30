import 'dart:typed_data';

import 'package:flutter/foundation.dart' show mapEquals;
import 'package:flutter/material.dart';

import '../../../theme/lyfta_theme.dart';
import 'body_map_paths.dart';

/// Anatomische Körper-Grafik: jede Muskelfläche wird nach ihrer Belastung eingefärbt.
///
/// [activation] bildet den Muskel-Slug des Backends (`chest`, `lower back`, …) auf einen Wert
/// zwischen 0 und 1 ab - genau das Feld `share` aus `GET /api/sports/stats/muscles`.
/// Fehlende Muskeln gelten als untrainiert und bleiben grau.
class BodyActivationMap extends StatefulWidget {
  final Map<String, double> activation;

  /// Bekommt den Backend-Slug, nicht den der Grafik - Aufrufer zeigen damit Namen und
  /// Zahlen an, die aus derselben Quelle stammen wie die Karte selbst.
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
          child: BodyActivationView(
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
  Widget build(BuildContext context) {
    return BodyFigure(
      back: back,
      activation: activation,
      onMuscleTap: onMuscleTap,
    );
  }
}

/// Zeichnet eine Ansicht der Figur, sobald die Geometrie geladen ist.
///
/// Vorher steht ein Platzhalter derselben Größe da statt eines Spinners: die Karte hat ihre
/// Höhe schon, und ein Ladekringel, der nach 20 ms verschwindet, ist nur Unruhe.
class BodyFigure extends StatefulWidget {
  final bool back;

  /// Volumen-Rampe (Backend-Slugs auf 0..1).
  final Map<String, double> activation;

  /// Zwei-Stufen-Einfärbung einer einzelnen Übung. Schlägt [activation].
  final BodyHighlight? highlight;

  final ValueChanged<String>? onMuscleTap;

  /// Trennstriche zwischen den Flächen. Bei Miniaturen aus, dort frisst ein
  /// 1px-Strich eine 4px-Fläche auf.
  final bool outlined;

  /// 1 = ganze Figur, > 1 = Ausschnitt um die beanspruchten Muskeln.
  final double zoom;

  const BodyFigure({
    super.key,
    required this.back,
    this.activation = const {},
    this.highlight,
    this.onMuscleTap,
    this.outlined = true,
    this.zoom = 1.0,
  });

  @override
  State<BodyFigure> createState() => _BodyFigureState();
}

class _BodyFigureState extends State<BodyFigure> {
  @override
  void initState() {
    super.initState();
    BodyMapGeometry.ensureLoaded();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<BodyGeometry?>(
      valueListenable: BodyMapGeometry.geometry,
      builder: (context, geometry, _) {
        if (geometry == null) return const SizedBox.expand();
        final view = geometry.view(back: widget.back);

        return LayoutBuilder(
          builder: (context, constraints) {
            final box = fitBodySize(constraints, aspectRatio: view.aspectRatio);
            final painter = BodyMapPainter(
              view: view,
              activation: widget.activation,
              highlight: widget.highlight,
              outlined: widget.outlined,
            );

            if (widget.zoom <= 1.0) {
              return Center(
                child: SizedBox.fromSize(
                  size: box,
                  child: _hitTestable(painter, box),
                ),
              );
            }

            // Figur größer zeichnen als das Fenster und so verschieben, dass der
            // beanspruchte Bereich in der Mitte liegt. Der Rest wird abgeschnitten.
            final viewW = constraints.maxWidth;
            final viewH = constraints.maxHeight;
            final canvasH = viewH * widget.zoom;
            final canvasW = canvasH * view.aspectRatio;

            final focus = _focusY(view);
            final top = (focus * canvasH - viewH / 2).clamp(0.0, canvasH - viewH);
            final left = (canvasW - viewW) / 2;

            return ClipRect(
              child: RepaintBoundary(
                child: Stack(
                  children: [
                    Positioned(
                      left: -left,
                      top: -top,
                      width: canvasW,
                      height: canvasH,
                      child: CustomPaint(
                        size: Size(canvasW, canvasH),
                        painter: painter,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  /// Worauf der Ausschnitt zentriert wird: Schwerpunkt der markierten Muskeln.
  double _focusY(BodyGeometryView view) {
    final marked = widget.highlight;
    final muscles = marked == null
        ? const <String>[]
        : (marked.primary.isNotEmpty ? marked.primary : marked.secondary).toList();
    if (muscles.isEmpty) return 0.5;

    var sum = 0.0;
    for (final m in muscles) {
      sum += view.centerYOf(m);
    }
    return sum / muscles.length;
  }

  Widget _hitTestable(BodyMapPainter painter, Size box) {
    final onTap = widget.onMuscleTap;
    final paint = RepaintBoundary(child: CustomPaint(size: box, painter: painter));
    if (onTap == null) return paint;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapUp: (details) {
        final muscle = painter.backendMuscleAt(details.localPosition, box);
        if (muscle != null) onTap(muscle);
      },
      child: paint,
    );
  }
}

/// Hält das Seitenverhältnis der Figur, egal wie die Fläche aufgezogen wird.
Size fitBodySize(BoxConstraints constraints, {double aspectRatio = kBodyAspectRatio}) {
  final maxHeight = constraints.hasBoundedHeight ? constraints.maxHeight : 320.0;
  final maxWidth = constraints.hasBoundedWidth ? constraints.maxWidth : 200.0;

  var height = maxHeight;
  var width = height * aspectRatio;
  if (width > maxWidth) {
    width = maxWidth;
    height = width / aspectRatio;
  }
  return Size(width, height);
}

class BodyMapPainter extends CustomPainter {
  final BodyGeometryView view;

  /// Backend-Slugs auf 0..1.
  final Map<String, double> activation;

  /// Wenn gesetzt, ersetzt die Zwei-Stufen-Einfärbung einer einzelnen Übung die
  /// Volumen-Rampe. Für die Miniatur-Figuren in Listen.
  final BodyHighlight? highlight;

  final bool outlined;

  /// Auf die Flächen der Grafik zusammengefasst - einmal je Painter statt je Fläche.
  final Map<String, double> _folded;

  BodyMapPainter({
    required this.view,
    this.activation = const {},
    this.highlight,
    this.outlined = true,
  }) : _folded = foldToMuscleMap(activation);

  static const Color _untrained = LyftaTheme.bodyBase;
  static const Color _neutral = LyftaTheme.bodyNeutral;

  /// Bildet die viewBox der Vorlage auf die gezeichnete Fläche ab.
  ///
  /// Von Hand aufgebaut statt über `Matrix4`: `Path.transform` will ohnehin eine
  /// spaltenweise 4x4-Matrix, und die beiden Setter, die es dafür bräuchte, sind
  /// inzwischen abgekündigt.
  static Float64List _fit(Rect viewBox, Size size) {
    final scale = size.height / viewBox.height;
    return Float64List.fromList(<double>[
      scale, 0, 0, 0,
      0, scale, 0, 0,
      0, 0, 1, 0,
      -viewBox.left * scale, -viewBox.top * scale, 0, 1,
    ]);
  }

  final Map<String, List<Path>> _scaled = {};
  List<Path>? _scaledSilhouette;
  Size? _scaledSize;

  void _rescale(Size size) {
    if (_scaledSize == size) return;
    final m = _fit(view.viewBox, size);
    _scaled.clear();
    for (final region in view.regions) {
      _scaled[region.muscle] = [for (final p in region.paths) p.transform(m)];
    }
    _scaledSilhouette = [for (final p in view.silhouette) p.transform(m)];
    _scaledSize = size;
  }

  @override
  void paint(Canvas canvas, Size size) {
    _rescale(size);

    // Mittelgrau statt Hintergrundfarbe: der Körper ist hell, ein dunkler
    // Strich in Hintergrundfarbe würde ihn zerschneiden statt gliedern.
    final outline = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = LyftaTheme.bodyOutline;

    // Silhouette zuerst, damit die Muskelflächen darüber liegen. Etwas dunkler
    // als die Muskeln - das gibt der Figur Tiefe.
    final neutralPaint = Paint()..color = _neutral;
    for (final path in _scaledSilhouette!) {
      canvas.drawPath(path, neutralPaint);
    }

    for (final region in view.regions) {
      final paint = Paint()..color = colorFor(region.muscle);
      for (final path in _scaled[region.muscle]!) {
        canvas.drawPath(path, paint);
        if (outlined) canvas.drawPath(path, outline);
      }
    }
  }

  /// Für eine einzelne Übung: rot für beanspruchte, blau für unterstützende
  /// Muskeln. Sonst der helle Grundton bei 0 und volles Rot bei maximaler
  /// Belastung - schon ein einziger Satz hebt die Fläche sichtbar an, sonst
  /// wäre eine leichte Belastung nicht von "gar nicht" zu unterscheiden.
  ///
  /// [muscle] ist ein Slug der Grafik, nicht des Backends.
  Color colorFor(String muscle) {
    final marked = highlight;
    if (marked != null) {
      if (marked.primary.contains(muscle)) return LyftaTheme.musclePrimary;
      if (marked.secondary.contains(muscle)) return LyftaTheme.muscleSecondary;
      return _untrained;
    }

    final share = (_folded[muscle] ?? 0).clamp(0.0, 1.0);
    if (share <= 0) return _untrained;
    final t = 0.25 + 0.75 * share;
    return Color.lerp(_untrained, LyftaTheme.musclePrimary, t) ?? _untrained;
  }

  /// Welche Muskelfläche liegt unter dem Finger? Rückwärts geprüft, damit die
  /// zuletzt gezeichnete (oben liegende) Fläche gewinnt.
  String? muscleAt(Offset position, Size size) {
    _rescale(size);
    for (final region in view.regions.reversed) {
      for (final path in _scaled[region.muscle]!) {
        if (path.contains(position)) return region.muscle;
      }
    }
    return null;
  }

  /// Wie [muscleAt], liefert aber den Backend-Slug zurück.
  ///
  /// Wo mehrere Backend-Muskeln auf dieselbe Fläche fallen, gewinnt der mit der höheren
  /// Belastung - angetippt wird die Fläche, die etwas zeigt, und der Aufrufer soll dazu
  /// die Zahl finden, die er gerade eingefärbt hat.
  String? backendMuscleAt(Offset position, Size size) {
    final mapped = muscleAt(position, size);
    if (mapped == null) return null;

    String? best;
    var bestShare = -1.0;
    kMuscleMapAlias.forEach((backend, target) {
      if (target != mapped) return;
      final share = activation[backend] ?? 0;
      if (share > bestShare) {
        bestShare = share;
        best = backend;
      }
    });
    return best;
  }

  @override
  bool shouldRepaint(BodyMapPainter old) =>
      old.view != view ||
      old.outlined != outlined ||
      old.highlight != highlight ||
      !mapEquals(old._folded, _folded);
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
///
/// Die Beschriftung ist einstellbar, weil dieselbe Rampe zwei Dinge zeigt: Trainingsvolumen
/// ("wenig" bis "viel") und Erholung ("erholt" bis "ermüdet").
class BodyActivationLegend extends StatelessWidget {
  final String low;
  final String high;

  const BodyActivationLegend({
    super.key,
    this.low = 'wenig',
    this.high = 'viel',
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(low, style: LyftaTheme.label),
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
        Text(high, style: LyftaTheme.label),
      ],
    );
  }
}
