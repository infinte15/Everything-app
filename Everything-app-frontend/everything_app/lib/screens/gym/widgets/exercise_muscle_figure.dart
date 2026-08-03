import 'package:flutter/material.dart';

import '../../../theme/lyfta_theme.dart';
import 'body_activation_map.dart';
import 'body_map_paths.dart';

/// Welche Körperseite die Figur zeigt.
enum BodySide {
  /// Automatisch die Seite, auf der die beanspruchten Muskeln liegen.
  auto,
  front,
  back,

  /// Vorne und hinten nebeneinander - nur bei genug Platz sinnvoll.
  both,
}

/// Ab dieser Kantenlänge sind die Trennstriche zwischen den Flächen sinnvoll.
/// Darunter frisst ein 1px-Strich eine 4px-Muskelfläche auf.
const double _kOutlineMinSize = 72;

/// Unterhalb dieser Kantenlänge wird auf den beanspruchten Bereich gezoomt.
///
/// Die ganze Figur ist bei 48px etwa 27px breit - eine einzelne Muskelfläche
/// landet dann bei 2 bis 4 Pixeln und ist nicht mehr von der Nachbarfläche zu
/// unterscheiden. Der Ausschnitt verdoppelt den Maßstab.
const double _kZoomBelowSize = 72;
const double _kZoomFactor = 2.1;

/// Mittlere Höhe eines Muskels in der Figur (0 = Kopf, 1 = Füße).
double _muscleCenterY(String muscle, bool back) {
  final regions = back ? kBodyBackRegions : kBodyFrontRegions;
  for (final region in regions) {
    if (region.muscle != muscle) continue;
    var sum = 0.0;
    for (final p in region.points) {
      sum += p.dy;
    }
    return sum / region.points.length;
  }
  return 0.5;
}

/// Worauf der Ausschnitt zentriert wird: Schwerpunkt der primären Muskeln,
/// ersatzweise der sekundären, sonst die Bildmitte.
double _focusY(List<String> primary, List<String> secondary, bool back) {
  final muscles = primary.isNotEmpty ? primary : secondary;
  if (muscles.isEmpty) return 0.5;
  var sum = 0.0;
  for (final m in muscles) {
    sum += _muscleCenterY(m, back);
  }
  return sum / muscles.length;
}

/// Auf welcher Seite liegt der Schwerpunkt der Übung?
///
/// Punkte je Muskel: 2 für primär, 1 für sekundär, gezählt gegen die Muskeln,
/// die es in der jeweiligen Ansicht überhaupt gibt. Muskeln, die vorne *und*
/// hinten vorkommen (Schultern, Unterarme, Waden …), zählen für beide Seiten
/// gleich und entscheiden damit nichts - das ist gewollt.
///
/// Gleichstand fällt auf die Vorderansicht zurück: die Flächen sind dort größer
/// und die Silhouette vertrauter.
bool preferBackView(List<String> primary, List<String> secondary) {
  var front = 0;
  var back = 0;

  for (final muscle in primary) {
    if (kFrontMuscles.contains(muscle)) front += 2;
    if (kBackMuscles.contains(muscle)) back += 2;
  }
  for (final muscle in secondary) {
    if (kFrontMuscles.contains(muscle)) front += 1;
    if (kBackMuscles.contains(muscle)) back += 1;
  }

  return back > front;
}

/// Kleine Körper-Figur, die zeigt, was eine Übung beansprucht.
///
/// Ersetzt die früheren Fotos aus der free-exercise-db: die Grafik entsteht aus
/// den Muskel-Slugs, die ohnehin an jeder Übung hängen, braucht damit weder
/// Assets noch Netz und ist für alle Übungen einheitlich.
class ExerciseMuscleFigure extends StatelessWidget {
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final double size;
  final double radius;
  final BodySide side;

