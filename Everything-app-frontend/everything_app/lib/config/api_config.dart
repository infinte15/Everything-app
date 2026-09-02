
import 'package:flutter/foundation.dart' show kIsWeb;

/// Zentrale Stelle für alle Backend-URLs.
///
/// Die Basis-URL wird nicht mehr fest verdrahtet, sondern beim Build gesetzt:
///
/// ```
/// flutter build apk --release --dart-define=API_BASE_URL=https://app.deine-domain.de/api
/// ```
///
/// Ohne `--dart-define` bleibt es bei `http://localhost:8080/api` — passend zum
/// Entwicklungsbetrieb mit `adb reverse tcp:8080 tcp:8080` bzw. dem iOS-Simulator.
/// Für den Android-Emulator oder ein Gerät ohne `adb reverse` stattdessen die
/// LAN-Adresse mitgeben, z. B. `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api`.
class ApiConfig {
  static const String _compiledBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api',
  );

  /// Im Web liegen App und API hinter Caddy auf derselben Origin — die Adresse
  /// steht also schon im Browser und muss nicht einkompiliert werden. Das spart
  /// beim Web-Build das `--dart-define` und die ganze CORS-Frage gleich mit.
  ///
  /// Bewusst `Uri.base.origin` und nicht der relative Pfad `/api`: `package:http`
  /// baut daraus eine absolute URL, und absolute URLs funktionieren in jedem
  /// Client gleich.
  ///
  /// Sobald das hier ein Getter ist, können die Endpunkte darunter nicht mehr
  /// `const` sein — deshalb sind sie alle zu Gettern geworden. Für die
  /// aufrufenden Services ändert sich dadurch nichts.
  static String get baseUrl =>
      kIsWeb ? '${Uri.base.origin}/api' : _compiledBaseUrl;
  
  static const Duration timeout = Duration(seconds: 30);
  
  //AUTH ENDPOINTS
  static String get login => '$baseUrl/auth/login';
  static String get register => '$baseUrl/auth/register';
  static String get devLogin => '$baseUrl/auth/dev-login';
  
  //TASK ENDPOINTS 
  static String get tasks => '$baseUrl/tasks';
  static String taskById(int id) => '$baseUrl/tasks/$id';
  static String tasksByStatus(String status) => '$baseUrl/tasks/status/$status';
  static String get unscheduledTasks => '$baseUrl/tasks/unscheduled';
  static String completeTask(int id) => '$baseUrl/tasks/$id/complete';
  
  //CALENDAR ENDPOINTS
  static String get calendarEvents => '$baseUrl/calendar/events';
  static String calendarEventById(int id) => '$baseUrl/calendar/events/$id';
  static String calendarEventPin(int id) => '$baseUrl/calendar/events/$id/pin';
  static String calendarEventComplete(int id) => '$baseUrl/calendar/events/$id/complete';
  static String calendarEventSkip(int id) => '$baseUrl/calendar/events/$id/skip';
  static String get generateSchedule => '$baseUrl/calendar/generate-schedule';
  /// Ergebnis des letzten Scheduler-Laufs — klein genug, um danach zu pollen.
  static String get scheduleStatus => '$baseUrl/calendar/schedule-status';

  /// Dasselbe, aber die Antwort kommt erst, wenn wirklich neu geplant wurde.
  ///
  /// [since] ist der ROHE Zeitstempel des zuletzt gesehenen Laufs, so wie der Server ihn
  /// geschrieben hat — bewusst nicht neu formatiert. Ein durch `DateTime.parse`/`toIso8601String`
  /// gelaufener Wert verliert Stellen (Dart schneidet auf Mikrosekunden ab) und wäre damit minimal
  /// kleiner als der gespeicherte: der Server hielte jeden alten Lauf für neuer als `since` und
  /// antwortete sofort — die App liefe in eine Anfrageschleife.
  static String scheduleStatusAwait([String? since]) => since == null
      ? '$baseUrl/calendar/schedule-status/await'
      : '$baseUrl/calendar/schedule-status/await?since=${Uri.encodeQueryComponent(since)}';

  /// Muss GRÖSSER sein als `scheduler.await-timeout-ms` (25 s) im Backend.
  ///
  /// Sonst gewinnt der Client-Timeout das Rennen und die App sieht einen Socket-Fehler statt des
  /// sauberen 204, das der Server ohnehin schicken wollte.
  static const Duration longPollTimeout = Duration(seconds: 35);

  //USER / PREFERENCES ENDPOINTS
  static String get userPreferences => '$baseUrl/user/preferences';
  
  //STUDY ENDPOINTS
  // Notizen
  static String get studyNotes => '$baseUrl/study/notes';
  static String studyNoteById(int id) => '$baseUrl/study/notes/$id';
  static String studyNotesByCourse(int courseId) =>
      '$baseUrl/study/notes/course/$courseId';
  static String studyNoteSearch(String query) =>
      '$baseUrl/study/notes/search?query=${Uri.encodeQueryComponent(query)}';
  // Seitenbaum: verschieben (eine Seite an eine andere Stelle) und umsortieren (eine Ebene).
  static String studyNoteMove(int id) => '$baseUrl/study/notes/$id/move';
  static String studyNoteCourse(int id) => '$baseUrl/study/notes/$id/course';
  static String get studyNotesReorder => '$baseUrl/study/notes/reorder';

  // Karteikarten
  static String get flashcards => '$baseUrl/study/flashcards';
  static String flashcardById(int id) => '$baseUrl/study/flashcards/$id';
  static String flashcardsByDeck(int deckId) =>
      '$baseUrl/study/flashcards/deck/$deckId';
  static String get dueFlashcards => '$baseUrl/study/flashcards/due';
  static String reviewFlashcard(int id) =>
      '$baseUrl/study/flashcards/$id/review';

  /// Das Review-Protokoll. Ohne [since] liefert der Server die letzten 30 Tage.
  static String flashcardReviews({DateTime? since}) => since == null
      ? '$baseUrl/study/flashcards/reviews'
      : '$baseUrl/study/flashcards/reviews'
          '?since=${Uri.encodeQueryComponent(since.toIso8601String())}';

  // Decks
  static String get flashcardDecks => '$baseUrl/study/decks';
  static String flashcardDeckById(int id) => '$baseUrl/study/decks/$id';
  static String flashcardDeckStats(int id) => '$baseUrl/study/decks/$id/stats';

  // Kurse / Module
  static String get courses => '$baseUrl/study/courses';
  static String courseById(int id) => '$baseUrl/study/courses/$id';
  static String courseSemester(int id) => '$baseUrl/study/courses/$id/semester';

  // Stundenplan. Der ganze Plan auf einmal; angelegt und geändert wird unter dem Modul.
  static String get courseSchedules => '$baseUrl/study/schedules';
  static String schedulesOfCourse(int courseId) =>
      '$baseUrl/study/courses/$courseId/schedules';
  static String scheduleById(int courseId, int id) =>
      '$baseUrl/study/courses/$courseId/schedules/$id';

  // Semester
  static String get semesters => '$baseUrl/study/semesters';
  static String semesterById(int id) => '$baseUrl/study/semesters/$id';
  static String semesterCurrent(int id) => '$baseUrl/study/semesters/$id/current';
  static String get semesterReorder => '$baseUrl/study/semesters/reorder';

  // Lernziele
  static String get studyGoals => '$baseUrl/study/goals';
  static String studyGoalById(int id) => '$baseUrl/study/goals/$id';
  static String studyGoalLog(int id) => '$baseUrl/study/goals/$id/log';

  // Noten
  static String get grades => '$baseUrl/study/grades';
  static String gradeById(int id) => '$baseUrl/study/grades/$id';
  static String gradesByCourse(int courseId) =>
      '$baseUrl/study/grades/course/$courseId';
  
  //SPORTS ENDPOINTS
  static String get workoutPlans => '$baseUrl/sports/plans';
  static String workoutPlanById(int id) => '$baseUrl/sports/plans/$id';
  static String get activeWorkoutPlan => '$baseUrl/sports/plans/active';
  static String get workoutSessions => '$baseUrl/sports/sessions';
  static String workoutSessionById(int id) => '$baseUrl/sports/sessions/$id';
  static String get exercises => '$baseUrl/sports/exercises';
  static String get exerciseSets => '$baseUrl/sports/sets';

  // Übungs-Katalog
  static String exerciseById(int id) => '$baseUrl/sports/exercises/$id';
  static String get muscleGroups => '$baseUrl/sports/exercises/muscles';
  static String get exerciseFilters => '$baseUrl/sports/exercises/filters';
  static String exerciseHistory(int id, {int limit = 20}) =>
      '$baseUrl/sports/exercises/$id/history?limit=$limit';
  static String exerciseRecords(int id) => '$baseUrl/sports/exercises/$id/records';

  /// Stehende Notiz zur Übung - GET und PUT auf derselben Adresse.
  static String exerciseNote(int id) => '$baseUrl/sports/exercises/$id/note';

  // Routinen
  static String get routines => '$baseUrl/sports/routines';
  static String routineById(int id) => '$baseUrl/sports/routines/$id';
  static String get routinesReorder => '$baseUrl/sports/routines/reorder';
  static String routineProgression(int id) =>
      '$baseUrl/sports/routines/$id/progression';

  // Laufendes Training
  static String get workoutLog => '$baseUrl/sports/workouts';
  static String get startWorkout => '$baseUrl/sports/workouts/start';
  static String finishWorkout(int sessionId) =>
      '$baseUrl/sports/workouts/$sessionId/finish';

  // Auswertungen
  static String get gymWeeklyStats => '$baseUrl/sports/stats/week';
  static String get gymMuscleStats => '$baseUrl/sports/stats/muscles';
  static String get gymRecovery => '$baseUrl/sports/stats/recovery';

  // Körpergewicht
  static String get bodyWeight => '$baseUrl/sports/bodyweight';
  static String bodyWeightSince(String fromIso) =>
      '$baseUrl/sports/bodyweight?from=$fromIso';
  static String bodyWeightById(int id) => '$baseUrl/sports/bodyweight/$id';
  static String get bodyWeightTarget => '$baseUrl/sports/bodyweight/target';

  // Ausrüstungsprofile
  static String get equipmentProfiles => '$baseUrl/sports/equipment-profiles';
  static String equipmentProfileById(int id) =>
      '$baseUrl/sports/equipment-profiles/$id';

  /// 0 schaltet die Filterung ab - so heißt es auch im Backend.
  static String equipmentProfileActivate(int id) =>
      '$baseUrl/sports/equipment-profiles/$id/activate';
  
  //RECIPE ENDPOINTS
  static String get recipes => '$baseUrl/recipes';
  static String recipeById(int id) => '$baseUrl/recipes/$id';
  static String recipesByCategory(String category) =>
      '$baseUrl/recipes/category/${Uri.encodeComponent(category)}';
  static String get favoriteRecipes => '$baseUrl/recipes/favorites';
  static String recipeSearch(String query) =>
      '$baseUrl/recipes/search?query=${Uri.encodeQueryComponent(query)}';
  static String quickRecipes({int maxMinutes = 30}) =>
      '$baseUrl/recipes/quick?maxMinutes=$maxMinutes';
  static String recipeFavorite(int id) => '$baseUrl/recipes/$id/favorite';

  // Entdecken-Reihen
  static String get recentlyCookedRecipes => '$baseUrl/recipes/recently-cooked';
  static String notCookedLately({int days = 30}) =>
      '$baseUrl/recipes/not-cooked-lately?days=$days';
  static String get neverCookedRecipes => '$baseUrl/recipes/never-cooked';
  static String bestRatedRecipes({int minRating = 4}) =>
      '$baseUrl/recipes/best-rated?minRating=$minRating';

  // Bewertung und Kochprotokoll
  static String recipeRating(int id) => '$baseUrl/recipes/$id/rating';
  static String recipeCooked(int id) => '$baseUrl/recipes/$id/cooked';
  static String recipeCookLog(int id) => '$baseUrl/recipes/$id/cook-log';
  static String cookLogById(int logId) => '$baseUrl/recipes/cook-log/$logId';

  // Import. `importPreview` nimmt jede Adresse; welche der Server abrufen darf,
  // entscheidet er selbst. `importText` ruft nichts ab - der Text kommt aus der
  // Zwischenablage.
  static String get recipeImportPreview => '$baseUrl/recipes/import/preview';
  static String get recipeImportText => '$baseUrl/recipes/import/text';
  static String get parseIngredients => '$baseUrl/recipes/ingredients/parse';

  // Wochenplan
  static String get mealPlan => '$baseUrl/recipes/meal-plan';
  static String mealPlanRange(DateTime from, DateTime to) =>
      '$baseUrl/recipes/meal-plan?startDate=${isoDay(from)}&endDate=${isoDay(to)}';
  static String mealPlanOnDate(DateTime day) =>
      '$baseUrl/recipes/meal-plan/date/${isoDay(day)}';
  static String mealPlanById(int id) => '$baseUrl/recipes/meal-plan/$id';
  static String mealPlanComplete(int id) => '$baseUrl/recipes/meal-plan/$id/complete';
  static String mealPlanGenerate(DateTime weekStart) =>
      '$baseUrl/recipes/meal-plan/generate?startDate=${isoDay(weekStart)}';

  // Einkaufsliste. Ohne Zeitraum-Parameter - die Liste ist ein Zustand, keine
  // Auswertung eines Zeitraums.
  static String get shoppingList => '$baseUrl/recipes/shopping-list';
  static String shoppingItemById(int id) => '$baseUrl/recipes/shopping-list/$id';
  static String get shoppingListChecked =>
      '$baseUrl/recipes/shopping-list/checked';
  static String shoppingListFromMealPlan(DateTime from, DateTime to) =>
      '$baseUrl/recipes/shopping-list/from-meal-plan'
      '?startDate=${isoDay(from)}&endDate=${isoDay(to)}';

  /// Zutaten eines einzelnen Rezepts übernehmen. Ohne [servings] gilt die
  /// Grundmenge des Rezepts; skaliert und zusammengefasst wird auf dem Server.
  static String shoppingListFromRecipe(int recipeId, {int? servings}) =>
      '$baseUrl/recipes/shopping-list/from-recipe/$recipeId'
      '${servings == null ? '' : '?servings=$servings'}';

  /// Datum ohne Zeitanteil für Query-Parameter - der Server bindet `LocalDate`.
  ///
  /// Von Hand zusammengesetzt statt über `toIso8601String()`: das liefert bei
  /// einem `DateTime` in UTC den Vortag, wenn es lokal schon der nächste ist.
  static String isoDay(DateTime day) =>
      '${day.year.toString().padLeft(4, '0')}-'
      '${day.month.toString().padLeft(2, '0')}-'
      '${day.day.toString().padLeft(2, '0')}';


  //FINANCE ENDPOINTS
  static String get transactions => '$baseUrl/finance/transactions';
  static String transactionById(int id) => '$baseUrl/finance/transactions/$id';
  static String transactionCategory(int id) =>
      '$baseUrl/finance/transactions/$id/category';
  static String get budgets => '$baseUrl/finance/budgets';
  static String budgetById(int id) => '$baseUrl/finance/budgets/$id';
  static String get budgetProgress => '$baseUrl/finance/stats/budget-progress';
  static String get financeStats => '$baseUrl/finance/stats/overview';
  static String financeMonthlyStats(String month) =>
      '$baseUrl/finance/stats/monthly?month=$month';

  // Verträge. Liefert seit der Vertragserkennung echte Contracts und nicht mehr
  // Buchungen mit isRecurring=true.
  static String get contracts => '$baseUrl/finance/contracts';
  static String contractById(int id) => '$baseUrl/finance/contracts/$id';
  static String contractTransactions(int id) =>
      '$baseUrl/finance/contracts/$id/transactions';

  // Prognose. Ohne month-Parameter der laufende Monat.
  static String financeForecast([String? month]) =>
      month == null ? '$baseUrl/finance/forecast' : '$baseUrl/finance/forecast?month=$month';

  //BANK ENDPOINTS
  static String bankAspsps(String country) =>
      '$baseUrl/finance/bank/aspsps?country=$country';
  static String get bankConnect => '$baseUrl/finance/bank/connect';
  static String get bankConnections => '$baseUrl/finance/bank/connections';
  static String bankConnectionById(int id) => '$baseUrl/finance/bank/connections/$id';
  static String get bankAccounts => '$baseUrl/finance/bank/accounts';
  static String bankAccountById(int id) => '$baseUrl/finance/bank/accounts/$id';
  static String get bankSync => '$baseUrl/finance/bank/sync';
  static String get bankStatus => '$baseUrl/finance/bank/status';
  
  //HABIT ENDPOINTS
  static String get habits => '$baseUrl/habits';
  static String habitById(int id) => '$baseUrl/habits/$id';
  static String completeHabit(int id) => '$baseUrl/habits/$id/complete';
  
  //PROJECT ENDPOINTS
  static String get projects => '$baseUrl/projects';
  static String projectById(int id) => '$baseUrl/projects/$id';
  static String projectTasks(int id) => '$baseUrl/projects/$id/tasks';
  /// Die vom Scheduler platzierten Projektbloecke als CalendarEvents.
  static String projectSessions(int id) => '$baseUrl/projects/$id/sessions';
  /// Aufgabe einem Projekt zuordnen oder entkoppeln (Body: {"projectId": null}).
  static String taskProject(int id) => '$baseUrl/tasks/$id/project';
}