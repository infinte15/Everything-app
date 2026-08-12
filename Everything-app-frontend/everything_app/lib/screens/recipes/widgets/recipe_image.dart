import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Das Bild eines Rezepts - oder die Kachel, die an seiner Stelle steht.
///
/// **Der Platzhalter ist das Normalbild dieses Space, nicht der Fehlerfall.**
/// Rezepte bekommen ein Foto nur beim Import; von Hand angelegte und alle
/// Rezepte des Demo-Bestands haben keins. Deshalb eine gesetzte
/// Buchstabenkachel: die liest sich als Absicht. Ein durchgestrichenes
/// Kamerasymbol würde bei jedem zweiten Rezept nach kaputter Leitung aussehen.
///
/// Kein `cached_network_image`: Flutters `ImageCache` deckt das Zurückscrollen
/// innerhalb einer Sitzung ab. Verloren geht nur der Plattencache über
/// Neustarts - dafür eine Abhängigkeit mit nativem Anteil aufzunehmen lohnt
/// erst, wenn importierte Bilder bei jedem Kaltstart sichtbar nachladen.
class RecipeImage extends StatelessWidget {
  const RecipeImage({
    super.key,
    required this.url,
    required this.name,
    this.height,
    this.width,
    this.fit = BoxFit.cover,
  });

  final String? url;
  final String name;
  final double? height;
  final double? width;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    if (url == null || url!.trim().isEmpty) {
      return _placeholder();
    }

    return Image.network(
      url!,
      height: height,
      width: width,
      fit: fit,
      errorBuilder: (_, _, _) => _placeholder(),
      loadingBuilder: (context, child, progress) =>
          progress == null ? child : _placeholder(showInitials: false),
      frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
        if (wasSynchronouslyLoaded || frame != null) return child;
        return _placeholder(showInitials: false);
      },
    );
  }

  Widget _placeholder({bool showInitials = true}) {
    return Container(
      height: height,
      width: width,
      decoration: BoxDecoration(
        color: KineticTheme.surfaceElevated,
        border: Border.all(color: KineticTheme.divider, width: 1),
      ),
      alignment: Alignment.center,
      child: showInitials
          ? Text(
              _initials(name),
              style: TextStyle(
                color: KineticTheme.textTertiary,
                fontSize: ((height ?? 64) * 0.22).clamp(12.0, 44.0),
                fontWeight: FontWeight.w600,
                letterSpacing: 2,
              ),
            )
          : null,
    );
  }

  /// Höchstens zwei Anfangsbuchstaben - "Rote-Linsen-Dal" wird zu "RL", nicht
  /// zu einer Buchstabensuppe.
  static String _initials(String name) {
    final words = name
        .split(RegExp(r'[\s\-–]+'))
        .where((word) => word.isNotEmpty)
        .toList();
    if (words.isEmpty) return '?';
    if (words.length == 1) {
      return words.first.characters.take(2).toString().toUpperCase();
    }
    return (words[0].characters.first + words[1].characters.first).toUpperCase();
  }
}
