import 'dart:ui';

class StudyPlanGoal {
  final String id;
  final String subject;
  final int colorValue;
  final String emoji;
  final double weeklyGoalHours;
  final double loggedHours;
  final DateTime weekStart;

  StudyPlanGoal({
    required this.id,
    required this.subject,
    required this.colorValue,
    this.emoji = '📚',
    required this.weeklyGoalHours,
    this.loggedHours = 0,
    required this.weekStart,
  });

  Color get color => Color(colorValue);

  double get progress =>
      weeklyGoalHours > 0 ? (loggedHours / weeklyGoalHours).clamp(0.0, 1.0) : 0;

  StudyPlanGoal copyWith({
    String? id,
    String? subject,
    int? colorValue,
    String? emoji,
    double? weeklyGoalHours,
    double? loggedHours,
    DateTime? weekStart,
  }) {
    return StudyPlanGoal(
      id: id ?? this.id,
      subject: subject ?? this.subject,
      colorValue: colorValue ?? this.colorValue,
      emoji: emoji ?? this.emoji,
      weeklyGoalHours: weeklyGoalHours ?? this.weeklyGoalHours,
      loggedHours: loggedHours ?? this.loggedHours,
      weekStart: weekStart ?? this.weekStart,
    );
  }
}
