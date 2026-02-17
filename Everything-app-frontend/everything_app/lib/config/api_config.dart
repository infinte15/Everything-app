
/// - Android Emulator: http://10.0.2.2:8080/api
/// - Echtes Gerät im gleichen WLAN: http://DEINE_IP:8080/api
/// - iOS Simulator: http://localhost:8080/api
class ApiConfig {
  static const String baseUrl = 'http://10.0.2.2:8080/api';
  
  static const Duration timeout = Duration(seconds: 30);
  
  //AUTH ENDPOINTS
  static const String login = '$baseUrl/auth/login';
  static const String register = '$baseUrl/auth/register';
  
  //TASK ENDPOINTS 
  static const String tasks = '$baseUrl/tasks';
  static String taskById(int id) => '$baseUrl/tasks/$id';
  static String tasksByStatus(String status) => '$baseUrl/tasks/status/$status';
  static const String unscheduledTasks = '$baseUrl/tasks/unscheduled';
  static String completeTask(int id) => '$baseUrl/tasks/$id/complete';
  
  //CALENDAR ENDPOINTS
  static const String calendarEvents = '$baseUrl/calendar/events';
  static String calendarEventById(int id) => '$baseUrl/calendar/events/$id';
  static const String generateSchedule = '$baseUrl/calendar/generate-schedule';
  
  //STUDY ENDPOINTS
  static const String studyNotes = '$baseUrl/study/notes';
  static String studyNoteById(int id) => '$baseUrl/study/notes/$id';
  static const String flashcards = '$baseUrl/study/flashcards';
  static const String flashcardDecks = '$baseUrl/study/decks';
  static const String courses = '$baseUrl/study/courses';
  static const String grades = '$baseUrl/study/grades';
  
  //SPORTS ENDPOINTS
  static const String workoutPlans = '$baseUrl/sports/plans';
  static String workoutPlanById(int id) => '$baseUrl/sports/plans/$id';
  static const String workoutSessions = '$baseUrl/sports/sessions';
  static String workoutSessionById(int id) => '$baseUrl/sports/sessions/$id';
  static const String exercises = '$baseUrl/sports/exercises';
  static const String exerciseSets = '$baseUrl/sports/sets';
  
  //RECIPE ENDPOINTS
  static const String recipes = '$baseUrl/recipes';
  static String recipeById(int id) => '$baseUrl/recipes/$id';
  static const String mealPlan = '$baseUrl/recipes/meal-plan';
  static const String shoppingList = '$baseUrl/recipes/shopping-list';
  
  //FINANCE ENDPOINTS
  static const String transactions = '$baseUrl/finance/transactions';
  static String transactionById(int id) => '$baseUrl/finance/transactions/$id';
  static const String budgets = '$baseUrl/finance/budgets';
  static const String financeStats = '$baseUrl/finance/stats/overview';
  
  //HABIT ENDPOINTS
  static const String habits = '$baseUrl/habits';
  static String habitById(int id) => '$baseUrl/habits/$id';
  static String completeHabit(int id) => '$baseUrl/habits/$id/complete';
  
  //PROJECT ENDPOINTS
  static const String projects = '$baseUrl/projects';
  static String projectById(int id) => '$baseUrl/projects/$id';
}