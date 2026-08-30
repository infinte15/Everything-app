/// Geometrie der Körper-Silhouette für die Muskel-Grafik.
///
/// **Herkunft.** Die Umrisse stammen aus [MuscleMap](https://github.com/melihcolpan/MuscleMap)
/// von Melih Colpan (MIT) und erreichen dieses Projekt über
/// [openGym](https://gitlab.com/DuarteSantos8/opengym), das die Swift-Quelle von MuscleMap in
/// ein JSON-Modul überführt hat. Beides ist MIT-lizenziert und darf hier liegen - anders als
/// die Übungsbilder, die weiterhin nur als URL referenziert werden.
///
/// Die Vorlage führt eine männliche und eine weibliche Silhouette; übernommen ist nur die
/// männliche. Beide zeichnen exakt dieselben Muskelflächen unter denselben Namen, die zweite
/// änderte also nur die Kontur - und eine Auswahl, die an der Auswertung nichts ändert, ist
/// eine Einstellung, die niemand treffen will.
///
/// Sie ersetzen die früher von Hand gesetzten Polygone: die hatten vier bis sieben Punkte pro
/// Muskel und mussten beim Zeichnen gerundet werden, damit sie überhaupt wie ein Körper
/// aussahen. MuscleMap liefert echte anatomische Konturen, Vorder- *und* Rückansicht, und
/// kennt Flächen, die es vorher schlicht nicht gab (Obliques, Serratus, Hüftbeuger, Schienbein).
///
/// **Warum ein Asset und kein Dart-Literal.** Die Geometrie ist rund 44 KB. Als `const`-Liste
/// im Code läge sie in jedem Build im Speicher, auch für alle, die nie ins Gym-Space gehen.
/// Als Asset wird sie einmal beim ersten Blick auf eine Körpergrafik geladen und danach
/// statisch gehalten - dieselbe Überlegung, die openGym zum dynamischen Import bewegt hat.
library;

import 'dart:convert';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path_parsing/path_parsing.dart';

/// Die Muskeln, die die Grafik einfärben kann - in Reihenfolge von Kopf zu Fuß.
///
/// Das ist MuscleMaps Vokabular, nicht das der App. [muscleMapSlug] übersetzt.
const List<String> kMuscleMapSlugs = [
  'trapezius', 'deltoids', 'chest', 'upper-back', 'serratus',
  'biceps', 'triceps', 'forearm',
  'abs', 'obliques', 'lower-back',
  'gluteal', 'quadriceps', 'hamstring', 'adductors', 'hip-flexors',
  'calves', 'tibialis',
];

/// Kopf, Haare, Hals, Hände, Knie, Knöchel, Füße: nötig, damit die Figur als Körper lesbar
/// ist, aber nie eingefärbt - sie tragen keine Trainingslast.
const List<String> kMuscleMapInert = [
  'head', 'hair', 'neck', 'hands', 'knees', 'ankles', 'feet',
];

/// Muskel-Slug des Backends (`MuscleGroup.getSlug()`) auf den Slug der Grafik.
///
/// Nicht ganz eins zu eins, weil MuscleMap den Rücken als eine Fläche zeichnet: `lats` und
/// `middle back` landen beide auf `upper-back`, `glutes` und `abductors` beide auf `gluteal`,
/// `traps` und `neck` beide auf `trapezius`. Trifft mehr als ein Backend-Muskel dieselbe
/// Fläche, gewinnt der stärkere Wert - siehe [foldToMuscleMap].
///
/// `cardio` fehlt bewusst: Ausdauer hat keine Muskelfläche, und eine erfundene wäre gelogen.
const Map<String, String> kMuscleMapAlias = {
  'abdominals': 'abs',
  'abductors': 'gluteal',
  'adductors': 'adductors',
  'biceps': 'biceps',
  'calves': 'calves',
  'chest': 'chest',
  'forearms': 'forearm',
  'glutes': 'gluteal',
  'hamstrings': 'hamstring',
  'hip flexors': 'hip-flexors',
  'lats': 'upper-back',
  'lower back': 'lower-back',
  'middle back': 'upper-back',
  'neck': 'trapezius',
  'obliques': 'obliques',
  'quadriceps': 'quadriceps',
  'serratus': 'serratus',
  'shoulders': 'deltoids',
  'tibialis': 'tibialis',
  'traps': 'trapezius',
  'triceps': 'triceps',
};

