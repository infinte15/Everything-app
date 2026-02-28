import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../providers/task_provider.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/task.dart';
import '../../models/calendar_event.dart';

class CreateScreen extends StatelessWidget {
  const CreateScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
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
        icon: Icons.notes,
        title: 'Neue Notiz',
        subtitle: 'Notiz für Studium oder Alltag',
        color: AppTheme.studyColor,
        onTap: () => context.go('/study'),
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
    final titleController = TextEditingController();
    int priority = 3;
    int duration = 60;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx2, setState) => Padding(
          padding: EdgeInsets.only(
              left: 24,
              right: 24,
              top: 24,
              bottom: MediaQuery.of(ctx2).viewInsets.bottom + 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.tasksColor.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.task_alt, color: AppTheme.tasksColor),
                ),
                const SizedBox(width: 12),
                const Text('Neue Aufgabe',
                    style:
                        TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
              ]),
              const SizedBox(height: 20),
              TextField(
                controller: titleController,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Titel',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              Text('Priorität', style: Theme.of(ctx2).textTheme.labelLarge),
              const SizedBox(height: 8),
              Row(
                children: [1, 2, 3, 4, 5].map((p) {
                  final color = AppTheme.getPriorityColor(p);
                  return Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(right: 4),
                      child: GestureDetector(
                        onTap: () => setState(() => priority = p),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          decoration: BoxDecoration(
                            color: priority == p
                                ? color
                                : color.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text('P$p',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: priority == p ? Colors.white : color,
                                fontWeight: FontWeight.bold,
                              )),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () async {
                  if (titleController.text.isEmpty) return;
                  await context.read<TaskProvider>().addTask(Task(
                        title: titleController.text,
                        priority: priority,
                        estimatedDurationMinutes: duration,
                        status: 'TODO',
                      ));
                  if (context.mounted) Navigator.pop(context);
                },
                style: FilledButton.styleFrom(
                    backgroundColor: AppTheme.tasksColor),
                child: const Text('Aufgabe erstellen'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showCreateEvent(BuildContext context) {
    final titleController = TextEditingController();
    DateTime startTime = DateTime.now().add(const Duration(hours: 1));
    DateTime endTime = DateTime.now().add(const Duration(hours: 2));

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
            left: 24,
            right: 24,
            top: 24,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: AppTheme.primaryColor.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.event, color: AppTheme.primaryColor),
              ),
              const SizedBox(width: 12),
              const Text('Neues Event',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            ]),
            const SizedBox(height: 20),
            TextField(
              controller: titleController,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Titel',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Start- und Endzeit im Kalender wählen',
                style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () async {
                if (titleController.text.isEmpty) return;
                await context.read<CalendarProvider>().addEvent(CalendarEvent(
                      title: titleController.text,
                      startTime: startTime,
                      endTime: endTime,
                      eventType: 'OTHER',
                    ));
                if (context.mounted) Navigator.pop(context);
              },
              child: const Text('Event erstellen'),
            ),
          ],
        ),
      ),
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
                  color: option.color.withOpacity(0.15),
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