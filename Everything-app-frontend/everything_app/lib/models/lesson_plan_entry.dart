import 'dart:ui';

class LessonPlanEntry {
  final String id;
  final String subject;
  final String? room;
  final String? professor;
  final int dayIndex; // 0=Mon … 6=Sun
  final int startHour;
  final int startMinute;
  final int durationMinutes;
  final int colorValue;
  final String type; // Vorlesung / Praktikum / Seminar / Übung

  LessonPlanEntry({
    required this.id,
    required this.subject,
    this.room,
    this.professor,
    required this.dayIndex,
    required this.startHour,
    this.startMinute = 0,
    this.durationMinutes = 90,
    required this.colorValue,
    this.type = 'Vorlesung',
  });

  Color get color => Color(colorValue);

  String get startTimeLabel {
    final h = startHour.toString().padLeft(2, '0');
    final m = startMinute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  String get endTimeLabel {
    final totalMin = startHour * 60 + startMinute + durationMinutes;
    final h = (totalMin ~/ 60).toString().padLeft(2, '0');
    final m = (totalMin % 60).toString().padLeft(2, '0');
    return '$h:$m';
  }

  LessonPlanEntry copyWith({
    String? id,
    String? subject,
    String? room,
    String? professor,
    int? dayIndex,
    int? startHour,
    int? startMinute,
    int? durationMinutes,
    int? colorValue,
    String? type,
  }) {
    return LessonPlanEntry(
      id: id ?? this.id,
      subject: subject ?? this.subject,
      room: room ?? this.room,
      professor: professor ?? this.professor,
      dayIndex: dayIndex ?? this.dayIndex,
      startHour: startHour ?? this.startHour,
      startMinute: startMinute ?? this.startMinute,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      colorValue: colorValue ?? this.colorValue,
      type: type ?? this.type,
    );
  }
}