/// Übersetzt einen Backend-Slug, oder `null` wenn die Grafik dafür keine Fläche hat.
///
/// Nimmt auch schon übersetzte Slugs an, damit Aufrufer nicht wissen müssen, aus welcher
/// Welt ihr Wert kommt.
String? muscleMapSlug(String slug) {
  final normalized = slug.trim().toLowerCase().replaceAll('_', ' ');
  final mapped = kMuscleMapAlias[normalized];
  if (mapped != null) return mapped;
  final direct = normalized.replaceAll(' ', '-');
  return kMuscleMapSlugs.contains(direct) ? direct : null;
}

/// Fasst Backend-Belastungswerte auf die Flächen der Grafik zusammen.
///
/// Beim Zusammenfallen zweier Backend-Muskeln auf eine Fläche gewinnt der größere Wert. Die
/// Summe wäre falsch: die Fläche zeigt "wie stark beansprucht", nicht "wie viele Muskeln
/// zeigen hierher" - Latissimus 0.8 und mittlerer Rücken 0.5 ergeben keinen Rücken bei 1.3.
Map<String, double> foldToMuscleMap(Map<String, double> byBackendSlug) {
  final out = <String, double>{};
  byBackendSlug.forEach((slug, value) {
    final mapped = muscleMapSlug(slug);
    if (mapped == null) return;
    final existing = out[mapped];
    if (existing == null || value > existing) out[mapped] = value;
  });
  return out;
}

/// Übersetzt eine Menge von Backend-Slugs auf Flächen der Grafik.
Set<String> foldSetToMuscleMap(Iterable<String> slugs) {
  final out = <String>{};
  for (final slug in slugs) {
    final mapped = muscleMapSlug(slug);
    if (mapped != null) out.add(mapped);
  }
  return out;
}

/// Welche Muskeln eine Übung beansprucht - im Gegensatz zur Volumen-Rampe der
/// Körper-Karte eine reine Ja/Nein-Einfärbung in zwei Stufen.
///
/// Die Slugs sind bereits auf das Vokabular der Grafik übersetzt.
class BodyHighlight {
  final Set<String> primary;
  final Set<String> secondary;

  const BodyHighlight({
    this.primary = const {},
    this.secondary = const {},
  });

  /// Aus Backend-Slugs. Ein Muskel, der primär *und* sekundär genannt wird, bleibt primär.
  factory BodyHighlight.fromBackend(
    Iterable<String> primaryMuscles,
    Iterable<String> secondaryMuscles,
  ) {
    final primary = foldSetToMuscleMap(primaryMuscles);
    return BodyHighlight(
      primary: primary,
      secondary: foldSetToMuscleMap(secondaryMuscles).difference(primary),
    );
  }

  bool get isEmpty => primary.isEmpty && secondary.isEmpty;
}

/// Eine einfärbbare Muskelfläche. Kann aus mehreren Teilflächen bestehen (links/rechts).
class BodyRegion {
  final String muscle;
  final List<Path> paths;

  /// Umschließendes Rechteck in viewBox-Koordinaten - für den Ausschnitt der Miniaturen.
  final Rect bounds;

  BodyRegion(this.muscle, this.paths) : bounds = _unionBounds(paths);
}

