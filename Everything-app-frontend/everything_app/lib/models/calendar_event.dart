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