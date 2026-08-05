import 'package:flutter/material.dart';
import '../models/calendar_event.dart';
import '../models/project.dart';
import '../models/task.dart';
import '../services/project_service.dart';

class ProjectProvider with ChangeNotifier {
  // Injizierbar, damit der Detail-Screen ohne Backend testbar bleibt.
  final ProjectService _projectService;

  ProjectProvider({ProjectService? service}) : _projectService = service ?? ProjectService();

  List<Project> _projects = [];
  bool _isLoading = false;
  String? _error;

  // Detail-Zustand: gehoert genau einem Projekt, wird beim Oeffnen eines anderen ersetzt.
  int? _detailProjectId;
  List<Task> _detailTasks = [];
  List<CalendarEvent> _detailSessions = [];
  bool _isDetailLoading = false;

  List<Project> get projects => _projects;
  bool get isLoading => _isLoading;
  String? get error => _error;

  List<Task> get detailTasks => _detailTasks;
  List<CalendarEvent> get detailSessions => _detailSessions;
  bool get isDetailLoading => _isDetailLoading;

  /// Alle Projekte einer Board-Spalte. [statuses] ist bewusst eine Liste: die Spalte "Aktiv"
  /// nimmt ACTIVE und IN_PROGRESS auf, "Fertig" auch CANCELLED — sonst verschwinden Projekte
  /// lautlos vom Board.
  List<Project> byStatus(List<String> statuses) =>
      _projects.where((p) => statuses.contains(p.status)).toList();

  Project? projectById(int id) {
    for (final p in _projects) {
      if (p.id == id) return p;
    }
    return null;
  }

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

  /// Laedt Projekt, Aufgaben und kommende Bloecke fuer den Detail-Screen.
  Future<void> loadProjectDetail(int projectId) async {
    _detailProjectId = projectId;
    _isDetailLoading = true;
    notifyListeners();

    try {
      final results = await Future.wait([
        _projectService.getProject(projectId),
        _projectService.getProjectTasks(projectId),
        _projectService.getProjectSessions(projectId),
      ]);

      // Zwischenzeitlicher Wechsel auf ein anderes Projekt: Antwort verwerfen, sonst
      // ueberschreibt die langsamere Anfrage die neuere.
      if (_detailProjectId != projectId) return;

      final project = results[0] as Project?;
      if (project != null) _mergeProject(project);
      _detailTasks = results[1] as List<Task>;
      _detailSessions = results[2] as List<CalendarEvent>;
      _error = null;
    } catch (e) {
      _error = 'Fehler beim Laden des Projekts: $e';
    }

    _isDetailLoading = false;
    notifyListeners();
  }

  void clearProjectDetail() {
    _detailProjectId = null;
    _detailTasks = [];
    _detailSessions = [];
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
        _mergeProject(updated);
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
        if (_detailProjectId == id) clearProjectDetail();
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

  /// Ordnet eine Aufgabe zu oder entkoppelt sie ([projectId] null) und laedt danach neu —
  /// den Fortschritt rechnet der Server aus den Aufgaben.
  Future<bool> assignTask(int taskId, int? projectId) async {
    final ok = await _projectService.assignTaskToProject(taskId, projectId);
    if (!ok) {
      _error = 'Aufgabe konnte nicht zugeordnet werden';
      notifyListeners();
      return false;
    }
    if (_detailProjectId != null) await loadProjectDetail(_detailProjectId!);
    return true;
  }

  void _mergeProject(Project project) {
    final index = _projects.indexWhere((p) => p.id == project.id);
    if (index != -1) {
      _projects[index] = project;
    } else {
      _projects.add(project);
    }
  }
}