/// Eine Ansicht (vorne oder hinten) mit ihrem eigenen Koordinatensystem.
class BodyGeometryView {
  /// Das viewBox-Rechteck der Vorlage. Vorder- und Rückansicht liegen bei MuscleMap
  /// nebeneinander in einem gemeinsamen Raum, die Rückansicht hat deshalb einen x-Versatz.
  final Rect viewBox;

  /// Silhouette: wird gezeichnet, aber nie eingefärbt.
  final List<Path> silhouette;

  final List<BodyRegion> regions;

  BodyGeometryView({
    required this.viewBox,
    required this.silhouette,
    required this.regions,
  });

  double get aspectRatio => viewBox.width / viewBox.height;

  late final Set<String> muscles = {for (final r in regions) r.muscle};

  /// Mittlere Höhe eines Muskels, 0 = Kopf, 1 = Füße. 0.5 wenn unbekannt.
  double centerYOf(String muscle) {
    for (final region in regions) {
      if (region.muscle != muscle) continue;
      return ((region.bounds.center.dy - viewBox.top) / viewBox.height).clamp(0.0, 1.0);
    }
    return 0.5;
  }
}

/// Ein Körper in beiden Ansichten.
class BodyGeometry {
  final BodyGeometryView front;
  final BodyGeometryView back;

  const BodyGeometry({required this.front, required this.back});

  BodyGeometryView view({required bool back}) => back ? this.back : front;
}

/// Lädt die Geometrie einmal und hält sie danach.
///
/// [geometry] ist `null`, solange das Asset nicht da ist. Die Widgets hören darauf und
/// zeichnen bis dahin einen Platzhalter gleicher Höhe, damit beim Eintreffen nichts springt.
class BodyMapGeometry {
  BodyMapGeometry._();

  static const String assetPath = 'assets/body/muscle_map.json';

  static final ValueNotifier<BodyGeometry?> geometry = ValueNotifier(null);

  static Future<void>? _pending;

  /// Lädt das Asset, falls nötig. Mehrfachaufrufe teilen sich denselben Ladevorgang.
  static Future<void> ensureLoaded() {
    if (geometry.value != null) return Future.value();
    return _pending ??= _load();
  }

  static Future<void> _load() async {
    final text = await rootBundle.loadString(assetPath);
    final body = json.decode(text) as Map<String, dynamic>;
    geometry.value = BodyGeometry(
      front: _buildView(body['front'] as Map<String, dynamic>),
      back: _buildView(body['back'] as Map<String, dynamic>),
    );
  }

  static BodyGeometryView _buildView(Map<String, dynamic> view) {
    final parts = (view['p'] as Map<String, dynamic>).map(
      (slug, paths) => MapEntry(
        slug,
        [for (final d in paths as List) _parse(d as String)],
      ),
    );

    return BodyGeometryView(
      viewBox: _parseViewBox(view['vb'] as String),
      silhouette: [
        for (final slug in kMuscleMapInert) ...?parts[slug],
      ],
      regions: [
        for (final slug in kMuscleMapSlugs)
          if (parts[slug] != null) BodyRegion(slug, parts[slug]!),
      ],
    );
  }

  static Rect _parseViewBox(String value) {
    final n = value.trim().split(RegExp(r'[\s,]+')).map(double.parse).toList();
    return Rect.fromLTWH(n[0], n[1], n[2], n[3]);
  }

  static Path _parse(String d) {
    final proxy = _PathBuilder();
    writeSvgPathDataToPath(d, proxy);
    return proxy.path;
  }
}

/// Nimmt die von [writeSvgPathDataToPath] normalisierten Segmente entgegen. Bögen und
/// quadratische Kurven der Vorlage sind dort schon zu kubischen aufgelöst.
class _PathBuilder extends PathProxy {
  final Path path = Path();

  @override
  void moveTo(double x, double y) => path.moveTo(x, y);

  @override
  void lineTo(double x, double y) => path.lineTo(x, y);

  @override
  void cubicTo(
    double x1, double y1, double x2, double y2, double x3, double y3) =>
      path.cubicTo(x1, y1, x2, y2, x3, y3);

