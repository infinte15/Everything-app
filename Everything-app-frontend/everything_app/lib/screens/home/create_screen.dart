import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../providers/task_provider.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/task.dart';
import '../../models/calendar_event.dart';
import '../../widgets/create_task_sheet.dart';
import '../../widgets/create_event_sheet.dart';
import '../../widgets/create_habit_sheet.dart';
import '../../widgets/create_note_sheet.dart';

class CreateScreen extends StatelessWidget {
  const CreateScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final options = [
      _CreateOption(
        icon: Icons.task_alt,
        title: 'Neue Aufgabe',
        subtitle: 'Task erstellen mit Priorität und Deadline',
        color: AppTheme.tasksColor,
        onTap: () => _showCreateTask(context),
      ),
      _CreateOption(
        icon: Icons.event,
        title: 'Neues Event',
        subtitle: 'Kalendereintrag oder Termin',
        color: AppTheme.primaryColor,
        onTap: () => _showCreateEvent(context),
      ),
      _CreateOption(
        icon: Icons.repeat,
        title: 'Neuer Habit',
        subtitle: 'Gewohnheiten aufbauen und tracken',
        color: const Color(0xFF81C784),
        onTap: () => _showCreateHabit(context),
      ),
      _CreateOption(
        icon: Icons.notes,
        title: 'Neue Notiz',
        subtitle: 'Notiz für Studium oder Alltag',
        color: AppTheme.studyColor,
        onTap: () => _showCreateNote(context),
      ),
      _CreateOption(
        icon: Icons.fitness_center,
        title: 'Workout starten',
        subtitle: 'Training beginnen oder planen',
        color: AppTheme.sportsColor,
        onTap: () => context.go('/sports'),
      ),
      _CreateOption(
        icon: Icons.restaurant_menu,
        title: 'Neues Rezept',
        subtitle: 'Rezept hinzufügen oder suchen',
        color: AppTheme.recipesColor,
        onTap: () => context.go('/recipes'),
      ),
      _CreateOption(
        icon: Icons.account_balance_wallet,
        title: 'Transaktion',
        subtitle: 'Einnahme oder Ausgabe erfassen',
        color: AppTheme.financeColor,
        onTap: () => context.go('/finance'),
      ),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Neu erstellen')),
      body: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: options.length,
        itemBuilder: (_, i) => _CreateOptionCard(option: options[i]),
      ),
    );
  }

  void _showCreateTask(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateTaskSheet(),
    );
  }

  void _showCreateEvent(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => CreateEventSheet(selectedDay: DateTime.now()),
    );
  }

  void _showCreateHabit(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateHabitSheet(),
    );
  }

  void _showCreateNote(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateNoteSheet(),
    );
  }
}

class _CreateOption {
  final IconData icon;
  final String title;
  final String subtitle;
  final Color color;
  final VoidCallback onTap;

  const _CreateOption({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.color,
    required this.onTap,
  });
}

class _CreateOptionCard extends StatelessWidget {
  final _CreateOption option;
  const _CreateOptionCard({required this.option});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        onTap: option.onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: option.color.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(option.icon, color: option.color, size: 28),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(option.title,
                        style: theme.textTheme.titleSmall
                            ?.copyWith(fontWeight: FontWeight.bold)),
                    Text(option.subtitle,
                        style: theme.textTheme.bodySmall
                            ?.copyWith(color: Colors.grey)),
                  ],
                ),
              ),
              Icon(Icons.arrow_forward_ios, size: 14, color: option.color),
            ],
          ),
        ),
      ),
    );
  }
}