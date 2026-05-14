import 'package:intl/intl.dart';

class Project {
  final int? id;
  final String name;
  final String? description;
  final String status; // PLANNING, ACTIVE, ON_HOLD, COMPLETED, CANCELLED
  final int completionPercentage;
  final int tasksTotal;
  final int tasksCompleted;
  final DateTime? startDate;
  final DateTime? targetEndDate;
  final DateTime? actualEndDate;
  final int weeklySessionCount;
  final int sessionDurationMinutes;

  Project({
    this.id,
    required this.name,
    this.description,
    this.status = 'PLANNING',
    this.completionPercentage = 0,
    this.tasksTotal = 0,
    this.tasksCompleted = 0,
    this.startDate,
    this.targetEndDate,
    this.actualEndDate,
    this.weeklySessionCount = 1,
    this.sessionDurationMinutes = 60,
  });

  factory Project.fromJson(Map<String, dynamic> json) {
    return Project(
      id: json['id'],
      name: json['name'],
      description: json['description'],
      status: json['status'] ?? 'PLANNING',
      completionPercentage: json['completionPercentage'] ?? 0,
      tasksTotal: json['tasksTotal'] ?? 0,
      tasksCompleted: json['tasksCompleted'] ?? 0,
      startDate: json['startDate'] != null ? DateTime.parse(json['startDate']) : null,
      targetEndDate: json['targetEndDate'] != null ? DateTime.parse(json['targetEndDate']) : null,
      actualEndDate: json['actualEndDate'] != null ? DateTime.parse(json['actualEndDate']) : null,
      weeklySessionCount: json['weeklySessionCount'] ?? 1,
      sessionDurationMinutes: json['sessionDurationMinutes'] ?? 60,
    );
  }

  Map<String, dynamic> toJson() {
    final fmt = DateFormat('yyyy-MM-dd');
    return {
      'id': id,
      'name': name,
      'description': description,
      'status': status,
      'completionPercentage': completionPercentage,
      'startDate': startDate != null ? fmt.format(startDate!) : null,
      'targetEndDate': targetEndDate != null ? fmt.format(targetEndDate!) : null,
      'actualEndDate': actualEndDate != null ? fmt.format(actualEndDate!) : null,
      'weeklySessionCount': weeklySessionCount,
      'sessionDurationMinutes': sessionDurationMinutes,
    };
  }

  Project copyWith({
    int? id,
    String? name,
    String? description,
    String? status,
    int? completionPercentage,
    int? tasksTotal,
    int? tasksCompleted,
    int? weeklySessionCount,
    int? sessionDurationMinutes,
  }) {
    return Project(
      id: id ?? this.id,
      name: name ?? this.name,
      description: description ?? this.description,
      status: status ?? this.status,
      completionPercentage: completionPercentage ?? this.completionPercentage,
      tasksTotal: tasksTotal ?? this.tasksTotal,
      tasksCompleted: tasksCompleted ?? this.tasksCompleted,
      startDate: startDate ?? this.startDate,
      targetEndDate: targetEndDate ?? this.targetEndDate,
      actualEndDate: actualEndDate ?? this.actualEndDate,
      weeklySessionCount: weeklySessionCount ?? this.weeklySessionCount,
      sessionDurationMinutes: sessionDurationMinutes ?? this.sessionDurationMinutes,
    );
  }

  String get statusLabel {
    switch (status) {
      case 'PLANNING': return 'Planung';
      case 'ACTIVE': return 'Aktiv';
      case 'ON_HOLD': return 'Pausiert';
      case 'COMPLETED': return 'Abgeschlossen';
      case 'CANCELLED': return 'Abgebrochen';
      default: return status;
    }
  }

  bool get isOverdue {
    if (targetEndDate == null) return false;
    return targetEndDate!.isBefore(DateTime.now()) && status != 'COMPLETED';
  }
}
