import 'dart:ui';

import '../utils/module_color.dart';

/// Ein wöchentliches Lernziel für ein Modul, serverseitig gespeichert.
///
/// Vorher lag das Ziel nur im Arbeitsspeicher: nach jedem Neustart war es weg, während die
/// daraus erzeugte Aufgabe im Backend liegen blieb und ewig weitergeplant wurde.
///
/// Das Fach ist kein Freitext mehr, sondern ein Modul. Name und Farbe kommen deshalb vom
/// Server und werden hier nur gespiegelt — ein umbenanntes Modul heißt sofort überall gleich.
class StudyGoal {
  final int? id;
  final int courseId;
  final String courseName;
  final String? courseColor;
  final String emoji;
  final double weeklyGoalHours;
  final double loggedHours;

  /// Der Brücken-Task, über den der SmartScheduler die Reststunden im Kalender platziert.
  final int? taskId;

  const StudyGoal({
    this.id,
    required this.courseId,
    required this.courseName,
    this.courseColor,
    this.emoji = '📚',
    required this.weeklyGoalHours,
    this.loggedHours = 0,
    this.taskId,
  });

  Color get color => parseModuleColor(courseColor);

  /// Was diese Woche noch zu tun ist; nie negativ.
  double get remainingHours =>
      (weeklyGoalHours - loggedHours).clamp(0.0, double.infinity);

  double get progress =>
      weeklyGoalHours > 0 ? (loggedHours / weeklyGoalHours).clamp(0.0, 1.0) : 0;

  factory StudyGoal.fromJson(Map<String, dynamic> json) {
    return StudyGoal(
      id: json['id'] as int?,
      courseId: json['courseId'] as int? ?? 0,
      courseName: json['courseName'] as String? ?? 'Unbekanntes Modul',
      courseColor: json['courseColor'] as String?,
      emoji: json['emoji'] as String? ?? '📚',
      weeklyGoalHours: (json['weeklyGoalHours'] as num?)?.toDouble() ?? 0,
      loggedHours: (json['loggedHours'] as num?)?.toDouble() ?? 0,
      taskId: json['taskId'] as int?,
    );
  }

  /// Nur die Felder, die der Server entgegennimmt — Name, Farbe und Reststunden leitet er
  /// selbst aus dem Modul ab.
  Map<String, dynamic> toJson() => {
        'courseId': courseId,
        'emoji': emoji,
        'weeklyGoalHours': weeklyGoalHours,
      };

  StudyGoal copyWith({
    int? id,
    int? courseId,
    String? courseName,
    String? courseColor,
    String? emoji,
    double? weeklyGoalHours,
    double? loggedHours,
    int? taskId,
  }) {
    return StudyGoal(
      id: id ?? this.id,
      courseId: courseId ?? this.courseId,
      courseName: courseName ?? this.courseName,
      courseColor: courseColor ?? this.courseColor,
      emoji: emoji ?? this.emoji,
      weeklyGoalHours: weeklyGoalHours ?? this.weeklyGoalHours,
      loggedHours: loggedHours ?? this.loggedHours,
      taskId: taskId ?? this.taskId,
    );
  }
}
