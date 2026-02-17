import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';

import '../screens/auth/login_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/home/home_screen.dart';
import '../screens/calendar/calendar_screen.dart';
import '../screens/spaces/spaces_screen.dart';
import '../screens/spaces/study_space.dart';
import '../screens/spaces/sports_space.dart';
import '../screens/spaces/tasks_space.dart';
import '../screens/spaces/recipes_space.dart';
import '../screens/spaces/finance_space.dart';
import '../screens/create/create_screen.dart';


class AppRoutes {
  static final GoRouter router = GoRouter(
    initialLocation: '/login',
    routes: [
      // Auth Routes
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        name: 'register',
        builder: (context, state) => const RegisterScreen(),
      ),
      
      // Main Routes
      GoRoute(
        path: '/',
        name: 'home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/calendar',
        name: 'calendar',
        builder: (context, state) => const CalendarScreen(),
      ),
      GoRoute(
        path: '/spaces',
        name: 'spaces',
        builder: (context, state) => const SpacesScreen(),
      ),
      GoRoute(
        path: '/create',
        name: 'create',
        builder: (context, state) => const CreateScreen(),
      ),
      
      // Space Routes
      GoRoute(
        path: '/study',
        name: 'study',
        builder: (context, state) => const StudySpace(),
      ),
      GoRoute(
        path: '/sports',
        name: 'sports',
        builder: (context, state) => const SportsSpace(),
      ),
      GoRoute(
        path: '/tasks',
        name: 'tasks',
        builder: (context, state) => const TasksSpace(),
      ),
      GoRoute(
        path: '/recipes',
        name: 'recipes',
        builder: (context, state) => const RecipesSpace(),
      ),
      GoRoute(
        path: '/finance',
        name: 'finance',
        builder: (context, state) => const FinanceSpace(),
      ),
    ],
    
    // Error Handling
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Text('Seite nicht gefunden: ${state.error}'),
      ),
    ),
  );
}