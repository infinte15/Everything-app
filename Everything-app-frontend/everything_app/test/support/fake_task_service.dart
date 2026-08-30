import 'package:everything_app/models/task.dart';
import 'package:everything_app/services/task_service.dart';

/// Attrappe fuer [TaskService], nach dem Muster von `fake_calendar_service.dart`.
///
/// Ueberschreibt jede Methode, die die Tests anfassen. Ein vergessener Override laesst die echte
/// Netzfassung laufen — und die haengt in `testWidgets` ohne jede Ausgabe, statt zu scheitern.
class FakeTaskService extends TaskService {
  FakeTaskService([List<Task>? initial]) : tasks = List.of(initial ?? const []);

  final List<Task> tasks;

  int updateCallCount = 0;
  Task? lastUpdated;

  /// Die zuletzt mitgeschickte Liste zu leerender Felder — das eigentliche Pruefobjekt fuer das
  /// Bearbeiten-Sheet.
  Set<String>? lastClear;

  int createCallCount = 0;
  Task? lastCreated;

  bool failUpdates = false;

  @override
  Future<List<Task>> getAllTasks() async => List.of(tasks);

  @override
  Future<Task?> getTaskById(int id) async {
    for (final t in tasks) {
      if (t.id == id) return t;
    }
    return null;
  }

  @override
  Future<Task?> createTask(Task task) async {
    createCallCount++;
    lastCreated = task;
    final gespeichert = task.copyWith(id: task.id ?? tasks.length + 1);
    tasks.add(gespeichert);
    return gespeichert;
  }

  @override
  Future<Task?> updateTask(Task task, {Set<String> clear = const {}}) async {
    updateCallCount++;
    lastUpdated = task;
    lastClear = clear;
    if (failUpdates) return null;

    final i = tasks.indexWhere((t) => t.id == task.id);
    if (i != -1) tasks[i] = task;
    return task;
  }

  @override
  Future<bool> deleteTask(int id) async {
    tasks.removeWhere((t) => t.id == id);
    return true;
  }

  @override
  Future<bool> completeTask(int id) async => true;
}
