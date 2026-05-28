class StudyGrade {
  final String id;
  final String subjectId;
  final String examName;
  final String? examType;
  final double grade;
  /// Anteil an der Modulnote in Prozent (1–100), Summe pro Modul idealerweise 100.
  final int weightPercent;
  final DateTime date;
  final String? notes;

  StudyGrade({
    required this.id,
    required this.subjectId,
    required this.examName,
    this.examType,
    required this.grade,
    this.weightPercent = 100,
    required this.date,
    this.notes,
  });

  StudyGrade copyWith({
    String? id,
    String? subjectId,
    String? examName,
    String? examType,
    double? grade,
    int? weightPercent,
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
      date: date ?? this.date,
      notes: notes ?? this.notes,
    );
  }
}
