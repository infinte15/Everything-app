import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../theme/lyfta_theme.dart';
import 'exercise_muscle_figure.dart';

/// Übungsbilder und -animationen.
///
/// Die Medien liegen nicht in der App: der Katalog liefert URLs auf ein CDN, von dem
/// [CachedNetworkImage] sie einmal lädt und danach lokal hält. Bei ~1300 Übungen mit je
/// rund 90 KB Animation wäre alles andere unvernünftig.
///
/// **Herkunft.** Die Zeichnungen sind © Gym visual (https://gymvisual.com/) und stehen weder
/// unter der Lizenz dieses Projekts noch unter der des Datensatzes, über den sie kommen.
/// [GymVisualAttribution] hält den Hinweis dort sichtbar, wo die Bilder zu sehen sind.
///
/// **Warum sie ins Design passen.** Es sind technische Illustrationen, keine Fotos: eine
/// anonyme graue Figur, der beanspruchte Muskel rot markiert - dieselbe Codierung, die
/// [LyftaTheme.musclePrimary] und die Körpergrafik ohnehin schon benutzen.

/// Weißabgleich für den Bildhintergrund.
///
/// Die Zeichnungen stehen auf reinem Weiß. Auf [LyftaTheme.background] (#0E0E0E) wäre das
/// eine leuchtende Fläche, die alles um sich herum erschlägt. Multiplizieren zieht das Weiß
/// auf ein ruhiges Hellgrau, während die dunklen Linien dunkel und das Muskelrot rot bleiben -
/// anders als beim Abdunkeln über Deckkraft, das die ganze Zeichnung ausgrauen würde.
const Color _kPaperTint = Color(0xFFE4E4E8);

Widget _tinted(Widget child) => ColorFiltered(
      colorFilter: const ColorFilter.mode(_kPaperTint, BlendMode.multiply),
      child: child,
    );

/// Quadratisches Vorschaubild für Listen und Zeilen.
///
/// Ohne [imageUrl] - eigene Übungen haben keine - übernimmt die gezeichnete
/// [ExerciseMuscleFigure], die dieselbe Kantenlänge und denselben Radius füllt. Sie ist damit
/// nicht abgelöst, sondern der Rückfall für alles, wofür es kein Bild gibt.
class ExerciseThumb extends StatelessWidget {
  final String? imageUrl;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final double size;
  final double radius;

  const ExerciseThumb({
    super.key,
    required this.imageUrl,
    this.primaryMuscles = const [],
    this.secondaryMuscles = const [],
    this.size = 48,
    this.radius = 10,
  });

  Widget get _fallback => ExerciseMuscleFigure(
        primaryMuscles: primaryMuscles,
        secondaryMuscles: secondaryMuscles,
        size: size,
        radius: radius,
      );

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    if (url == null || url.isEmpty) return _fallback;

    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Container(
        width: size,
        height: size,
        color: _kPaperTint,
        child: _tinted(CachedNetworkImage(
          imageUrl: url,
          fit: BoxFit.cover,
          fadeInDuration: const Duration(milliseconds: 140),
          // Beim Laden bleibt die Kachel einfarbig statt zu blinken; die Liste scrollt sonst
          // durch ein Feld zuckender Spinner.
          placeholder: (_, _) => const SizedBox.shrink(),
          errorWidget: (_, _, _) => _fallback,
        )),
      ),
    );
  }
}

/// Große Animation der Übungsausführung.
///
/// Antippen hält sie an - dann steht das Standbild derselben Übung da, nicht ein eingefrorener
/// Frame, denn ein GIF lässt sich nicht anhalten. Genau dafür liefert der Katalog beides.
///
/// Sie steht nur im Übungsblatt, nicht im laufenden Training: dort will man Gewicht und
/// Wiederholungen sehen, und eine laufende Schleife schob das Satzraster aus dem Bild.
class ExerciseAnimation extends StatefulWidget {
  final String? animationUrl;
  final String? imageUrl;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final double height;

  const ExerciseAnimation({
    super.key,
    required this.animationUrl,
    required this.imageUrl,
    this.primaryMuscles = const [],
    this.secondaryMuscles = const [],
    this.height = 200,
  });

