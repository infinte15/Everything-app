
import 'package:flutter/foundation.dart';
import '../config/api_config.dart';
import '../models/task.dart';
import 'api_service.dart';

/// Task Service
/// 
/// CRUD Operationen für Tasks
class TaskService {
  final ApiService _apiService = ApiService();

  /// Get all tasks
  Future<List<Task>> getAllTasks() async {
    try {
      final response = await _apiService.get(ApiConfig.tasks);

      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Task.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching tasks: $e');
      return [];
    }
  }

  /// Get task by ID
  Future<Task?> getTaskById(int id) async {
    try {
      final response = await _apiService.get(ApiConfig.taskById(id));

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Task.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching task: $e');
      return null;
    }
  }

  /// Get tasks by status
  Future<List<Task>> getTasksByStatus(String status) async {
    try {
      final response = await _apiService.get(ApiConfig.tasksByStatus(status));

      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Task.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching tasks by status: $e');
      return [];
    }
  }

  /// Get unscheduled tasks
  Future<List<Task>> getUnscheduledTasks() async {
    try {
      final response = await _apiService.get(ApiConfig.unscheduledTasks);

      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Task.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching unscheduled tasks: $e');
      return [];
    }
  }

  /// Create task
  Future<Task?> createTask(Task task) async {
    try {
      final response = await _apiService.post(
        ApiConfig.tasks,
        task.toJson(),
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Task.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error creating task: $e');
      return null;
    }
  }

  /// Update task
  /// [clear] nennt die Felder, die ausdruecklich GELEERT werden sollen.
  ///
  /// Der Backend-Endpunkt patcht mit "null heisst unveraendert" — sonst raeumte das Abhaken eines
  /// Kalenderblocks die halbe Aufgabe leer. Eine Deadline zu ENTFERNEN laesst sich darin nicht
  /// ausdruecken; dafuer ist diese Liste da. Erlaubte Werte spiegeln das Enum
  /// `TaskClearableField` im Backend: DEADLINE, NOT_BEFORE, MIN_CHUNK_MINUTES,
  /// MAX_CHUNK_MINUTES, MAX_CHUNKS_PER_DAY, DESCRIPTION. Ein unbekannter Wert wird mit 400
  /// abgewiesen, nicht stillschweigend geschluckt.
  ///
  /// Bewusst ein Parameter der ANFRAGE und kein Feld am Modell: [Task.toJson] soll weiterhin alle
  /// Felder senden. Ein toJson, das Nullen weglaesst, loeste das Problem nicht (weggelassen =
  /// abwesend = unveraendert) und verschoebe still die Bedeutung beim Anlegen.
  Future<Task?> updateTask(Task task, {Set<String> clear = const {}}) async {
    try {
      if (task.id == null) {
        throw Exception('Task ID is required for update');
      }

      final response = await _apiService.put(
        ApiConfig.taskById(task.id!),
        {
          ...task.toJson(),
          if (clear.isNotEmpty) 'clearFields': clear.toList(),
        },
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Task.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error updating task: $e');
      return null;
    }
  }

  /// Complete task
  Future<bool> completeTask(int id) async {
    try {
      final response = await _apiService.put(
        ApiConfig.completeTask(id),
        {},
      );

      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error completing task: $e');
      return false;
    }
  }

  /// Delete task
  Future<bool> deleteTask(int id) async {
    try {
      final response = await _apiService.delete(ApiConfig.taskById(id));
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting task: $e');
      return false;
    }
  }
}