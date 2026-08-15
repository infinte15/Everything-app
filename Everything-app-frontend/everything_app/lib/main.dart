import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'config/app_theme.dart';
import 'config/routes.dart';
import 'providers/auth_provider.dart';
import 'providers/task_provider.dart';
import 'providers/calendar_provider.dart';
import 'providers/study_provider.dart';
import 'providers/sports_provider.dart';
import 'providers/recipe_provider.dart';
import 'providers/recipe_space_provider.dart';
import 'providers/shopping_list_provider.dart';
import 'providers/finance_provider.dart';
import 'providers/habit_provider.dart';
import 'providers/project_provider.dart';
import 'providers/preferences_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('de_DE', null);
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      
      providers: [
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        // Der Kalender muss VOR den Aufgaben stehen: der TaskProvider haengt an ihm.
        ChangeNotifierProvider(create: (_) => CalendarProvider()),
        // Jede Aufgaben-Mutation stoesst serverseitig eine Neuplanung an. Ohne diese Verdrahtung
        // erfaehrt der Kalender davon erst beim 30-Sekunden-Poll — eine abgehakte Aufgabe blieb
        // so lange als Block stehen. update() legt keine neue Instanz an, es setzt nur denselben
        // Haken erneut.
        ChangeNotifierProxyProvider<CalendarProvider, TaskProvider>(
          create: (_) => TaskProvider(),
          update: (_, calendar, task) => task!..onScheduleAffected = calendar.scheduleReconcile,
        ),
        ChangeNotifierProvider(create: (_) => StudyProvider()),
        ChangeNotifierProvider(create: (_) => SportsProvider()),
        ChangeNotifierProvider(create: (_) => RecipeProvider()),
        // Muss hier oben stehen und nicht im Rezepte-Space: die Detailseite ist
        // eine aufgeschobene Route, also kein Kind der Reiter-Hülle.
        ChangeNotifierProvider(create: (_) => RecipeSpaceProvider()),
        ChangeNotifierProvider(create: (_) => ShoppingListProvider()),
        ChangeNotifierProvider(create: (_) => FinanceProvider()),
        ChangeNotifierProvider(create: (_) => HabitProvider()),
        ChangeNotifierProvider(create: (_) => ProjectProvider()),
        ChangeNotifierProvider(create: (_) => PreferencesProvider()),
      ],
      child: MaterialApp.router(
        title: 'Everything App',
        debugShowCheckedModeBanner: false,
        
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeMode.system,
        
        routerConfig: router,
      ),
    );
  }
}