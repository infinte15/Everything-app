import 'package:flutter/material.dart';
import '../models/task.dart';
import '../services/task_service.dart';

class TaskProvider with ChangeNotifier {
  final TaskService _taskService = TaskService();
  
  List<Task> _tasks = [];
  bool _isLoading = false;
  String? _error;

  // Getters
  List<Task> get tasks => _tasks;
  bool get isLoading => _isLoading;
  String? get error => _error;

  // Filtered Lists
  List<Task> get todoTasks => 
      _tasks.where((t) => t.status == 'TODO').toList();
  
  List<Task> get inProgressTasks => 
      _tasks.where((t) => t.status == 'IN_PROGRESS').toList();
  
  List<Task> get completedTasks => 
      _tasks.where((t) => t.status == 'COMPLETED').toList();

  List<Task> get todayTasks {
    final now = DateTime.now();
    return _tasks.where((t) {
      if (t.deadline == null) return false;
      return t.deadline!.year == now.year &&
             t.deadline!.month == now.month &&
             t.deadline!.day == now.day;
    }).toList();
  }

  List<Task> get overdueTasks => 
      _tasks.where((t) => t.isOverdue).toList();


  Future<void> loadTasks() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _tasks = await _taskService.getAllTasks();
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Tasks: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  
  Future<bool> addTask(Task task) async {
    try {
      final created = await _taskService.createTask(task);
      
      if (created != null) {
        _tasks.add(created);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Erstellen der Task: $e';
      notifyListeners();
      return false;
    }
  }


  Future<bool> updateTask(Task task) async {
    try {
      final updated = await _taskService.updateTask(task);
      
      if (updated != null) {
        final index = _tasks.indexWhere((t) => t.id == task.id);
        if (index != -1) {
          _tasks[index] = updated;
          notifyListeners();
        }
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Aktualisieren der Task: $e';
      notifyListeners();
      return false;
    }
  }

 
  Future<bool> completeTask(int id) async {
    try {
      final success = await _taskService.completeTask(id);
      
      if (success) {
        await loadTasks(); // Reload to get updated data
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Abschließen der Task: $e';
      notifyListeners();
      return false;
    }
  }

  
  Future<bool> deleteTask(int id) async {
    try {
      final success = await _taskService.deleteTask(id);
      
      if (success) {
        _tasks.removeWhere((t) => t.id == id);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Löschen der Task: $e';
      notifyListeners();
      return false;
    }
  }

  
  Task? getTaskById(int id) {
    try {
      return _tasks.firstWhere((t) => t.id == id);
    } catch (e) {
      return null;
    }
  }

  
  void clearError() {
    _error = null;
    notifyListeners();
  }
}