  const ExerciseMuscleFigure({
    super.key,
    required this.primaryMuscles,
    this.secondaryMuscles = const [],
    this.size = 52,
    this.radius = 10,
    this.side = BodySide.auto,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Container(
        width: size,
        height: size,
        // Deutlich dunkler als der helle Körper, damit die Figur heraussticht.
        color: LyftaTheme.background,
        child: _FigureBody(
          primaryMuscles: primaryMuscles,
          secondaryMuscles: secondaryMuscles,
          side: side,
          outlined: size >= _kOutlineMinSize,
          zoom: size < _kZoomBelowSize ? _kZoomFactor : 1.0,
        ),
      ),
    );
  }
}

/// Große Variante für die Detailansicht: Vorder- und Rückansicht nebeneinander.
class ExerciseMuscleFigureBanner extends StatelessWidget {
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final double height;

  const ExerciseMuscleFigureBanner({
    super.key,
    required this.primaryMuscles,
    this.secondaryMuscles = const [],
    this.height = 200,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(
        color: LyftaTheme.background,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.fromLTRB(8, 14, 8, 10),
      child: Column(
        children: [
          Expanded(
            child: Row(
              children: [
                Expanded(child: _labelled('VORNE', BodySide.front)),
                Expanded(child: _labelled('HINTEN', BodySide.back)),
              ],
            ),
          ),
          const SizedBox(height: 8),
          const _MuscleLegend(),
        ],
      ),
    );
  }

  Widget _labelled(String label, BodySide side) {
    return Column(
      children: [
        Expanded(
          child: _FigureBody(
            primaryMuscles: primaryMuscles,
            secondaryMuscles: secondaryMuscles,
            side: side,
            outlined: true,
          ),
        ),
        const SizedBox(height: 6),
        Text(label, style: LyftaTheme.label),
      ],
    );
  }
}

/// "Beansprucht / Unterstützend" - ohne die Legende ist die Rot/Blau-Codierung
/// beim ersten Mal nicht selbsterklärend.
class _MuscleLegend extends StatelessWidget {
  const _MuscleLegend();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _dot(LyftaTheme.musclePrimary, 'Beansprucht'),
        const SizedBox(width: 16),
        _dot(LyftaTheme.muscleSecondary, 'Unterstützend'),
      ],
    );
  }

  Widget _dot(Color color, String label) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 7,
          height: 7,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 5),
        Text(label, style: LyftaTheme.label),
      ],
    );
  }
}

class _FigureBody extends StatelessWidget {
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final BodySide side;
  final bool outlined;

  /// 1 = ganze Figur, >1 = Ausschnitt um die beanspruchten Muskeln.
  final double zoom;

  const _FigureBody({
    required this.primaryMuscles,
    required this.secondaryMuscles,
    required this.side,
    required this.outlined,
    this.zoom = 1.0,
  });

  @override
  Widget build(BuildContext context) {
    final highlight = BodyHighlight(
      primary: primaryMuscles.toSet(),
      secondary: secondaryMuscles.toSet(),
    );

    if (side == BodySide.both) {
      return Row(
        children: [
          Expanded(child: _view(highlight, back: false)),
          Expanded(child: _view(highlight, back: true)),
        ],
      );
    }

    final back = switch (side) {
      BodySide.back => true,
      BodySide.front => false,
      _ => preferBackView(primaryMuscles, secondaryMuscles),
    };
    return _view(highlight, back: back);
  }

  Widget _view(BodyHighlight highlight, {required bool back}) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final box = fitBodySize(constraints);
        final painter = BodyMapPainter(
          highlight: highlight,
          back: back,
          outlined: outlined,
        );

        // Listen zeichnen dutzende dieser Figuren; ohne Grenze malt jeder
        // Scroll-Frame alle neu.
        if (zoom <= 1.0) {
          return Center(
            child: RepaintBoundary(
              child: CustomPaint(size: box, painter: painter),
            ),
          );
        }

        // Figur größer zeichnen als das Fenster und so verschieben, dass der
        // beanspruchte Bereich in der Mitte liegt. Der Rest wird abgeschnitten.
        final viewW = constraints.maxWidth;
        final viewH = constraints.maxHeight;
        final canvasH = viewH * zoom;
        final canvasW = canvasH * kBodyAspectRatio;

        final focus = _focusY(primaryMuscles, secondaryMuscles, back);
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
  }
}
