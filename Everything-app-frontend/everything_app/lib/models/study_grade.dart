class StudyGrade {
  final String id;
  final String subjectId;
  final String examName;
  final double grade;
  final double weight; // e.g., 1.0 for full course, or 0.3 for mid-term
  final DateTime date;

  StudyGrade({
    required this.id,
    required this.subjectId,
    required this.examName,
    required this.grade,
    this.weight = 1.0,
    required this.date,
  });
}
