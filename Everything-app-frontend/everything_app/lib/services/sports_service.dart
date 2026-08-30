import 'dart:convert';

import '../config/api_config.dart';
import '../models/gym/gym_models.dart';
import 'api_service.dart';

/// Ergebnis einer Katalog-Seite.
class GymExercisePage {
  final List<GymExercise> content;
  final int page;
  final int totalElements;
  final bool last;

  const GymExercisePage({
    this.content = const [],
    this.page = 0,
    this.totalElements = 0,
    this.last = true,
  });

  factory GymExercisePage.fromMap(Map<String, dynamic> m) => GymExercisePage(
        content: (m['content'] as List? ?? [])
            .whereType<Map>()
            .map((e) => GymExercise.fromMap(Map<String, dynamic>.from(e)))
            .toList(),
        page: m['page'] as int? ?? 0,
        totalElements: m['totalElements'] as int? ?? 0,
        last: m['last'] as bool? ?? true,
      );
}

/// Belegte Filterwerte des Katalogs.
class GymExerciseFilters {
  final List<String> equipment;
  final List<String> categories;
  final List<String> difficulties;

  const GymExerciseFilters({
    this.equipment = const [],
    this.categories = const [],
    this.difficulties = const [],
  });

  factory GymExerciseFilters.fromMap(Map<String, dynamic> m) => GymExerciseFilters(
        equipment: (m['equipment'] as List? ?? []).map((e) => e.toString()).toList(),
        categories: (m['categories'] as List? ?? []).map((e) => e.toString()).toList(),
        difficulties: (m['difficulties'] as List? ?? []).map((e) => e.toString()).toList(),
      );
}

/// Wird geworfen, wenn das Backend einen Fehler meldet - der Provider macht daraus
/// eine Meldung für den Nutzer. Fehler werden bewusst nicht mehr verschluckt.
class SportsApiException implements Exception {
  final String message;
  const SportsApiException(this.message);

  @override
  String toString() => message;
}

class SportsService {
  final ApiService _api = ApiService();

  // ── Übungs-Katalog ───────────────────────────────────────────────────────────

  Future<GymExercisePage> searchExercises({
    String? search,
    String? muscle,
    String? equipment,
    String? category,
    String? difficulty,
    int page = 0,
    int size = 30,
  }) async {
    final query = <String, String>{
      if (search != null && search.isNotEmpty) 'search': search,
      if (muscle != null && muscle.isNotEmpty) 'muscle': muscle,
      if (equipment != null && equipment.isNotEmpty) 'equipment': equipment,
      if (category != null && category.isNotEmpty) 'category': category,
      if (difficulty != null && difficulty.isNotEmpty) 'difficulty': difficulty,
      'page': '$page',
      'size': '$size',
    };
    final url = Uri.parse(ApiConfig.exercises).replace(queryParameters: query).toString();
    return GymExercisePage.fromMap(await _getMap(url));
  }

  Future<GymExercise> getExercise(int id) async =>
      GymExercise.fromMap(await _getMap(ApiConfig.exerciseById(id)));

  Future<List<GymMuscleOption>> getMuscleGroups() async {
    final list = await _getList(ApiConfig.muscleGroups);
    return list.map(GymMuscleOption.fromMap).toList();
  }

  Future<GymExerciseFilters> getExerciseFilters() async =>
      GymExerciseFilters.fromMap(await _getMap(ApiConfig.exerciseFilters));

  Future<List<GymHistoryEntry>> getExerciseHistory(int exerciseId, {int limit = 20}) async {
    final list = await _getList(ApiConfig.exerciseHistory(exerciseId, limit: limit));
    return list.map(GymHistoryEntry.fromMap).toList();
  }

  Future<GymPersonalRecord> getPersonalRecords(int exerciseId) async =>
      GymPersonalRecord.fromMap(await _getMap(ApiConfig.exerciseRecords(exerciseId)));

