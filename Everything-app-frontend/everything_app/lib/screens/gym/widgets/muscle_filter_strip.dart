import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';
import 'body_activation_map.dart';
import 'body_map_paths.dart';

/// Muskel-Filter als Reihe kleiner Körper statt als Textleiste.
///
/// Lyfta zeigt über der Übungsliste genau das: für jeden Muskel eine Figur, in der er rot
/// markiert ist. Das liest sich schneller als zwanzig Wörter nebeneinander - man sucht sich
/// die Stelle am Körper, nicht die Vokabel. Möglich wird es erst mit der MuscleMap-Geometrie,
/// die jede Fläche einzeln kennt.
///
/// Die Figur zeigt jeweils die Seite, auf der der Muskel wirklich zu sehen ist (siehe
/// [preferBackView]) - eine Trizeps-Kachel von vorne wäre eine graue Figur mit einem roten
/// Strich am Armrand.
class MuscleFilterStrip extends StatelessWidget {
  final List<GymMuscleOption> options;

  /// Backend-Slug des gewählten Muskels, `null` = alle.
  final String? selected;

  final ValueChanged<String?> onChanged;

  const MuscleFilterStrip({
    super.key,
    required this.options,
    required this.selected,
    required this.onChanged,
  });

  /// Höhe der Figur. Darunter wird der markierte Muskel zum Fleck.
  static const double _figureHeight = 54;

  @override
  Widget build(BuildContext context) {
    // Ausdauer hat keine Muskelfläche - eine Figur, in der nichts rot ist, wäre von
    // "kein Filter" nicht zu unterscheiden. Der Filter bleibt, als Symbol.
    final drawable = [
      for (final o in options)
        if (muscleMapSlug(o.slug) != null) o,
    ];
    final other = [
      for (final o in options)
        if (muscleMapSlug(o.slug) == null) o,
    ];

    return SizedBox(
      height: _figureHeight + 20,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        children: [
          _Tile(
            label: 'Alle',
            selected: selected == null,
            onTap: () => onChanged(null),
            child: const Icon(
              Icons.grid_view_rounded,
              size: 22,
              color: LyftaTheme.textSecondary,
            ),
          ),
          for (final option in drawable)
            _Tile(
              label: option.label,
              selected: selected == option.slug,
              onTap: () => onChanged(option.slug),
              child: BodyFigure(
                back: preferBackView([option.slug], const []),
                highlight: BodyHighlight.fromBackend([option.slug], const []),
                // Bei 54 px frisst ein 1px-Strich die kleineren Flächen auf.
                outlined: false,
              ),
            ),
          for (final option in other)
            _Tile(
              label: option.label,
              selected: selected == option.slug,
              onTap: () => onChanged(option.slug),
              child: const Icon(
                Icons.favorite_rounded,
                size: 20,
                color: LyftaTheme.textSecondary,
              ),
            ),
        ],
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Widget child;

  const _Tile({
    required this.label,
    required this.selected,
    required this.onTap,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: onTap,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 140),
              width: 46,
              height: MuscleFilterStrip._figureHeight,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: LyftaTheme.surfaceHighlight,
                borderRadius: BorderRadius.circular(10),
                // Die Auswahl steht im Rahmen, nicht in einer Füllung: eine getönte Fläche
                // hinter der Figur würde mit dem Rot der Markierung um Aufmerksamkeit ringen.
                border: Border.all(
                  color: selected ? LyftaTheme.primary : Colors.transparent,
                  width: 1.5,
                ),
              ),
              padding: const EdgeInsets.all(3),
              child: child,
            ),
            const SizedBox(height: 4),
            SizedBox(
              width: 50,
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: LyftaTheme.label.copyWith(
                  color: selected ? LyftaTheme.textPrimary : LyftaTheme.textTertiary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
