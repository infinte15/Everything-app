
class Habit {
  final int? id;
  final String name;
  final String? description;
  final String frequency; 
  
  final bool monday;
  final bool tuesday;
  final bool wednesday;
  final bool thursday;
  final bool friday;
  final bool saturday;
  final bool sunday;
  
  final DateTime? preferredTime;
  final int? durationMinutes;
  final DateTime? startDate;
  final DateTime? endDate;
  final int currentStreak;
  final int longestStreak;

  Habit({
    this.id,
    required this.name,
    this.description,
    this.frequency = 'DAILY',
    this.monday = false,
    this.tuesday = false,
    this.wednesday = false,
    this.thursday = false,
    this.friday = false,
    this.saturday = false,
    this.sunday = false,
    this.preferredTime,
    this.durationMinutes,
    this.startDate,
    this.endDate,
    this.currentStreak = 0,
    this.longestStreak = 0,
  });

  // JSON zu Habit
  factory Habit.fromJson(Map<String, dynamic> json) {
    return Habit(
      id: json['id'],
      name: json['name'],
      description: json['description'],
      frequency: json['frequency'] ?? 'DAILY',
      monday: json['monday'] ?? false,
      tuesday: json['tuesday'] ?? false,
      wednesday: json['wednesday'] ?? false,
      thursday: json['thursday'] ?? false,
      friday: json['friday'] ?? false,
      saturday: json['saturday'] ?? false,
      sunday: json['sunday'] ?? false,
      preferredTime: json['preferredTime'] != null
          ? DateTime.parse('2000-01-01 ${json['preferredTime']}')
          : null,
      durationMinutes: json['durationMinutes'],
      startDate: json['startDate'] != null
          ? DateTime.parse(json['startDate'])
          : null,
      endDate: json['endDate'] != null
          ? DateTime.parse(json['endDate'])
          : null,
      currentStreak: json['currentStreak'] ?? 0,
      longestStreak: json['longestStreak'] ?? 0,
    );
  }

  // Habit zu JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'description': description,
      'frequency': frequency,
      'monday': monday,
      'tuesday': tuesday,
      'wednesday': wednesday,
      'thursday': thursday,
      'friday': friday,
      'saturday': saturday,
      'sunday': sunday,
      'preferredTime': preferredTime != null
          ? '${preferredTime!.hour.toString().padLeft(2, '0')}:${preferredTime!.minute.toString().padLeft(2, '0')}'
          : null,
      'durationMinutes': durationMinutes,
      'startDate': startDate?.toIso8601String(),
      'endDate': endDate?.toIso8601String(),
      'currentStreak': currentStreak,
      'longestStreak': longestStreak,
    };
  }

  bool isScheduledToday() {
    final now = DateTime.now();
    final weekday = now.weekday; 
    
    switch (weekday) {
      case 1: return monday;
      case 2: return tuesday;
      case 3: return wednesday;
      case 4: return thursday;
      case 5: return friday;
      case 6: return saturday;
      case 7: return sunday;
      default: return false;
    }
  }

  int get daysPerWeek {
    int count = 0;
    if (monday) count++;
    if (tuesday) count++;
    if (wednesday) count++;
    if (thursday) count++;
    if (friday) count++;
    if (saturday) count++;
    if (sunday) count++;
    return count;
  }

  @override
  String toString() => 'Habit(id: $id, name: $name, streak: $currentStreak)';
}