  // ── Routinen ─────────────────────────────────────────────────────────────────

  Future<List<GymRoutine>> getRoutines() async {
    final list = await _getList(ApiConfig.routines);
    return list.map(GymRoutine.fromMap).toList();
  }

  Future<GymRoutine> getRoutine(int id) async =>
      GymRoutine.fromMap(await _getMap(ApiConfig.routineById(id)));

  Future<GymRoutine> createRoutine({
    required String name,
    String? description,
    String? imageUrl,
    String? dayLabel,
    int? preferredWeekday,
    int? estimatedDurationMinutes,
    int? workoutPlanId,
    required List<GymRoutineExercise> exercises,
  }) async {
    final body = _routineBody(
      name: name,
      description: description,
      imageUrl: imageUrl,
      dayLabel: dayLabel,
      preferredWeekday: preferredWeekday,
      estimatedDurationMinutes: estimatedDurationMinutes,
      workoutPlanId: workoutPlanId,
      exercises: exercises,
    );
    final response = await _api.post(ApiConfig.routines, body);
    return GymRoutine.fromMap(_decodeMap(response.body, response.statusCode, _api.isSuccess(response)));
  }

  Future<GymRoutine> updateRoutine({
    required int id,
    required String name,
    String? description,
    String? imageUrl,
    String? dayLabel,
    int? preferredWeekday,
    int? estimatedDurationMinutes,
    int? workoutPlanId,
    required List<GymRoutineExercise> exercises,
  }) async {
    final body = _routineBody(
      name: name,
      description: description,
      imageUrl: imageUrl,
      dayLabel: dayLabel,
      preferredWeekday: preferredWeekday,
      estimatedDurationMinutes: estimatedDurationMinutes,
      workoutPlanId: workoutPlanId,
      exercises: exercises,
    );
    final response = await _api.put(ApiConfig.routineById(id), body);
    return GymRoutine.fromMap(_decodeMap(response.body, response.statusCode, _api.isSuccess(response)));
  }

  Future<void> deleteRoutine(int id) async {
    final response = await _api.delete(ApiConfig.routineById(id));
    if (!_api.isSuccess(response)) {
      throw SportsApiException(_api.getErrorMessage(response));
    }
  }

  Map<String, dynamic> _routineBody({
    required String name,
    String? description,
    String? imageUrl,
    String? dayLabel,
    int? estimatedDurationMinutes,
    int? workoutPlanId,
    int? preferredWeekday,
    required List<GymRoutineExercise> exercises,
  }) =>
      {
        'name': name,
        'description': ?description,
        'imageUrl': ?imageUrl,
        'dayLabel': ?dayLabel,
        // Ohne "?" und damit immer im Body: null heißt hier "kein Wunschtag mehr" und muss
        // beim Server ankommen. Ein weggelassenes Feld könnte das nicht ausdrücken.
        'preferredWeekday': preferredWeekday,
        'estimatedDurationMinutes': ?estimatedDurationMinutes,
        'workoutPlanId': ?workoutPlanId,
        'exercises': exercises.map((e) => e.toRequest()).toList(),
      };

  // ── Laufendes Training ───────────────────────────────────────────────────────

  /// Startet ein Training und liefert die geplanten Übungen samt letzter Leistung.
  Future<Map<String, dynamic>> startWorkout({int? routineId, int? sessionId, String? name}) async {
    final response = await _api.post(ApiConfig.startWorkout, {
      'routineId': ?routineId,
      'sessionId': ?sessionId,
      'name': ?name,
    });
    return _decodeMap(response.body, response.statusCode, _api.isSuccess(response));
  }