  @override
  State<ExerciseAnimation> createState() => _ExerciseAnimationState();
}

class _ExerciseAnimationState extends State<ExerciseAnimation> {
  bool _playing = true;

  String? get _source {
    final animation = widget.animationUrl;
    final still = widget.imageUrl;
    if (_playing && animation != null && animation.isNotEmpty) return animation;
    if (still != null && still.isNotEmpty) return still;
    return animation;
  }

  bool get _canPlay {
    final a = widget.animationUrl;
    final i = widget.imageUrl;
    // Umschalten lohnt nur, wenn es beides gibt - sonst passiert beim Tippen nichts.
    return a != null && a.isNotEmpty && i != null && i.isNotEmpty;
  }

  @override
  Widget build(BuildContext context) => _frame(widget.height);

  Widget _frame(double height) {
    final url = _source;

    if (url == null || url.isEmpty) {
      return ExerciseMuscleFigureBanner(
        primaryMuscles: widget.primaryMuscles,
        secondaryMuscles: widget.secondaryMuscles,
        height: height,
      );
    }

    final Widget tile = ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Container(
        color: _kPaperTint,
        child: _tinted(CachedNetworkImage(
          imageUrl: url,
          // contain, nicht cover: die Zeichnung nutzt ihr Quadrat schon fast vollständig
          // aus (im Mittel bleiben 4 % Rand), formatfüllend würde also echtes Motiv
          // abschneiden - bei Übungen mit Rack oder Bank genau das Gerät.
          fit: BoxFit.contain,
          fadeInDuration: const Duration(milliseconds: 160),
          placeholder: (_, _) => const Center(
            child: SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2, color: LyftaTheme.textTertiary),
            ),
          ),
          errorWidget: (_, _, _) => ExerciseMuscleFigureBanner(
            primaryMuscles: widget.primaryMuscles,
            secondaryMuscles: widget.secondaryMuscles,
            height: height,
          ),
        )),
      ),
    );

    return GestureDetector(
      onTap: _canPlay ? () => setState(() => _playing = !_playing) : null,
      child: AnimatedSize(
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
        child: SizedBox(
          height: height,
          width: double.infinity,
          // Die Vorlage ist quadratisch. Ohne diese Begrenzung zöge sich die helle Fläche
          // über die ganze Breite und stünde als leuchtender Balken um ein kleines Bild -
          // auf einem breiten Fenster besonders. AspectRatio schrumpft von selbst mit,
          // wenn die Breite knapper ist als die Höhe.
          child: Center(
            child: AspectRatio(
              aspectRatio: 1,
              child: Stack(
                children: [
                  Positioned.fill(child: tile),
                  if (_canPlay)
                    Positioned(
                      bottom: 6,
                      left: 6,
                      child: _Pill(
                        icon: _playing ? Icons.pause : Icons.play_arrow,
                        label: _playing ? 'Tippen pausiert' : 'Tippen startet',
                        onTap: () => setState(() => _playing = !_playing),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Kleine Schaltfläche auf dem hellen Bild - dunkel, damit sie darauf lesbar bleibt.
class _Pill extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _Pill({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: LyftaTheme.background.withValues(alpha: 0.72),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 13, color: LyftaTheme.textSecondary),
            const SizedBox(width: 4),
            Text(label, style: LyftaTheme.label.copyWith(color: LyftaTheme.textSecondary)),
          ],
        ),
      ),
    );
  }
}

/// Pflichtangabe zu den Übungsmedien.
///
/// Der Rechteinhaber verlangt, dass der Hinweis jede Verwendung begleitet. Er gehört deshalb
/// dorthin, wo die Bilder zu sehen sind, nicht in einen Einstellungsdialog, den niemand öffnet.
class GymVisualAttribution extends StatelessWidget {
  const GymVisualAttribution({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: GestureDetector(
        onTap: () => launchUrl(
          Uri.parse('https://gymvisual.com/'),
          mode: LaunchMode.externalApplication,
        ),
        child: Text(
          'Übungsbilder und -animationen © Gym visual',
          textAlign: TextAlign.center,
          style: LyftaTheme.label.copyWith(color: LyftaTheme.textTertiary),
        ),
      ),
    );
  }
}