  @override
  void close() => path.close();
}

Rect _unionBounds(List<Path> paths) {
  if (paths.isEmpty) return Rect.zero;
  var bounds = paths.first.getBounds();
  for (final path in paths.skip(1)) {
    bounds = bounds.expandToInclude(path.getBounds());
  }
  return bounds;
}

/// Seitenverhältnis (Breite zu Höhe), solange die echte Geometrie noch nicht da ist.
///
/// Entspricht der männlichen Vorderansicht (727 × 1280). Der Platzhalter bekommt damit
/// dieselbe Fläche wie die Figur, die ihn ablöst.
const double kBodyAspectRatio = 727 / 1280;

/// Welche Flächen die Vorderansicht zeigt, welche die Rückansicht.
///
/// Fest verdrahtet statt aus der geladenen Geometrie abgeleitet, damit [preferBackView] schon
/// entscheiden kann, bevor das Asset da ist - die Liste steht sonst kurz auf der falschen
/// Seite und klappt beim Laden um. Für die männliche und die weibliche Vorlage identisch,
/// nachgeprüft gegen `muscle_map.json`.
const Set<String> kFrontMuscles = {
  'trapezius', 'deltoids', 'chest', 'serratus', 'biceps', 'triceps', 'forearm',
  'abs', 'obliques', 'quadriceps', 'adductors', 'hip-flexors', 'calves', 'tibialis',
};

const Set<String> kBackMuscles = {
  'trapezius', 'deltoids', 'upper-back', 'triceps', 'forearm', 'lower-back',
  'gluteal', 'hamstring', 'adductors', 'calves',
};

/// Muskeln, die die Rückansicht deutlich größer zeigt als die Vorderansicht.
///
/// MuscleMap zeichnet mehrere Muskeln in *beiden* Ansichten - den Trizeps etwa auch von
/// vorne, als schmalen Streifen an der Armaußenseite. Bloße Zugehörigkeit ("kommt hinten
/// vor") taugt deshalb nicht mehr zur Seitenwahl: sie schickte eine Trizeps-Übung auf die
/// Vorderansicht, wo vom Muskel fast nichts zu sehen ist.
///
/// Die Einteilung stammt aus der gezeichneten Fläche je Ansicht, gemessen an der Vorlage.
/// Schultern, Unterarme und Adduktoren sind in beiden Ansichten etwa gleich groß und stehen
/// deshalb in keiner der beiden Mengen - sie entscheiden nichts.
const Set<String> kMusclesBestFromBack = {
  'trapezius', 'triceps', 'upper-back', 'lower-back', 'gluteal', 'hamstring', 'calves',
};

const Set<String> kMusclesBestFromFront = {
  'chest', 'serratus', 'biceps', 'abs', 'obliques', 'quadriceps', 'hip-flexors', 'tibialis',
};

/// Auf welcher Seite liegt der Schwerpunkt einer Übung?
///
/// Punkte je Muskel: 2 für primär, 1 für sekundär, vergeben an die Ansicht, die den Muskel
/// tatsächlich zeigt (siehe [kMusclesBestFromBack]). Muskeln, die in beiden Ansichten gleich
/// gut zu sehen sind, zählen für keine Seite - das ist gewollt.
///
/// Gleichstand fällt auf die Vorderansicht zurück: die Flächen sind dort größer und die
/// Silhouette vertrauter. Erwartet Backend-Slugs.
bool preferBackView(Iterable<String> primary, Iterable<String> secondary) {
  var front = 0;
  var back = 0;

  void score(Iterable<String> muscles, int weight) {
    for (final muscle in foldSetToMuscleMap(muscles)) {
      if (kMusclesBestFromBack.contains(muscle)) back += weight;
      if (kMusclesBestFromFront.contains(muscle)) front += weight;
    }
  }

  score(primary, 2);
  score(secondary, 1);

  return back > front;
}
