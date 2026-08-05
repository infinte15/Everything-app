import 'dart:ui';

import '../utils/module_color.dart';

/// Ein wiederkehrender Termin im Stundenplan, serverseitig gespeichert.
///
/// Loest [LessonPlanEntry] ab, das nur im Speicher lebte und bei jedem App-Start weg war.
/// Ein Termin haengt immer an einem Modul — Name, Farbe und Dozent kommen von dort, sie werden
/// hier nicht noch einmal eingetippt.
///
/// Der Termin gilt nur innerhalb des Semesters seines Moduls; der SmartScheduler blockiert
/// ausserhalb dieser Grenzen nichts mehr. [semesterLabel] macht das in der UI sichtbar.
class CourseSchedule {
  final String id;
  final String courseId;
  final String courseName;
  final String? courseColor;
  final String? courseInstructor;
  final String? semesterLabel;

  /// 1 = Montag … 7 = Sonntag (ISO, wie `DateTime.weekday` und `java.time.DayOfWeek`).
  final int weekday;
  final int startHour;
  final int startMinute;
  final int endHour;
  final int endMinute;
  final String? location;

  const CourseSchedule({
    required this.id,
    required this.courseId,
    required this.courseName,
    this.courseColor,
    this.courseInstructor,
    this.semesterLabel,
    required this.weekday,
    required this.startHour,
    this.startMinute = 0,
    required this.endHour,
    this.endMinute = 0,
    this.location,
  });

  factory CourseSchedule.fromJson(Map<String, dynamic> json) {
    final start = _parseTime(json['startTime']);
    final end = _parseTime(json['endTime']);
    return CourseSchedule(
      id: json['id'].toString(),
      courseId: json['courseId']?.toString() ?? '',
      courseName: json['courseName'] ?? '',
      courseColor: json['courseColor'],
      courseInstructor: json['courseInstructor'],
      semesterLabel: json['semesterLabel'],
      weekday: _parseWeekday(json['dayOfWeek']),
      startHour: start.$1,
      startMinute: start.$2,
      endHour: end.$1,
      endMinute: end.$2,
      location: json['location'],
    );
  }

  /// Nur die aenderbaren Felder. Das Modul kommt aus dem Pfad, nicht aus dem Rumpf.
  Map<String, dynamic> toJson() => {
        'dayOfWeek': _weekdayNames[weekday - 1],
        'startTime': _formatTime(startHour, startMinute),
        'endTime': _formatTime(endHour, endMinute),
        'location': location,
      };

  // ── Anzeige ────────────────────────────────────────────────────────────────

  /// 0 = Montag … 6 = Sonntag — die Wochenansicht rechnet spaltenweise ab null.
  int get dayIndex => weekday - 1;

  int get durationMinutes =>
      (endHour * 60 + endMinute) - (startHour * 60 + startMinute);

  String get startTimeLabel => _formatLabel(startHour, startMinute);
  String get endTimeLabel => _formatLabel(endHour, endMinute);

  /// Die Modulfarbe; ohne gesetzte Farbe das Study-Lila.
  Color get color => parseModuleColor(courseColor);

  CourseSchedule copyWith({
    int? weekday,
    int? startHour,
    int? startMinute,
    int? endHour,
    int? endMinute,
    String? location,
  }) {
    return CourseSchedule(
      id: id,
      courseId: courseId,
      courseName: courseName,
      courseColor: courseColor,
      courseInstructor: courseInstructor,
      semesterLabel: semesterLabel,
      weekday: weekday ?? this.weekday,
      startHour: startHour ?? this.startHour,
      startMinute: startMinute ?? this.startMinute,
      endHour: endHour ?? this.endHour,
      endMinute: endMinute ?? this.endMinute,
      location: location ?? this.location,
    );
  }

  // ── Umrechnung ─────────────────────────────────────────────────────────────

  static const _weekdayNames = [
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
  ];

  static int _parseWeekday(dynamic raw) {
    final index = _weekdayNames.indexOf(raw?.toString().toUpperCase() ?? '');
    return index == -1 ? 1 : index + 1;
  }

  /// Der Server schickt "08:00:00" (LocalTime), gelegentlich ohne Sekunden.
  static (int, int) _parseTime(dynamic raw) {
    final parts = raw?.toString().split(':') ?? const [];
    if (parts.length < 2) return (0, 0);
    return (int.tryParse(parts[0]) ?? 0, int.tryParse(parts[1]) ?? 0);
  }

  static String _formatTime(int hour, int minute) =>
      '${_pad(hour)}:${_pad(minute)}:00';

  static String _formatLabel(int hour, int minute) => '${_pad(hour)}:${_pad(minute)}';

  static String _pad(int value) => value.toString().padLeft(2, '0');
}
