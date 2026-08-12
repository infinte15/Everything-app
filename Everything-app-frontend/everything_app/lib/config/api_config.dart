
/// - Android Emulator: http://10.0.2.2:8080/api
/// - Echtes Gerät im gleichen WLAN: http://DEINE_IP:8080/api
/// - iOS Simulator: http://localhost:8080/api
class ApiConfig {
  // Wir nutzen jetzt adb reverse tcp:8080 tcp:8080, daher funktioniert localhost auch auf dem echten Handy!
  static const String baseUrl = 'http://localhost:8080/api';
  
  static const Duration timeout = Duration(seconds: 30);
  
  //AUTH ENDPOINTS
  static const String login = '$baseUrl/auth/login';
  static const String register = '$baseUrl/auth/register';
  static const String devLogin = '$baseUrl/auth/dev-login';
  
  //TASK ENDPOINTS 
  static const String tasks = '$baseUrl/tasks';
  static String taskById(int id) => '$baseUrl/tasks/$id';
  static String tasksByStatus(String status) => '$baseUrl/tasks/status/$status';
  static const String unscheduledTasks = '$baseUrl/tasks/unscheduled';
  static String completeTask(int id) => '$baseUrl/tasks/$id/complete';
  
  //CALENDAR ENDPOINTS
  static const String calendarEvents = '$baseUrl/calendar/events';
  static String calendarEventById(int id) => '$baseUrl/calendar/events/$id';
  static String calendarEventPin(int id) => '$baseUrl/calendar/events/$id/pin';
  static String calendarEventComplete(int id) => '$baseUrl/calendar/events/$id/complete';
  static String calendarEventSkip(int id) => '$baseUrl/calendar/events/$id/skip';
  static const String generateSchedule = '$baseUrl/calendar/generate-schedule';

  //USER / PREFERENCES ENDPOINTS
  static const String userPreferences = '$baseUrl/user/preferences';
  
  //STUDY ENDPOINTS
  // Notizen
  static const String studyNotes = '$baseUrl/study/notes';
  static String studyNoteById(int id) => '$baseUrl/study/notes/$id';
  static String studyNotesByCourse(int courseId) =>
      '$baseUrl/study/notes/course/$courseId';
  static String studyNoteSearch(String query) =>
      '$baseUrl/study/notes/search?query=${Uri.encodeQueryComponent(query)}';
  // Seitenbaum: verschieben (eine Seite an eine andere Stelle) und umsortieren (eine Ebene).
  static String studyNoteMove(int id) => '$baseUrl/study/notes/$id/move';
  static String studyNoteCourse(int id) => '$baseUrl/study/notes/$id/course';
  static const String studyNotesReorder = '$baseUrl/study/notes/reorder';

  // Karteikarten
  static const String flashcards = '$baseUrl/study/flashcards';
  static String flashcardById(int id) => '$baseUrl/study/flashcards/$id';
  static String flashcardsByDeck(int deckId) =>
      '$baseUrl/study/flashcards/deck/$deckId';
  static const String dueFlashcards = '$baseUrl/study/flashcards/due';
  static String reviewFlashcard(int id) =>
      '$baseUrl/study/flashcards/$id/review';

  /// Das Review-Protokoll. Ohne [since] liefert der Server die letzten 30 Tage.
  static String flashcardReviews({DateTime? since}) => since == null
      ? '$baseUrl/study/flashcards/reviews'
      : '$baseUrl/study/flashcards/reviews'
          '?since=${Uri.encodeQueryComponent(since.toIso8601String())}';

  // Decks
  static const String flashcardDecks = '$baseUrl/study/decks';
  static String flashcardDeckById(int id) => '$baseUrl/study/decks/$id';
  static String flashcardDeckStats(int id) => '$baseUrl/study/decks/$id/stats';

  // Kurse / Module
  static const String courses = '$baseUrl/study/courses';
  static String courseById(int id) => '$baseUrl/study/courses/$id';
  static String courseSemester(int id) => '$baseUrl/study/courses/$id/semester';

  // Stundenplan. Der ganze Plan auf einmal; angelegt und geändert wird unter dem Modul.
  static const String courseSchedules = '$baseUrl/study/schedules';
  static String schedulesOfCourse(int courseId) =>
      '$baseUrl/study/courses/$courseId/schedules';
  static String scheduleById(int courseId, int id) =>
      '$baseUrl/study/courses/$courseId/schedules/$id';

  // Semester
  static const String semesters = '$baseUrl/study/semesters';
  static String semesterById(int id) => '$baseUrl/study/semesters/$id';
  static String semesterCurrent(int id) => '$baseUrl/study/semesters/$id/current';
  static const String semesterReorder = '$baseUrl/study/semesters/reorder';

  // Lernziele
  static const String studyGoals = '$baseUrl/study/goals';
  static String studyGoalById(int id) => '$baseUrl/study/goals/$id';
  static String studyGoalLog(int id) => '$baseUrl/study/goals/$id/log';

  // Noten
  static const String grades = '$baseUrl/study/grades';
  static String gradeById(int id) => '$baseUrl/study/grades/$id';
  static String gradesByCourse(int courseId) =>
      '$baseUrl/study/grades/course/$courseId';
  
  //SPORTS ENDPOINTS
  static const String workoutPlans = '$baseUrl/sports/plans';
  static String workoutPlanById(int id) => '$baseUrl/sports/plans/$id';
  static const String activeWorkoutPlan = '$baseUrl/sports/plans/active';
  static const String workoutSessions = '$baseUrl/sports/sessions';
  static String workoutSessionById(int id) => '$baseUrl/sports/sessions/$id';
  static const String exercises = '$baseUrl/sports/exercises';
  static const String exerciseSets = '$baseUrl/sports/sets';

