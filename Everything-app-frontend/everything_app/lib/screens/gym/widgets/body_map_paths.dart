/// Geometrie der Körper-Silhouette für die Muskel-Grafik.
///
/// Alle Koordinaten liegen normiert in 0..1 (x nach rechts, y nach unten) und werden
/// beim Zeichnen auf die tatsächliche Fläche skaliert. Die Figur ist symmetrisch:
/// Flächen mit [BodyRegion.mirrored] werden nur für die linke Körperhälfte beschrieben
/// und an x = 0.5 gespiegelt.
///
/// Bewusst kein SVG-Asset: die Flächen müssen einzeln nach Belastung eingefärbt und
/// angetippt werden können, und die Farben kommen aus dem Theme.
library;

import 'dart:ui';

/// Seitenverhältnis (Breite zu Höhe) der Silhouette.
const double kBodyAspectRatio = 0.62;

/// Wie stark die Polygone zu Kurven gerundet werden (0 = eckig wie zuvor).
///
/// Die Punktlisten unten bleiben unverändert; das Runden passiert erst beim
/// Zeichnen. Bewusst weit unter dem Lehrbuchwert 0.5: die Flächen haben nur 4
/// bis 7 Punkte, und volles Catmull-Rom rundet ein Fünfeck zur Ellipse - die
/// Figur zerfiel dabei in einzelne Kiesel. 0.18 nimmt die harten Kanten, lässt
/// die Silhouette aber stehen.
const double kBodySmoothing = 0.18;

class BodyRegion {
  /// Slug aus `MuscleGroup` im Backend, z.B. "lower back".
  final String muscle;
  final List<Offset> points;
  final bool mirrored;

  const BodyRegion(this.muscle, this.points, {this.mirrored = true});
}

/// Welche Muskeln eine Übung beansprucht - im Gegensatz zur Volumen-Rampe der
/// Körper-Karte eine reine Ja/Nein-Einfärbung in zwei Stufen.
class BodyHighlight {
  final Set<String> primary;
  final Set<String> secondary;

  const BodyHighlight({
    this.primary = const {},
    this.secondary = const {},
  });

  bool get isEmpty => primary.isEmpty && secondary.isEmpty;
}

/// Kopf, Hände, Füße und Knie - nie eingefärbt, aber nötig, damit die Figur
/// als Körper lesbar ist.
const List<List<Offset>> kBodyNeutralShapes = [
  // Linke Hand
  [Offset(0.326, 0.455), Offset(0.370, 0.452), Offset(0.374, 0.505), Offset(0.328, 0.508)],
  // Rechte Hand
  [Offset(0.674, 0.455), Offset(0.630, 0.452), Offset(0.626, 0.505), Offset(0.672, 0.508)],
  // Linkes Knie
  [Offset(0.398, 0.712), Offset(0.462, 0.715), Offset(0.470, 0.750), Offset(0.406, 0.748)],
  // Rechtes Knie
  [Offset(0.602, 0.712), Offset(0.538, 0.715), Offset(0.530, 0.750), Offset(0.594, 0.748)],
  // Linker Fuß
  [Offset(0.412, 0.902), Offset(0.466, 0.902), Offset(0.472, 0.962), Offset(0.402, 0.962)],
  // Rechter Fuß
  [Offset(0.588, 0.902), Offset(0.534, 0.902), Offset(0.528, 0.962), Offset(0.598, 0.962)],
];

/// Kopf als Ellipse (Mittelpunkt, Radien) - ebenfalls neutral.
const Offset kHeadCenter = Offset(0.5, 0.046);
const Offset kHeadRadius = Offset(0.043, 0.048);

/// Vorderansicht.
const List<BodyRegion> kBodyFrontRegions = [
  BodyRegion('neck', [
    Offset(0.456, 0.082),
    Offset(0.544, 0.082),
    Offset(0.552, 0.126),
    Offset(0.448, 0.126),
  ], mirrored: false),

  BodyRegion('traps', [
    Offset(0.500, 0.116),
    Offset(0.500, 0.152),
    Offset(0.394, 0.167),
    Offset(0.374, 0.150),
    Offset(0.440, 0.121),
  ]),

  BodyRegion('shoulders', [
    Offset(0.374, 0.149),
    Offset(0.394, 0.168),
    Offset(0.387, 0.233),
    Offset(0.336, 0.239),
    Offset(0.310, 0.206),
    Offset(0.321, 0.167),
  ]),

  BodyRegion('chest', [
    Offset(0.499, 0.158),
    Offset(0.499, 0.262),
    Offset(0.413, 0.258),
    Offset(0.394, 0.213),
    Offset(0.397, 0.169),
  ]),

  // Von vorn ist vom Latissimus nur ein schmaler Streifen an der Flanke zu sehen.
  BodyRegion('lats', [
    Offset(0.393, 0.216),
    Offset(0.412, 0.263),
    Offset(0.408, 0.330),
    Offset(0.383, 0.322),
    Offset(0.377, 0.251),
  ]),

  BodyRegion('biceps', [
    Offset(0.336, 0.241),
    Offset(0.387, 0.237),
    Offset(0.378, 0.331),
    Offset(0.331, 0.337),
    Offset(0.318, 0.286),
  ]),

  BodyRegion('forearms', [
    Offset(0.331, 0.339),
    Offset(0.378, 0.334),
    Offset(0.369, 0.452),
    Offset(0.329, 0.455),
    Offset(0.318, 0.400),
  ]),

  BodyRegion('abdominals', [
    Offset(0.429, 0.266),
    Offset(0.571, 0.266),
    Offset(0.578, 0.396),
    Offset(0.545, 0.462),
    Offset(0.455, 0.462),
    Offset(0.422, 0.396),
  ], mirrored: false),

  BodyRegion('abductors', [
    Offset(0.384, 0.326),
    Offset(0.413, 0.341),
    Offset(0.419, 0.431),
    Offset(0.399, 0.471),
    Offset(0.369, 0.441),
    Offset(0.366, 0.366),
  ]),

  BodyRegion('quadriceps', [
    Offset(0.405, 0.482),
    Offset(0.478, 0.489),
    Offset(0.474, 0.640),
    Offset(0.458, 0.714),
    Offset(0.418, 0.712),
    Offset(0.398, 0.630),
    Offset(0.392, 0.520),
  ]),

  BodyRegion('adductors', [
    Offset(0.479, 0.489),
    Offset(0.498, 0.492),
    Offset(0.498, 0.612),
    Offset(0.484, 0.652),
    Offset(0.470, 0.630),
    Offset(0.474, 0.546),
  ]),

  BodyRegion('calves', [
    Offset(0.412, 0.752),
    Offset(0.468, 0.752),
    Offset(0.464, 0.878),
    Offset(0.448, 0.902),
    Offset(0.425, 0.900),
    Offset(0.410, 0.858),
  ]),
];

