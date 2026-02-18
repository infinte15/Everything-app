
class Task {
  final int? id;
  final String title;
  final String? description;
  final int priority; 
  final DateTime? deadline;
  final int estimatedDurationMinutes;
  final DateTime? scheduledStartTime;
  final DateTime? scheduledEndTime;
  final String status; 
  final String? spaceType; 
  final int? projectId;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final DateTime? completedAt;

  Task({
    this.id,
    required this.title,
    this.description,
    this.priority = 3,
    this.deadline,
    this.estimatedDurationMinutes = 60,
    this.scheduledStartTime,
    this.scheduledEndTime,
    this.status = 'TODO',
    this.spaceType,
    this.projectId,
    this.createdAt,
    this.updatedAt,
    this.completedAt,
  });

  // JSON zu Task
  factory Task.fromJson(Map<String, dynamic> json) {
    return Task(
      id: json['id'],
      title: json['title'],
      description: json['description'],
      priority: json['priority'] ?? 3,
      deadline: json['deadline'] != null 
          ? DateTime.parse(json['deadline']) 
          : null,
      estimatedDurationMinutes: json['estimatedDurationMinutes'] ?? 60,
      scheduledStartTime: json['scheduledStartTime'] != null
          ? DateTime.parse(json['scheduledStartTime'])
          : null,
      scheduledEndTime: json['scheduledEndTime'] != null
          ? DateTime.parse(json['scheduledEndTime'])
          : null,
      status: json['status'] ?? 'TODO',
      spaceType: json['spaceType'],
      projectId: json['projectId'],
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'])
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'])
          : null,
      completedAt: json['completedAt'] != null
          ? DateTime.parse(json['completedAt'])
          : null,
    );
  }

  // Task zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'priority': priority,
      'deadline': deadline?.toIso8601String(),
      'estimatedDurationMinutes': estimatedDurationMinutes,
      'scheduledStartTime': scheduledStartTime?.toIso8601String(),
      'scheduledEndTime': scheduledEndTime?.toIso8601String(),
      'status': status,
      'spaceType': spaceType,
      'projectId': projectId,
    };
  }

  // Copy with
  Task copyWith({
    int? id,
    String? title,
    String? description,
    int? priority,
    DateTime? deadline,
    int? estimatedDurationMinutes,
    DateTime? scheduledStartTime,
    DateTime? scheduledEndTime,
    String? status,
    String? spaceType,
    int? projectId,
    DateTime? createdAt,
    DateTime? updatedAt,
    DateTime? completedAt,
  }) {
    return Task(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      priority: priority ?? this.priority,
      deadline: deadline ?? this.deadline,
      estimatedDurationMinutes: estimatedDurationMinutes ?? this.estimatedDurationMinutes,
      scheduledStartTime: scheduledStartTime ?? this.scheduledStartTime,
      scheduledEndTime: scheduledEndTime ?? this.scheduledEndTime,
      status: status ?? this.status,
      spaceType: spaceType ?? this.spaceType,
      projectId: projectId ?? this.projectId,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      completedAt: completedAt ?? this.completedAt,
    );
  }

  
  bool get isOverdue {
    if (deadline == null) return false;
    return deadline!.isBefore(DateTime.now()) && status != 'COMPLETED';
  }

  
  bool get isDueToday {
    if (deadline == null) return false;
    final now = DateTime.now();
    return deadline!.year == now.year &&
           deadline!.month == now.month &&
           deadline!.day == now.day;
  }

  
  bool get isCompleted => status == 'COMPLETED';

  @override
  String toString() => 'Task(id: $id, title: $title, status: $status)';
}