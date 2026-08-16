import 'package:flutter/material.dart';

/// Nutzereinstellungen, inklusive der Parameter, mit denen der Smart Scheduler plant.
class UserPreferences {
  final int? id;

  final TimeOfDay? workdayStart;
  final TimeOfDay? workdayEnd;
  final String? peakProductivityTime; // MORNING | AFTERNOON | EVENING

  final int? breakDurationMinutes;
  final int? hoursBeforeBreak;
  final bool? groupSimilarTasks;
  final int? maxTasksPerDay;

  final bool? notificationsEnabled;
  final int? reminderMinutesBefore;

  final String? themeColor;
  final bool? darkMode;

  // --- Scheduling ---
  final int? bufferMinutes;
  final int? maxTaskMinutesPerDay;

  /// Deckel ueber ALLES, was pro Tag automatisch geplant wird — nicht nur ueber die Aufgaben.
  /// Ohne ihn konnten Gewohnheiten, Trainings und Projektzeit einen Tag fuellen, bevor die
  /// Aufgaben ueberhaupt an die Reihe kamen.
  final int? maxScheduledMinutesPerDay;

  /// Ende der Kernzeit. Aufgaben danach sind erlaubt, kosten den Scheduler aber etwas — sonst
  /// waere 21:00 bei einem Arbeitsende von 22:00 eine gleichwertige Lage.
  final TimeOfDay? coreHoursEnd;

  /// Privatzeiten — der Rahmen fuer Gewohnheiten und Trainings ("Personal Hours").
  ///
  /// Getrennt von den Arbeitszeiten, weil beides verschiedene Fragen beantwortet. Solange
  /// Gewohnheiten an der Arbeitszeit hingen, war ihr Wunschfenster bei 08:00–17:00 unerreichbar:
  /// eine Abend-Gewohnheit landete mitten im Nachmittag.
  final TimeOfDay? personalHoursStart;
  final TimeOfDay? personalHoursEnd;

  final int? defaultMinChunkMinutes;
  final int? defaultMaxChunkMinutes;
  final bool? autoScheduleEnabled;

  const UserPreferences({
    this.id,
    this.workdayStart,
    this.workdayEnd,
    this.peakProductivityTime,
    this.breakDurationMinutes,
    this.hoursBeforeBreak,
    this.groupSimilarTasks,
    this.maxTasksPerDay,
    this.notificationsEnabled,
    this.reminderMinutesBefore,
    this.themeColor,
    this.darkMode,
    this.bufferMinutes,
    this.maxTaskMinutesPerDay,
    this.maxScheduledMinutesPerDay,
    this.coreHoursEnd,
    this.personalHoursStart,
    this.personalHoursEnd,
    this.defaultMinChunkMinutes,
    this.defaultMaxChunkMinutes,
    this.autoScheduleEnabled,
  });