  /// Speichert Einheit und alle Sätze in einem Request.
  Future<GymSession> finishWorkout({
    required int sessionId,
    required List<GymWorkoutExercise> exercises,
    String? notes,
    int? durationMinutes,
  }) async {
    final body = <String, dynamic>{
      'notes': ?notes,
      'durationMinutes': ?durationMinutes,
      'exercises': [
        for (var i = 0; i < exercises.length; i++) exercises[i].toRequest(i),
      ],
    };
    final response = await _api.post(ApiConfig.finishWorkout(sessionId), body);
    return GymSession.fromMap(
        _decodeMap(response.body, response.statusCode, _api.isSuccess(response)));
  }

  // ── Trainingseinheiten ───────────────────────────────────────────────────────

  Future<List<GymSession>> getSessions() async {
    final list = await _getList(ApiConfig.workoutSessions);
    return list.map(GymSession.fromMap).toList();
  }

  Future<GymSession> getSession(int id) async =>
      GymSession.fromMap(await _getMap(ApiConfig.workoutSessionById(id)));

  Future<void> deleteSession(int id) async {
    final response = await _api.delete(ApiConfig.workoutSessionById(id));
    if (!_api.isSuccess(response)) {
      throw SportsApiException(_api.getErrorMessage(response));
    }
  }

  // ── Auswertungen ─────────────────────────────────────────────────────────────

  Future<GymWeeklyStats> getWeeklyStats({DateTime? weekStart}) async {
    var url = ApiConfig.gymWeeklyStats;
    if (weekStart != null) {
      url = '$url?weekStart=${_isoDate(weekStart)}';
    }
    return GymWeeklyStats.fromMap(await _getMap(url));
  }

  Future<List<GymMuscleVolume>> getMuscleVolume({
    DateTime? startDate,
    DateTime? endDate,
  }) async {
    final params = <String>[
      if (startDate != null) 'startDate=${_isoDate(startDate)}',
      if (endDate != null) 'endDate=${_isoDate(endDate)}',
    ];
    final url = params.isEmpty
        ? ApiConfig.gymMuscleStats
        : '${ApiConfig.gymMuscleStats}?${params.join('&')}';

    final list = await _getList(url);
    return list.map(GymMuscleVolume.fromMap).toList();
  }

  /// Erholungsstand je Muskelgruppe. Ohne Zeitraum - die Frage ist immer "jetzt".
  Future<List<GymMuscleRecovery>> getRecovery() async {
    final list = await _getList(ApiConfig.gymRecovery);
    return list.map(GymMuscleRecovery.fromMap).toList();
  }

  // ── Stehende Übungsnotizen ───────────────────────────────────────────────────

  Future<String?> getExerciseNote(int exerciseId) async {
    final map = await _getMap(ApiConfig.exerciseNote(exerciseId));
    final text = map['text'] as String?;
    return text == null || text.isEmpty ? null : text;
  }

  /// Leerer Text löscht die Notiz - so hält es auch das Backend.
  Future<String?> saveExerciseNote(int exerciseId, String text) async {
    final response = await _api.put(ApiConfig.exerciseNote(exerciseId), {'text': text});
    final map = _decodeMap(response.body, response.statusCode, _api.isSuccess(response));
    final saved = map['text'] as String?;
    return saved == null || saved.isEmpty ? null : saved;
  }

  // ── Ausrüstungsprofile ───────────────────────────────────────────────────────

  Future<List<GymEquipmentProfile>> getEquipmentProfiles() async {
    final list = await _getList(ApiConfig.equipmentProfiles);
    return list.map(GymEquipmentProfile.fromMap).toList();
  }

  Future<GymEquipmentProfile> saveEquipmentProfile({
    int? id,
    required String name,
    required List<String> equipment,
  }) async {
    final body = {'name': name, 'equipment': equipment};
    final response = id == null
        ? await _api.post(ApiConfig.equipmentProfiles, body)
        : await _api.put(ApiConfig.equipmentProfileById(id), body);
    return GymEquipmentProfile.fromMap(
        _decodeMap(response.body, response.statusCode, _api.isSuccess(response)));
  }

