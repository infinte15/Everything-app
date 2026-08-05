import 'package:flutter/foundation.dart';
import '../config/api_config.dart';
import '../models/calendar_event.dart';
import '../models/project.dart';
import '../models/task.dart';
import 'api_service.dart';

class ProjectService {
  final ApiService _apiService = ApiService();

  Future<List<Project>> getAllProjects() async {
    try {
      final response = await _apiService.get(ApiConfig.projects);
      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Project.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching projects: $e');
      return [];
    }
  }

  Future<Project?> getProject(int id) async {
    try {
      final response = await _apiService.get(ApiConfig.projectById(id));
      if (_apiService.isSuccess(response)) {
        return Project.fromJson(_apiService.parseResponse(response));
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching project: $e');
      return null;
    }
  }

  /// Aufgaben des Projekts — Quelle des Fortschritts.
  Future<List<Task>> getProjectTasks(int id) async {
    try {
      final response = await _apiService.get(ApiConfig.projectTasks(id));
      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => Task.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching project tasks: $e');
      return [];
    }
  }

  /// Kommende Projektbloecke aus dem Kalender. Ohne Zeitraum liefert der Server die
  /// naechsten 14 Tage ab jetzt.
  Future<List<CalendarEvent>> getProjectSessions(int id, {DateTime? from, DateTime? to}) async {
    try {
      final params = <String>[];
      if (from != null) params.add('from=${from.toIso8601String()}');
      if (to != null) params.add('to=${to.toIso8601String()}');
      final url = params.isEmpty
          ? ApiConfig.projectSessions(id)
          : '${ApiConfig.projectSessions(id)}?${params.join('&')}';

      final response = await _apiService.get(url);
      if (_apiService.isSuccess(response)) {
        final List<dynamic> data = _apiService.parseResponse(response);
        return data.map((json) => CalendarEvent.fromJson(json)).toList();
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error fetching project sessions: $e');
      return [];
    }
  }

  Future<Project?> createProject(Project project) async {
    try {
      final response = await _apiService.post(ApiConfig.projects, project.toJson());
      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Project.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error creating project: $e');
      return null;
    }
  }

  Future<Project?> updateProject(Project project) async {
    try {
      final response = await _apiService.put(ApiConfig.projectById(project.id!), project.toJson());
      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        return Project.fromJson(data);
      } else {
        throw Exception(_apiService.getErrorMessage(response));
      }
    } catch (e) {
      debugPrint('Error updating project: $e');
      return null;
    }
  }

  Future<bool> deleteProject(int id) async {
    try {
      final response = await _apiService.delete(ApiConfig.projectById(id));
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting project: $e');
      return false;
    }
  }

  /// [projectId] null entkoppelt die Aufgabe von ihrem Projekt — die Patch-Semantik von
  /// PUT /tasks/{id} kann "leeren" nicht ausdruecken.
  Future<bool> assignTaskToProject(int taskId, int? projectId) async {
    try {
      final response = await _apiService.put(ApiConfig.taskProject(taskId), {'projectId': projectId});
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error assigning task to project: $e');
      return false;
    }
  }
}
