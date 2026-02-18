
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
      print('Error fetching tasks: $e');
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
      print('Error fetching task: $e');
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
      print('Error fetching tasks by status: $e');
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
      print('Error fetching unscheduled tasks: $e');
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
      print('Error creating task: $e');
      return null;
    }
  }

  /// Update task
  Future<Task?> updateTask(Task task) async {
    try {
      if (task.id == null) {
        throw Exception('Task ID is required for update');
      }

      final response = await _apiService.put(
        ApiConfig.taskById(task.id!),
        task.toJson(),
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Task.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      print('Error updating task: $e');
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
      print('Error completing task: $e');
      return false;
    }
  }

  /// Delete task
  Future<bool> deleteTask(int id) async {
    try {
      final response = await _apiService.delete(ApiConfig.taskById(id));
      return _apiService.isSuccess(response);
    } catch (e) {
      print('Error deleting task: $e');
      return false;
    }
  }
}