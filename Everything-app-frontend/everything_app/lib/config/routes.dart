import 'package:flutter/material.dart';
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
      builder: (_, __) => const LoginScreen(),
    ),
    GoRoute(
      path: '/register',
      builder: (_, __) => const RegisterScreen(),
    ),

    // Main App Routes (mit Bottom Nav via ShellRoute)
    ShellRoute(
      builder: (_, __, child) => MainScaffold(child: child),
      routes: [
        GoRoute(
          path: '/home',
          builder: (_, __) => const HomeScreen(),
        ),
        GoRoute(
          path: '/calendar',
          builder: (_, __) => const CalendarScreen(),
        ),
        GoRoute(
          path: '/spaces',
          builder: (_, __) => const SpacesScreen(),
        ),
        GoRoute(
          path: '/create',
          builder: (_, __) => const CreateScreen(),
        ),
      ],
    ),

    // Space Detail Routes (ohne Bottom Nav Shell, eigene Navigation)
    GoRoute(
      path: '/study',
      builder: (_, __) => const StudyScreen(),
    ),
    GoRoute(
      path: '/sports',
      builder: (_, __) => const SportsScreen(),
    ),
    GoRoute(
      path: '/tasks',
      builder: (_, __) => const TasksScreen(),
    ),
    GoRoute(
      path: '/recipes',
      builder: (_, __) => const RecipesScreen(),
    ),
    GoRoute(
      path: '/finance',
      builder: (_, __) => const FinanceScreen(),
    ),
  ],
);