  /// Jackson liefert LocalTime als "HH:mm:ss" (siehe write-dates-as-timestamps=false).
  static TimeOfDay? _parseTime(dynamic value) {
    if (value is! String || value.isEmpty) return null;
    final parts = value.split(':');
    if (parts.length < 2) return null;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) return null;
    return TimeOfDay(hour: h, minute: m);
  }

  static String? _formatTime(TimeOfDay? t) {
    if (t == null) return null;
    final h = t.hour.toString().padLeft(2, '0');
    final m = t.minute.toString().padLeft(2, '0');
    return '$h:$m:00';
  }

  factory UserPreferences.fromJson(Map<String, dynamic> json) {
    return UserPreferences(
      id: json['id'],
      workdayStart: _parseTime(json['workdayStart']),
      workdayEnd: _parseTime(json['workdayEnd']),
      peakProductivityTime: json['peakProductivityTime'],
      breakDurationMinutes: json['breakDurationMinutes'],
      hoursBeforeBreak: json['hoursBeforeBreak'],
      groupSimilarTasks: json['groupSimilarTasks'],
      maxTasksPerDay: json['maxTasksPerDay'],
      notificationsEnabled: json['notificationsEnabled'],
      reminderMinutesBefore: json['reminderMinutesBefore'],
      themeColor: json['themeColor'],
      darkMode: json['darkMode'],
      bufferMinutes: json['bufferMinutes'],
      maxTaskMinutesPerDay: json['maxTaskMinutesPerDay'],
      maxScheduledMinutesPerDay: json['maxScheduledMinutesPerDay'],
      coreHoursEnd: _parseTime(json['coreHoursEnd']),
      personalHoursStart: _parseTime(json['personalHoursStart']),
      personalHoursEnd: _parseTime(json['personalHoursEnd']),
      defaultMinChunkMinutes: json['defaultMinChunkMinutes'],
      defaultMaxChunkMinutes: json['defaultMaxChunkMinutes'],
      autoScheduleEnabled: json['autoScheduleEnabled'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'workdayStart': _formatTime(workdayStart),
      'workdayEnd': _formatTime(workdayEnd),
      'peakProductivityTime': peakProductivityTime,
      'breakDurationMinutes': breakDurationMinutes,
      'hoursBeforeBreak': hoursBeforeBreak,
      'groupSimilarTasks': groupSimilarTasks,
      'maxTasksPerDay': maxTasksPerDay,
      'notificationsEnabled': notificationsEnabled,
      'reminderMinutesBefore': reminderMinutesBefore,
      'themeColor': themeColor,
      'darkMode': darkMode,
      'bufferMinutes': bufferMinutes,
      'maxTaskMinutesPerDay': maxTaskMinutesPerDay,
      'maxScheduledMinutesPerDay': maxScheduledMinutesPerDay,
      'coreHoursEnd': _formatTime(coreHoursEnd),
      'personalHoursStart': _formatTime(personalHoursStart),
      'personalHoursEnd': _formatTime(personalHoursEnd),
      'defaultMinChunkMinutes': defaultMinChunkMinutes,
      'defaultMaxChunkMinutes': defaultMaxChunkMinutes,
      'autoScheduleEnabled': autoScheduleEnabled,
    };
  }

  UserPreferences copyWith({
    TimeOfDay? workdayStart,
    TimeOfDay? workdayEnd,
    String? peakProductivityTime,
    int? breakDurationMinutes,
    int? hoursBeforeBreak,
    bool? groupSimilarTasks,
    int? maxTasksPerDay,
    bool? notificationsEnabled,
    int? reminderMinutesBefore,
    String? themeColor,
    bool? darkMode,
    int? bufferMinutes,
    int? maxTaskMinutesPerDay,
    int? maxScheduledMinutesPerDay,
    TimeOfDay? coreHoursEnd,
    TimeOfDay? personalHoursStart,
    TimeOfDay? personalHoursEnd,
    int? defaultMinChunkMinutes,
    int? defaultMaxChunkMinutes,
    bool? autoScheduleEnabled,
  }) {
    return UserPreferences(
      id: id,
      workdayStart: workdayStart ?? this.workdayStart,
      workdayEnd: workdayEnd ?? this.workdayEnd,
      peakProductivityTime: peakProductivityTime ?? this.peakProductivityTime,
      breakDurationMinutes: breakDurationMinutes ?? this.breakDurationMinutes,
      hoursBeforeBreak: hoursBeforeBreak ?? this.hoursBeforeBreak,
      groupSimilarTasks: groupSimilarTasks ?? this.groupSimilarTasks,
      maxTasksPerDay: maxTasksPerDay ?? this.maxTasksPerDay,
      notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
      reminderMinutesBefore: reminderMinutesBefore ?? this.reminderMinutesBefore,
      themeColor: themeColor ?? this.themeColor,
      darkMode: darkMode ?? this.darkMode,
      bufferMinutes: bufferMinutes ?? this.bufferMinutes,
      maxTaskMinutesPerDay: maxTaskMinutesPerDay ?? this.maxTaskMinutesPerDay,
      maxScheduledMinutesPerDay: maxScheduledMinutesPerDay ?? this.maxScheduledMinutesPerDay,
      coreHoursEnd: coreHoursEnd ?? this.coreHoursEnd,
      personalHoursStart: personalHoursStart ?? this.personalHoursStart,
      personalHoursEnd: personalHoursEnd ?? this.personalHoursEnd,
      defaultMinChunkMinutes: defaultMinChunkMinutes ?? this.defaultMinChunkMinutes,
      defaultMaxChunkMinutes: defaultMaxChunkMinutes ?? this.defaultMaxChunkMinutes,
      autoScheduleEnabled: autoScheduleEnabled ?? this.autoScheduleEnabled,
    );
  }
}
