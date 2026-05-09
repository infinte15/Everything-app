import 'package:flutter/material.dart';
import '../models/habit.dart';
import '../services/habit_service.dart';

class HabitProvider with ChangeNotifier {
  final HabitService _habitService = HabitService();
  
  List<Habit> _habits = [];
  bool _isLoading = false;
  String? _error;

  List<Habit> get habits => _habits;
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> loadHabits() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _habits = await _habitService.getAllHabits();
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Habits: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> addHabit(Habit habit) async {
    try {
      final created = await _habitService.createHabit(habit);
      if (created != null) {
        _habits.add(created);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Erstellen des Habits: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> completeHabit(int id, {DateTime? date}) async {
    try {
      final success = await _habitService.completeHabit(id, date: date);
      if (success) {
        await loadHabits();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Abschließen des Habits: $e';
      notifyListeners();
      return false;
    }
  }
}
