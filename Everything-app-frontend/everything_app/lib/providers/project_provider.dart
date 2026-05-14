import 'package:flutter/material.dart';
import '../models/project.dart';
import '../services/project_service.dart';

class ProjectProvider with ChangeNotifier {
  final ProjectService _projectService = ProjectService();

  List<Project> _projects = [];
  bool _isLoading = false;
  String? _error;

  List<Project> get projects => _projects;
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> loadProjects() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _projects = await _projectService.getAllProjects();
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden der Projekte: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> addProject(Project project) async {
    try {
      final created = await _projectService.createProject(project);
      if (created != null) {
        _projects.add(created);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Erstellen des Projekts: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> updateProject(Project project) async {
    try {
      final updated = await _projectService.updateProject(project);
      if (updated != null) {
        final index = _projects.indexWhere((p) => p.id == updated.id);
        if (index != -1) {
          _projects[index] = updated;
        } else {
          _projects.add(updated);
        }
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Aktualisieren des Projekts: $e';
      notifyListeners();
      return false;
    }
  }

  Future<bool> deleteProject(int id) async {
    try {
      final success = await _projectService.deleteProject(id);
      if (success) {
        _projects.removeWhere((p) => p.id == id);
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      _error = 'Fehler beim Löschen des Projekts: $e';
      notifyListeners();
      return false;
    }
  }
}
