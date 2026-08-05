import 'dart:ui';

import '../utils/module_color.dart';

/// Ein Modul. Entspricht dem Course im Backend.
///
/// Die Dart-Feldnamen bleiben, wie die Screens sie kennen; die JSON-Schlüssel sind die des
/// Backend-DTOs. Genau hier lag der Contract-Bruch: gelesen wurde `professor`, `creditPoints`
/// und `colorHex`, geliefert werden `instructor`, `ectsCredits` und `color`. Weil creditPoints
/// dadurch immer 0 war, zeigte der GPA-Ring dauerhaft „—".
class StudySubject {
  final String id;
  final String name;
  final String? professor;
  final String? colorHex;

  /// Die Modulfarbe; ohne gesetzte Farbe das Study-Lila. Dieselbe Auslegung wie im
  /// Stundenplan und am Lernziel — sonst hätte dasselbe Modul je nach Ansicht eine
  /// andere Farbe.
  Color get color => parseModuleColor(colorHex);
  final int creditPoints;

  /// Freitext-Bezeichnung des Semesters. Der Server pflegt sie aus [semesterId].
  final String? semester;

  /// Verknüpftes Semester; null = keinem zugeordnet.
  final String? semesterId;

  StudySubject({
    required this.id,
    required this.name,
    this.professor,
    this.colorHex,
    this.creditPoints = 0,
    this.semester,
    this.semesterId,
  });

  factory StudySubject.fromJson(Map<String, dynamic> json) => StudySubject(
        id: json['id'].toString(),
        name: json['name'] ?? '',
        professor: json['instructor'] ?? '',
        creditPoints: (json['ectsCredits'] as num?)?.toInt() ?? 0,
        semester: json['semesterLabel'] ?? json['semester'] ?? '',
        semesterId: json['semesterId']?.toString(),
        colorHex: json['color'] ?? '#3B82F6',
      );

  /// semesterId geht bewusst mit: beim Anlegen wertet der Server es aus. Beim Ändern läuft
  /// die Zuordnung über PUT /courses/{id}/semester, weil updateCourse partiell arbeitet und
  /// „keinem Semester zugeordnet" dort nicht von „unverändert" zu unterscheiden wäre.
  Map<String, dynamic> toJson() => {
        'name': name,
        'instructor': professor,
        'ectsCredits': creditPoints,
        'semester': semester,
        'semesterId': semesterId != null ? int.tryParse(semesterId!) : null,
        'color': colorHex,
      };

  StudySubject copyWith({
    String? id,
    String? name,
    String? professor,
    String? colorHex,
    int? creditPoints,
    String? semester,
    String? semesterId,
  }) {
    return StudySubject(
      id: id ?? this.id,
      name: name ?? this.name,
      professor: professor ?? this.professor,
      colorHex: colorHex ?? this.colorHex,
      creditPoints: creditPoints ?? this.creditPoints,
      semester: semester ?? this.semester,
      semesterId: semesterId ?? this.semesterId,
    );
  }
}
