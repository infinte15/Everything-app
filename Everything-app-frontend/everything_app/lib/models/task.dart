
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
  final String category;

  /// Darf der Scheduler die Aufgabe in mehrere Bloecke zerlegen? `null` = Vorgabe des Backends.
  ///
  /// Das Feld gab es im Backend von Anfang an, im Modell hier aber nicht — die Checkbox "Split up"
  /// im Anlege-Dialog wurde gesetzt und dann stillschweigend weggeworfen.
  final bool? splittable;

  /// Frühestens ab diesem Zeitpunkt einplanen (Reclaims "start after"). Gleiche Geschichte.
  final DateTime? notBefore;

  /// Unter-/Obergrenze fuer einen einzelnen Block; `null` = Vorgabe aus den Einstellungen.
  final int? minChunkMinutes;
  final int? maxChunkMinutes;

  /// Hoechstens so viele Bloecke dieser Aufgabe an einem Tag; `null` = unbegrenzt.
  final int? maxChunksPerDay;

  /// Bereits geleistete Minuten — nur gelesen, geplant wird nur der Rest.
  final int? completedMinutes;

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
    this.category = 'Personal',
    this.splittable,
    this.notBefore,
    this.minChunkMinutes,
    this.maxChunkMinutes,
    this.maxChunksPerDay,
    this.completedMinutes,
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
      category: json['category'] ?? 'Personal',
      splittable: json['splittable'],
      notBefore: json['notBefore'] != null
          ? DateTime.parse(json['notBefore'])
          : null,
      minChunkMinutes: json['minChunkMinutes'],
      maxChunkMinutes: json['maxChunkMinutes'],
      maxChunksPerDay: json['maxChunksPerDay'],
      completedMinutes: json['completedMinutes'],
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
      'category': category,
      'splittable': splittable,
      'notBefore': notBefore?.toIso8601String(),
      'minChunkMinutes': minChunkMinutes,
      'maxChunkMinutes': maxChunkMinutes,
      'maxChunksPerDay': maxChunksPerDay,
      'completedMinutes': completedMinutes,
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
    String? category,
    bool? splittable,
    DateTime? notBefore,
    int? minChunkMinutes,
    int? maxChunkMinutes,
    int? maxChunksPerDay,
    int? completedMinutes,
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
      category: category ?? this.category,
      splittable: splittable ?? this.splittable,
      notBefore: notBefore ?? this.notBefore,
      minChunkMinutes: minChunkMinutes ?? this.minChunkMinutes,
      maxChunkMinutes: maxChunkMinutes ?? this.maxChunkMinutes,
      maxChunksPerDay: maxChunksPerDay ?? this.maxChunksPerDay,
      completedMinutes: completedMinutes ?? this.completedMinutes,
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

  /// Offen, mit Termin, aber ohne Block im Kalender.
  ///
  /// Der Scheduler plant seit der harten Deadline-Grenze nichts mehr hinter die Deadline: passt
  /// die Aufgabe nicht mehr davor, bleibt sie ungeplant. Er meldet das zwar als "at risk", aber
  /// nur in der Antwort auf eine ausdrueckliche Neuplanung — laeuft die Neuplanung im Hintergrund,
  /// sieht das niemand. Der fehlende Termin ist die Spur, die bleibt.
  bool get isUnschedulable =>
      !isCompleted && status != 'CANCELLED' && deadline != null && scheduledStartTime == null;

  @override
  String toString() => 'Task(id: $id, title: $title, status: $status)';
}