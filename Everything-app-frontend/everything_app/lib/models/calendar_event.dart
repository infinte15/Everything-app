import 'package:flutter/material.dart';

class CalendarEvent {
  final int? id;
  final String title;
  final String? description;
  final DateTime startTime;
  final DateTime endTime;
  final String? location;
  final String eventType; 
  final bool isFixed;
  final String? color;
  final String? notes;
  
  // Related IDs
  final int? relatedTaskId;
  final int? relatedHabitId;
  final int? relatedWorkoutId;
  /// Gesetzt bei automatisch geplanter Projektzeit (eventType == 'PROJECT').
  final int? relatedProjectId;

  /// Vom Nutzer abgehakt; null heisst offen. Wird ausschliesslich ueber
  /// PUT /events/{id}/complete gesetzt — dort werden auch die Minuten gutgeschrieben.
  final DateTime? completedAt;

  /// Vom Nutzer uebersprungen; null heisst offen. Wird ausschliesslich ueber
  /// PUT /events/{id}/skip gesetzt.
  ///
  /// Der Block bleibt bewusst stehen: er ist der Beleg dafuer, dass diese Ausfuehrung schon vom
  /// Wochenpensum abgezogen ist. Wuerde er verschwinden, plante der Scheduler prompt Ersatz —
  /// genau daran scheiterte frueher das Loeschen automatisch geplanter Bloecke.
  final DateTime? skippedAt;

  /// Deadline und Prioritaet der verknuepften Aufgabe — nur lesend, gepflegt wird beides am Task.
  /// Der Kalender hebt damit den Block hervor, der die letzte Chance vor der Deadline ist.
  final DateTime? relatedTaskDeadline;
  final int? relatedTaskPriority;

  CalendarEvent({
    this.id,
    required this.title,
    this.description,
    required this.startTime,
    required this.endTime,
    this.location,
    this.eventType = 'OTHER',
    this.isFixed = false,
    this.color,
    this.notes,
    this.relatedTaskId,
    this.relatedHabitId,
    this.relatedWorkoutId,
    this.relatedProjectId,
    this.completedAt,
    this.skippedAt,
    this.relatedTaskDeadline,
    this.relatedTaskPriority,
  });

  // JSON zu CalendarEvent
  factory CalendarEvent.fromJson(Map<String, dynamic> json) {
    return CalendarEvent(
      id: json['id'],
      title: json['title'],
      description: json['description'],
      startTime: DateTime.parse(json['startTime']),
      endTime: DateTime.parse(json['endTime']),
      location: json['location'],
      eventType: json['eventType'] ?? 'OTHER',
      isFixed: json['isFixed'] ?? false,
      color: json['color'],
      notes: json['notes'],
      relatedTaskId: json['relatedTaskId'],
      relatedHabitId: json['relatedHabitId'],
      relatedWorkoutId: json['relatedWorkoutId'],
      relatedProjectId: json['relatedProjectId'],
      completedAt: json['completedAt'] != null
          ? DateTime.parse(json['completedAt'])
          : null,
      skippedAt: json['skippedAt'] != null
          ? DateTime.parse(json['skippedAt'])
          : null,
      relatedTaskDeadline: json['relatedTaskDeadline'] != null
          ? DateTime.parse(json['relatedTaskDeadline'])
          : null,
      relatedTaskPriority: json['relatedTaskPriority'],
    );
  }

  // CalendarEvent zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'startTime': startTime.toIso8601String(),
      'endTime': endTime.toIso8601String(),
      'location': location,
      'eventType': eventType,
      'isFixed': isFixed,
      'color': color,
      'notes': notes,
      'relatedTaskId': relatedTaskId,
      'relatedHabitId': relatedHabitId,
      'relatedWorkoutId': relatedWorkoutId,
      'relatedProjectId': relatedProjectId,
      'completedAt': completedAt?.toIso8601String(),
      // relatedTaskDeadline/-Priority fehlen bewusst: sie gehoeren dem Task, das Backend liest
      // sie beim Speichern eines Termins ohnehin nicht (CalendarEventMapper.toEntity).
    };
  }

  // Copy with
  CalendarEvent copyWith({
    int? id,
    String? title,
    String? description,
    DateTime? startTime,
    DateTime? endTime,
    String? location,
    String? eventType,
    bool? isFixed,
    String? color,
    String? notes,
    int? relatedTaskId,
    int? relatedHabitId,
    int? relatedWorkoutId,
    int? relatedProjectId,
    DateTime? completedAt,
    DateTime? skippedAt,
    DateTime? relatedTaskDeadline,
    int? relatedTaskPriority,
  }) {
    return CalendarEvent(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
      location: location ?? this.location,
      eventType: eventType ?? this.eventType,
      isFixed: isFixed ?? this.isFixed,
      color: color ?? this.color,
      notes: notes ?? this.notes,
      relatedTaskId: relatedTaskId ?? this.relatedTaskId,
      relatedHabitId: relatedHabitId ?? this.relatedHabitId,
      relatedWorkoutId: relatedWorkoutId ?? this.relatedWorkoutId,
      relatedProjectId: relatedProjectId ?? this.relatedProjectId,
      completedAt: completedAt ?? this.completedAt,
      skippedAt: skippedAt ?? this.skippedAt,
      relatedTaskDeadline: relatedTaskDeadline ?? this.relatedTaskDeadline,
      relatedTaskPriority: relatedTaskPriority ?? this.relatedTaskPriority,
    );
  }

  
  int get durationInMinutes {
    return endTime.difference(startTime).inMinutes;
  }

 
  Color get colorObject {
    if (color == null) return Colors.blue;
    
    try {
      // Entferne '#' falls vorhanden
      String colorString = color!.replaceAll('#', '');
      // Füge Alpha Wert hinzu
      return Color(int.parse('FF$colorString', radix: 16));
    } catch (e) {
      return Colors.blue;
    }
  }

 
  /// Eine Vorlesung aus dem Stundenplan. Sie wird bei jeder Neuplanung neu erzeugt und
  /// deshalb im Kalender nur angezeigt, nicht bearbeitet — das Backend weist Ändern, Pinnen
  /// und Löschen mit 400 ab.
  bool get isClass => eventType.toUpperCase() == 'CLASS';

  /// Nicht verschiebbar: entweder vom Nutzer gepinnt oder aus dem Stundenplan abgeleitet.
  bool get isLocked => isFixed || isClass;

  /// Ein Aufgabenblock — nur diese lassen sich im Kalender abhaken. Gewohnheiten und Workouts
  /// haben ihre eigenen Abschlusswege.
  bool get isTask => eventType.toUpperCase() == 'TASK';

  bool get isCompleted => completedAt != null;

  bool get isSkipped => skippedAt != null;

  /// Quotenbasierte Bloecke: nur sie lassen sich ueberspringen. Ein Aufgabenblock ist ein
  /// Bruchteil einer Aufgabe — seine Restminuten wollen auch danach verplant werden, dafuer
  /// gibt es Verschieben oder Abhaken.
  bool get isSkippable {
    final t = eventType.toUpperCase();
    return t == 'HABIT' || t == 'PROJECT' || t == 'WORKOUT';
  }

  bool get isToday {
    final now = DateTime.now();
    return startTime.year == now.year &&
           startTime.month == now.month &&
           startTime.day == now.day;
  }

  
  bool get isPast => endTime.isBefore(DateTime.now());

  
  bool get isOngoing {
    final now = DateTime.now();
    return startTime.isBefore(now) && endTime.isAfter(now);
  }

  @override
  String toString() => 'CalendarEvent(id: $id, title: $title, type: $eventType)';
}