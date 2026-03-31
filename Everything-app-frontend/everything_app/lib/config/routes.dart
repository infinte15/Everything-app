import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/home/home_screen.dart';
import '../screens/home/create_screen.dart';
import '../screens/calendar/calendar_screen.dart';
import '../screens/spaces/spaces_screen.dart';
import '../screens/study/study_screen.dart';
import '../screens/sports/sports_screen.dart';
import '../screens/tasks/tasks_screen.dart';
import '../screens/recipes/recipes_screen.dart';
import '../screens/finance/finance_screen.dart';
import '../widgets/bottom_nav.dart';

final router = GoRouter(
  initialLocation: '/login',
  redirect: (context, state) {
    final auth = context.read<AuthProvider>();
    final loggedIn = auth.isLoggedIn;
    final loggingIn = state.matchedLocation == '/login' ||
                      state.matchedLocation == '/register';

    if (!loggedIn && !loggingIn) return '/login';
    if (loggedIn && loggingIn) return '/home';
    return null;
  },
  routes: [
    // Auth Routes (kein Bottom Nav)
    GoRoute(
      path: '/login',
      builder: (_, _) => const LoginScreen(),
    ),
    GoRoute(
      path: '/register',
      builder: (_, _) => const RegisterScreen(),
    ),

    // Main App Routes (mit Bottom Nav via ShellRoute)
    ShellRoute(
      builder: (_, __, child) => MainScaffold(child: child),
      routes: [
        GoRoute(
          path: '/home',
          builder: (_, _) => const HomeScreen(),
        ),
        GoRoute(
          path: '/calendar',
          builder: (_, _) => const CalendarScreen(),
        ),
        GoRoute(
          path: '/spaces',
          builder: (_, _) => const SpacesScreen(),
        ),
        GoRoute(
          path: '/create',
          builder: (_, _) => const CreateScreen(),
        ),
      ],
    ),

    // Space Detail Routes (ohne Bottom Nav Shell, eigene Navigation)
    GoRoute(
      path: '/study',
      builder: (_, _) => const StudyScreen(),
    ),
    GoRoute(
      path: '/sports',
      builder: (_, _) => const SportsScreen(),
    ),
    GoRoute(
      path: '/tasks',
      builder: (_, _) => const TasksScreen(),
    ),
    GoRoute(
      path: '/recipes',
      builder: (_, _) => const RecipesScreen(),
    ),
    GoRoute(
      path: '/finance',
      builder: (_, _) => const FinanceScreen(),
    ),
  ],
);
