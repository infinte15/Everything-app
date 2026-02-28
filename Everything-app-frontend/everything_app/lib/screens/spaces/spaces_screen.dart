import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../config/app_theme.dart';

class SpacesScreen extends StatelessWidget {
  const SpacesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final spaces = [
      _SpaceData(
        icon: Icons.school,
        title: 'Studium',
        subtitle: 'Notizen, Stundenplan, Karteikarten, Noten',
        route: '/study',
        color: AppTheme.studyColor,
        stats: '3 Kurse • 12 Notizen',
      ),
      _SpaceData(
        icon: Icons.fitness_center,
        title: 'Sport',
        subtitle: 'Workouts, Training, Fortschritt',
        route: '/sports',
        color: AppTheme.sportsColor,
        stats: '3 Trainings/Woche',
      ),
      _SpaceData(
        icon: Icons.task_alt,
        title: 'Aufgaben',
        subtitle: 'Tasks, Habits, Projekte',
        route: '/tasks',
        color: AppTheme.tasksColor,
        stats: '5 offene Tasks',
      ),
      _SpaceData(
        icon: Icons.restaurant_menu,
        title: 'Rezepte',
        subtitle: 'Kochrezepte, Wochenplan, Einkaufsliste',
        route: '/recipes',
        color: AppTheme.recipesColor,
        stats: '5 Rezepte gespeichert',
      ),
      _SpaceData(
        icon: Icons.account_balance_wallet,
        title: 'Finanzen',
        subtitle: 'Transaktionen, Budget, Statistiken',
        route: '/finance',
        color: AppTheme.financeColor,
        stats: '€ 769,50 verfügbar',
      ),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Meine Spaces'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Wähle einen Space um loszulegen',
              style: theme.textTheme.bodyMedium
                  ?.copyWith(color: Colors.grey),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: ListView.builder(
                itemCount: spaces.length,
                itemBuilder: (_, i) => _SpaceCard(space: spaces[i]),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SpaceData {
  final IconData icon;
  final String title;
  final String subtitle;
  final String route;
  final Color color;
  final String stats;

  const _SpaceData({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.route,
    required this.color,
    required this.stats,
  });
}

class _SpaceCard extends StatelessWidget {
  final _SpaceData space;
  const _SpaceCard({required this.space});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        onTap: () => context.go(space.route),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: space.color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(space.icon, color: space.color, size: 32),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(space.title,
                        style: theme.textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text(space.subtitle,
                        style: theme.textTheme.bodySmall
                            ?.copyWith(color: Colors.grey)),
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: space.color.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(space.stats,
                          style: TextStyle(
                              color: space.color,
                              fontSize: 11,
                              fontWeight: FontWeight.w600)),
                    ),
                  ],
                ),
              ),
              Icon(Icons.arrow_forward_ios,
                  size: 16, color: space.color),
            ],
          ),
        ),
      ),
    );
  }
}