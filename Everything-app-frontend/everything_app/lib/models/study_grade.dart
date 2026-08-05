/// Eine Teilleistung eines Moduls.
///
/// Die JSON-Schlüssel sind die des Backend-DTOs. Gelesen wurde vorher `gradeValue`,
/// `weighting` und `date` — geliefert werden `grade`, `weight` und `examDate`. Deshalb
/// warf addGrade beim Auswerten von `(created['gradeValue'] as num)` einen TypeError auf null.
class StudyGrade {
  final String id;
  final String subjectId;
  final String examName;
  final String? examType;
  final double grade;
  /// Anteil an der Modulnote in Prozent (1–100), Summe pro Modul idealerweise 100.
  final int weightPercent;
  /// false = Schein: wird angezeigt, verschiebt den Modulschnitt aber nicht.
  final bool countsTowardGrade;
  final DateTime date;
  final String? notes;

  StudyGrade({
    required this.id,
    required this.subjectId,
    required this.examName,
    this.examType,
    required this.grade,
    this.weightPercent = 100,
    this.countsTowardGrade = true,
    required this.date,
    this.notes,
  });

  factory StudyGrade.fromJson(Map<String, dynamic> json) => StudyGrade(
        id: json['id'].toString(),
        subjectId: json['courseId']?.toString() ?? '',
        examName: json['examName'] ?? '',
        examType: json['examType'] ?? 'Klausur',
        grade: (json['grade'] as num?)?.toDouble() ?? 0.0,
        weightPercent: (json['weight'] as num?)?.toInt() ?? 100,
        countsTowardGrade: json['countsTowardGrade'] as bool? ?? true,
        date: json['examDate'] != null
            ? DateTime.parse(json['examDate'])
            : DateTime.now(),
        notes: json['notes'],
      );

  Map<String, dynamic> toJson() => {
        'courseId': int.tryParse(subjectId),
        'examName': examName,
        'examType': examType,
        'grade': grade,
        'weight': weightPercent,
        'countsTowardGrade': countsTowardGrade,
        'examDate': date.toIso8601String().split('T')[0],
        'notes': notes,
      };

  StudyGrade copyWith({
    String? id,
    String? subjectId,
    String? examName,
    String? examType,
    double? grade,
    int? weightPercent,
    bool? countsTowardGrade,
    DateTime? date,
    String? notes,
  }) {
    return StudyGrade(
      id: id ?? this.id,
      subjectId: subjectId ?? this.subjectId,
      examName: examName ?? this.examName,
      examType: examType ?? this.examType,
      grade: grade ?? this.grade,
      weightPercent: weightPercent ?? this.weightPercent,
      countsTowardGrade: countsTowardGrade ?? this.countsTowardGrade,
      date: date ?? this.date,
      notes: notes ?? this.notes,
    );
  }
}