  // Übungs-Katalog
  static String exerciseById(int id) => '$baseUrl/sports/exercises/$id';
  static const String muscleGroups = '$baseUrl/sports/exercises/muscles';
  static const String exerciseFilters = '$baseUrl/sports/exercises/filters';
  static String exerciseHistory(int id, {int limit = 20}) =>
      '$baseUrl/sports/exercises/$id/history?limit=$limit';
  static String exerciseRecords(int id) => '$baseUrl/sports/exercises/$id/records';

  // Routinen
  static const String routines = '$baseUrl/sports/routines';
  static String routineById(int id) => '$baseUrl/sports/routines/$id';
  static const String routinesReorder = '$baseUrl/sports/routines/reorder';

  // Laufendes Training
  static const String workoutLog = '$baseUrl/sports/workouts';
  static const String startWorkout = '$baseUrl/sports/workouts/start';
  static String finishWorkout(int sessionId) =>
      '$baseUrl/sports/workouts/$sessionId/finish';

  // Auswertungen
  static const String gymWeeklyStats = '$baseUrl/sports/stats/week';
  static const String gymMuscleStats = '$baseUrl/sports/stats/muscles';
  
  //RECIPE ENDPOINTS
  static const String recipes = '$baseUrl/recipes';
  static String recipeById(int id) => '$baseUrl/recipes/$id';
  static String recipesByCategory(String category) =>
      '$baseUrl/recipes/category/${Uri.encodeComponent(category)}';
  static const String favoriteRecipes = '$baseUrl/recipes/favorites';
  static String recipeSearch(String query) =>
      '$baseUrl/recipes/search?query=${Uri.encodeQueryComponent(query)}';
  static String quickRecipes({int maxMinutes = 30}) =>
      '$baseUrl/recipes/quick?maxMinutes=$maxMinutes';
  static String recipeFavorite(int id) => '$baseUrl/recipes/$id/favorite';

  // Entdecken-Reihen
  static const String recentlyCookedRecipes = '$baseUrl/recipes/recently-cooked';
  static String notCookedLately({int days = 30}) =>
      '$baseUrl/recipes/not-cooked-lately?days=$days';
  static const String neverCookedRecipes = '$baseUrl/recipes/never-cooked';
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
  static const String recipeImportPreview = '$baseUrl/recipes/import/preview';
  static const String recipeImportText = '$baseUrl/recipes/import/text';
  static const String parseIngredients = '$baseUrl/recipes/ingredients/parse';

  // Wochenplan
  static const String mealPlan = '$baseUrl/recipes/meal-plan';
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
  static const String shoppingList = '$baseUrl/recipes/shopping-list';
  static String shoppingItemById(int id) => '$baseUrl/recipes/shopping-list/$id';
  static const String shoppingListChecked =
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
  static const String transactions = '$baseUrl/finance/transactions';
  static String transactionById(int id) => '$baseUrl/finance/transactions/$id';
  static String transactionCategory(int id) =>
      '$baseUrl/finance/transactions/$id/category';
  static const String budgets = '$baseUrl/finance/budgets';
  static String budgetById(int id) => '$baseUrl/finance/budgets/$id';
  static const String budgetProgress = '$baseUrl/finance/stats/budget-progress';
  static const String financeStats = '$baseUrl/finance/stats/overview';
  static String financeMonthlyStats(String month) =>
      '$baseUrl/finance/stats/monthly?month=$month';

  // Verträge. Liefert seit der Vertragserkennung echte Contracts und nicht mehr
  // Buchungen mit isRecurring=true.
  static const String contracts = '$baseUrl/finance/contracts';
  static String contractById(int id) => '$baseUrl/finance/contracts/$id';
  static String contractTransactions(int id) =>
      '$baseUrl/finance/contracts/$id/transactions';

  // Prognose. Ohne month-Parameter der laufende Monat.
  static String financeForecast([String? month]) =>
      month == null ? '$baseUrl/finance/forecast' : '$baseUrl/finance/forecast?month=$month';

  //BANK ENDPOINTS
  static String bankAspsps(String country) =>
      '$baseUrl/finance/bank/aspsps?country=$country';
  static const String bankConnect = '$baseUrl/finance/bank/connect';
  static const String bankConnections = '$baseUrl/finance/bank/connections';
  static String bankConnectionById(int id) => '$baseUrl/finance/bank/connections/$id';
  static const String bankAccounts = '$baseUrl/finance/bank/accounts';
  static String bankAccountById(int id) => '$baseUrl/finance/bank/accounts/$id';
  static const String bankSync = '$baseUrl/finance/bank/sync';
  static const String bankStatus = '$baseUrl/finance/bank/status';
  
  //HABIT ENDPOINTS
  static const String habits = '$baseUrl/habits';
  static String habitById(int id) => '$baseUrl/habits/$id';
  static String completeHabit(int id) => '$baseUrl/habits/$id/complete';
  
  //PROJECT ENDPOINTS
  static const String projects = '$baseUrl/projects';
  static String projectById(int id) => '$baseUrl/projects/$id';
  static String projectTasks(int id) => '$baseUrl/projects/$id/tasks';
  /// Die vom Scheduler platzierten Projektbloecke als CalendarEvents.
  static String projectSessions(int id) => '$baseUrl/projects/$id/sessions';
  /// Aufgabe einem Projekt zuordnen oder entkoppeln (Body: {"projectId": null}).
  static String taskProject(int id) => '$baseUrl/tasks/$id/project';
}