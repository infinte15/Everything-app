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

/// Kleine Körper-Figur, die zeigt, was eine Übung beansprucht.
///
/// Sie tritt dort an, wo es kein Übungsbild gibt - bei selbst angelegten Übungen und wenn das
/// CDN nicht erreichbar ist. Die Grafik entsteht aus den Muskel-Slugs, die ohnehin an jeder
/// Übung hängen, und braucht deshalb kein Netz.
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
          const MuscleLegend(),
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
class MuscleLegend extends StatelessWidget {
  const MuscleLegend({super.key});

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
    final highlight = BodyHighlight.fromBackend(primaryMuscles, secondaryMuscles);

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

  Widget _view(BodyHighlight highlight, {required bool back}) => BodyFigure(
        back: back,
        highlight: highlight,
        outlined: outlined,
        zoom: zoom,
      );
}