/// Rückansicht.
const List<BodyRegion> kBodyBackRegions = [
  BodyRegion('neck', [
    Offset(0.456, 0.082),
    Offset(0.544, 0.082),
    Offset(0.552, 0.126),
    Offset(0.448, 0.126),
  ], mirrored: false),

  // Von hinten ist der Trapezmuskel die große Fläche des oberen Rückens.
  BodyRegion('traps', [
    Offset(0.500, 0.114),
    Offset(0.500, 0.258),
    Offset(0.437, 0.238),
    Offset(0.408, 0.180),
    Offset(0.438, 0.126),
  ]),

  BodyRegion('shoulders', [
    Offset(0.374, 0.149),
    Offset(0.394, 0.168),
    Offset(0.387, 0.233),
    Offset(0.336, 0.239),
    Offset(0.310, 0.206),
    Offset(0.321, 0.167),
  ]),

  BodyRegion('lats', [
    Offset(0.390, 0.206),
    Offset(0.438, 0.251),
    Offset(0.440, 0.336),
    Offset(0.412, 0.353),
    Offset(0.383, 0.301),
    Offset(0.378, 0.241),
  ]),

  BodyRegion('middle back', [
    Offset(0.444, 0.259),
    Offset(0.556, 0.259),
    Offset(0.556, 0.335),
    Offset(0.444, 0.335),
  ], mirrored: false),

  BodyRegion('lower back', [
    Offset(0.433, 0.340),
    Offset(0.567, 0.340),
    Offset(0.559, 0.440),
    Offset(0.441, 0.440),
  ], mirrored: false),

  BodyRegion('triceps', [
    Offset(0.336, 0.241),
    Offset(0.387, 0.237),
    Offset(0.378, 0.331),
    Offset(0.331, 0.337),
    Offset(0.318, 0.286),
  ]),

  BodyRegion('forearms', [
    Offset(0.331, 0.339),
    Offset(0.378, 0.334),
    Offset(0.369, 0.452),
    Offset(0.329, 0.455),
    Offset(0.318, 0.400),
  ]),

  BodyRegion('abductors', [
    Offset(0.372, 0.396),
    Offset(0.401, 0.409),
    Offset(0.403, 0.470),
    Offset(0.383, 0.492),
    Offset(0.362, 0.456),
  ]),

  BodyRegion('glutes', [
    Offset(0.398, 0.448),
    Offset(0.498, 0.453),
    Offset(0.498, 0.546),
    Offset(0.431, 0.553),
    Offset(0.396, 0.520),
  ]),

  BodyRegion('hamstrings', [
    Offset(0.400, 0.557),
    Offset(0.479, 0.559),
    Offset(0.474, 0.660),
    Offset(0.458, 0.714),
    Offset(0.418, 0.712),
    Offset(0.400, 0.640),
  ]),

  BodyRegion('adductors', [
    Offset(0.480, 0.560),
    Offset(0.498, 0.562),
    Offset(0.498, 0.660),
    Offset(0.482, 0.690),
    Offset(0.470, 0.660),
    Offset(0.474, 0.600),
  ]),

  BodyRegion('calves', [
    Offset(0.410, 0.752),
    Offset(0.470, 0.752),
    Offset(0.466, 0.870),
    Offset(0.448, 0.902),
    Offset(0.424, 0.900),
    Offset(0.408, 0.856),
  ]),
];

/// Welche Muskeln überhaupt in welcher Ansicht vorkommen. Wird gebraucht, um für
/// eine Übung automatisch die passende Seite zu wählen - `triceps` und `glutes`
/// gibt es nur hinten, `chest` und `quadriceps` nur vorne.
final Set<String> kFrontMuscles = {
  for (final region in kBodyFrontRegions) region.muscle,
};
final Set<String> kBackMuscles = {
  for (final region in kBodyBackRegions) region.muscle,
};
