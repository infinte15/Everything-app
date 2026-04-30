class StudySubject {
  final String id;
  final String name;
  final String? professor;
  final String? colorHex;
  final int creditPoints;
  final String? semester;

  StudySubject({
    required this.id,
    required this.name,
    this.professor,
    this.colorHex,
    this.creditPoints = 0,
    this.semester,
  });
}