  Future<void> deleteEquipmentProfile(int id) async {
    final response = await _api.delete(ApiConfig.equipmentProfileById(id));
    _ensureSuccess(response.body, response.statusCode, _api.isSuccess(response));
  }

  /// [id] null schaltet die Filterung ab.
  Future<void> activateEquipmentProfile(int? id) async {
    final response = await _api.put(ApiConfig.equipmentProfileActivate(id ?? 0), const {});
    _ensureSuccess(response.body, response.statusCode, _api.isSuccess(response));
  }

  // ── Körpergewicht ────────────────────────────────────────────────────────────

  Future<GymBodyWeightSeries> getBodyWeight({DateTime? from}) async {
    final url = from == null
        ? ApiConfig.bodyWeight
        : ApiConfig.bodyWeightSince(_isoDate(from));
    return GymBodyWeightSeries.fromMap(await _getMap(url));
  }

  /// Legt den Eintrag des Tages an - oder überschreibt ihn, wenn es schon einen gibt.
  Future<GymBodyWeightEntry> logBodyWeight(double weightKg, {DateTime? date, String? note}) async {
    final response = await _api.post(ApiConfig.bodyWeight, {
      'weightKg': weightKg,
      if (date != null) 'date': _isoDate(date),
      if (note != null && note.isNotEmpty) 'note': note,
    });
    return GymBodyWeightEntry.fromMap(
        _decodeMap(response.body, response.statusCode, _api.isSuccess(response)));
  }

  Future<void> deleteBodyWeight(int id) async {
    final response = await _api.delete(ApiConfig.bodyWeightById(id));
    if (!_api.isSuccess(response)) {
      throw SportsApiException(_api.getErrorMessage(response));
    }
  }

  /// Setzt das Zielgewicht. `null` entfernt es wieder.
  Future<double?> setBodyWeightTarget(double? targetKg) async {
    final response = await _api.put(ApiConfig.bodyWeightTarget, {'targetWeightKg': targetKg});
    final map = _decodeMap(response.body, response.statusCode, _api.isSuccess(response));
    return (map['targetWeightKg'] as num?)?.toDouble();
  }

  // ── Trainingspläne (für die Ziel-Vorgabe) ────────────────────────────────────

  Future<Map<String, dynamic>?> getActivePlan() async {
    final response = await _api.get(ApiConfig.activeWorkoutPlan);
    if (!_api.isSuccess(response) || response.body.isEmpty) return null;
    final decoded = json.decode(response.body);
    return decoded is Map<String, dynamic> ? decoded : null;
  }

  // ── Hilfsfunktionen ──────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> _getMap(String url) async {
    final response = await _api.get(url);
    return _decodeMap(response.body, response.statusCode, _api.isSuccess(response));
  }

  Future<List<Map<String, dynamic>>> _getList(String url) async {
    final response = await _api.get(url);
    if (!_api.isSuccess(response)) {
      throw SportsApiException(_api.getErrorMessage(response));
    }
    if (response.body.isEmpty) return [];
    final decoded = json.decode(response.body);
    if (decoded is! List) return [];
    return decoded.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }

  /// Für Antworten ohne Inhalt (204): nur prüfen, nicht dekodieren.
  void _ensureSuccess(String body, int statusCode, bool success) {
    if (!success) _decodeMap(body, statusCode, false);
  }

  Map<String, dynamic> _decodeMap(String body, int statusCode, bool success) {
    if (!success) {
      String message = 'Serverfehler ($statusCode)';
      try {
        final decoded = json.decode(body);
        if (decoded is Map && decoded['message'] != null) {
          message = decoded['message'].toString();
        }
      } catch (_) {
        // Antwort war kein JSON - die Standardmeldung reicht.
      }
      throw SportsApiException(message);
    }
    if (body.isEmpty) return {};
    final decoded = json.decode(body);
    return decoded is Map<String, dynamic> ? decoded : {};
  }

  String _isoDate(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';
}
