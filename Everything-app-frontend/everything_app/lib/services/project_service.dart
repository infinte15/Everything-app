import 'package:flutter/foundation.dart';
import '../models/project.dart';
import 'api_service.dart';

class ProjectService {
  final ApiService _apiService = ApiService();

  Future<List<Project>> getAllProjects() async {
    try {
      final response = await _apiService.get('/api/projects');
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

  Future<Project?> createProject(Project project) async {
    try {
      final response = await _apiService.post('/api/projects', project.toJson());
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
      final response = await _apiService.put('/api/projects/${project.id}', project.toJson());
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
      final response = await _apiService.delete('/api/projects/$id');
      return _apiService.isSuccess(response);
    } catch (e) {
      debugPrint('Error deleting project: $e');
      return false;
    }
  }
}
