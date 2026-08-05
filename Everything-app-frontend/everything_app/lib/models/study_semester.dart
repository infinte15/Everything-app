/// Ein Semester, z.B. „WS 2025/26".
///
/// Ersetzt die bisherige Freitext-Gruppierung: die Semesterauswahl im Notenrechner arbeitete
/// auf den distinkten `semester`-Strings der Module, war also weder sortierbar noch
/// umbenennbar. Das Backend hält den Freitext weiterhin synchron, damit alte Daten passen.
class StudySemester {
  final String id;
  final String label;
  final DateTime? startDate;
  final DateTime? endDate;
  final int orderIndex;
  final bool isCurrent;

  /// Abgeleitet, nur lesend.
  final int moduleCount;
  final int totalEcts;

  const StudySemester({
    required this.id,
    required this.label,
    this.startDate,
    this.endDate,
    this.orderIndex = 0,
    this.isCurrent = false,
    this.moduleCount = 0,
    this.totalEcts = 0,
  });

  factory StudySemester.fromJson(Map<String, dynamic> json) => StudySemester(
        id: json['id'].toString(),
        label: json['label'] ?? '',
        startDate:
            json['startDate'] != null ? DateTime.parse(json['startDate']) : null,
        endDate: json['endDate'] != null ? DateTime.parse(json['endDate']) : null,
        orderIndex: (json['orderIndex'] as num?)?.toInt() ?? 0,
        isCurrent: json['isCurrent'] as bool? ?? false,
        moduleCount: (json['moduleCount'] as num?)?.toInt() ?? 0,
        totalEcts: (json['totalEcts'] as num?)?.toInt() ?? 0,
      );

  Map<String, dynamic> toJson() => {
        'label': label,
        'startDate': startDate?.toIso8601String().split('T')[0],
        'endDate': endDate?.toIso8601String().split('T')[0],
        'isCurrent': isCurrent,
      };

  StudySemester copyWith({
    String? id,
    String? label,
    DateTime? startDate,
    DateTime? endDate,
    int? orderIndex,
    bool? isCurrent,
    int? moduleCount,
    int? totalEcts,
  }) {
    return StudySemester(
      id: id ?? this.id,
      label: label ?? this.label,
      startDate: startDate ?? this.startDate,
      endDate: endDate ?? this.endDate,
      orderIndex: orderIndex ?? this.orderIndex,
      isCurrent: isCurrent ?? this.isCurrent,
      moduleCount: moduleCount ?? this.moduleCount,
      totalEcts: totalEcts ?? this.totalEcts,
    );
  }
}
