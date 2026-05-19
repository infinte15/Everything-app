import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/study_plan.dart';
import '../../../models/study_note.dart';
import 'widgets/study_kinetic_card.dart';

class StudyPlanPage extends StatelessWidget {
  const StudyPlanPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final goals = provider.studyPlan;

    // Summary calculations
    final totalGoal = goals.fold<double>(0, (s, g) => s + g.weeklyGoalHours);
    final totalLogged = goals.fold<double>(0, (s, g) => s + g.loggedHours);
    final progress = totalGoal > 0 ? (totalLogged / totalGoal).clamp(0.0, 1.0) : 0.0;

    final isWide = MediaQuery.of(context).size.width > 700;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Hero Section: Strategic Overview
            Text(
              'STRATEGIC OVERVIEW',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurfaceVariant,
                letterSpacing: 1.5,
              ),
            ),
            const SizedBox(height: 12),
            StudyKineticCard(
              backgroundColor: theme.colorScheme.surfaceContainerHighest,
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Wöchentliches Ziel',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${totalLogged.toStringAsFixed(1)} / ${totalGoal.toStringAsFixed(0)} Stunden',
                            style: theme.textTheme.headlineMedium?.copyWith(
                              fontWeight: FontWeight.w900,
                              color: theme.colorScheme.onSurface,
                            ),
                          ),
                        ],
                      ),
                      Text(
                        '${(progress * 100).round()}%',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  LinearProgressIndicator(
                    value: progress,
                    backgroundColor: theme.colorScheme.surfaceContainerLowest,
                    valueColor: AlwaysStoppedAnimation(theme.colorScheme.primary),
                    minHeight: 4,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    progress >= 1.0
                        ? '🎉 Wochenziel erreicht!'
                        : 'Auf Kurs für dein wöchentliches Ziel.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: progress >= 1.0 ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 40),

            // Section: Exam Readiness (Goals List)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'EXAM READINESS',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.w900,
                    color: theme.colorScheme.onSurfaceVariant,
                    letterSpacing: 1.5,
                  ),
                ),
                TextButton.icon(
                  onPressed: () => _showAddGoalDialog(context),
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('ZIEL HINZUFÜGEN'),
                  style: TextButton.styleFrom(
                    foregroundColor: theme.colorScheme.primary,
                    textStyle: theme.textTheme.labelSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (goals.isEmpty)
              StudyKineticCard(
                backgroundColor: theme.colorScheme.surfaceContainerLow,
                child: const Center(child: Text('Keine Lernziele definiert.')),
              )
            else
              if (!isWide)
              Column(
                children: List.generate(goals.length, (index) {
                  final goal = goals[index];
                  final color = Color(goal.colorValue);
                  final goalProgress = goal.weeklyGoalHours > 0
                      ? (goal.loggedHours / goal.weeklyGoalHours).clamp(0.0, 1.0)
                      : 0.0;

                  return Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: StudyKineticCard(
                      backgroundColor: theme.colorScheme.surfaceContainerLow,
                      padding: const EdgeInsets.all(16),
                      onTap: () => _showLogHoursDialog(context, goal),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Row(
                                  children: [
                                    Text(goal.emoji, style: const TextStyle(fontSize: 20)),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Text(
                                        goal.subject,
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                '${(goalProgress * 100).round()}%',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: color,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          LinearProgressIndicator(
                            value: goalProgress,
                            backgroundColor: theme.colorScheme.surfaceContainerLowest,
                            valueColor: AlwaysStoppedAnimation(color),
                            minHeight: 2,
                          ),
                          const SizedBox(height: 8),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                '${goal.loggedHours.toStringAsFixed(1)} / ${goal.weeklyGoalHours.toStringAsFixed(0)} Std',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline, size: 18),
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                                onPressed: () {
                                  provider.deleteStudyGoal(goal.id);
                                },
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              )
            else
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: isWide ? 3 : 1,
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                  childAspectRatio: isWide ? 1.4 : 3.0,
                ),
                itemCount: goals.length,
                itemBuilder: (context, index) {
                  final goal = goals[index];
                  final color = Color(goal.colorValue);
                  final goalProgress = goal.weeklyGoalHours > 0
                      ? (goal.loggedHours / goal.weeklyGoalHours).clamp(0.0, 1.0)
                      : 0.0;

                  return StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerLow,
                    padding: const EdgeInsets.all(16),
                    onTap: () => _showLogHoursDialog(context, goal),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  Text(goal.emoji, style: const TextStyle(fontSize: 20)),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      goal.subject,
                                      style: theme.textTheme.titleMedium?.copyWith(
                                        fontWeight: FontWeight.bold,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              '${(goalProgress * 100).round()}%',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: color,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        LinearProgressIndicator(
                          value: goalProgress,
                          backgroundColor: theme.colorScheme.surfaceContainerLowest,
                          valueColor: AlwaysStoppedAnimation(color),
                          minHeight: 2,
                        ),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '${goal.loggedHours.toStringAsFixed(1)} / ${goal.weeklyGoalHours.toStringAsFixed(0)} Std',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, size: 18),
                              onPressed: () {
                                provider.deleteStudyGoal(goal.id);
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),

            const SizedBox(height: 48),

            // Section: Active Sprint (Kanban Board)
            Text(
              'ACTIVE SPRINT',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurfaceVariant,
                letterSpacing: 1.5,
              ),
            ),
            const SizedBox(height: 16),

            // Kanban columns layout
            isWide
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: _buildKanbanColumn(context, 'TO DO', provider.todoNotes, 'todo')),
                      const SizedBox(width: 16),
                      Expanded(child: _buildKanbanColumn(context, 'IN PROGRESS', provider.inProgressNotes, 'in_progress')),
                      const SizedBox(width: 16),
                      Expanded(child: _buildKanbanColumn(context, 'DONE', provider.doneNotes, 'done')),
                    ],
                  )
                : Column(
                    children: [
                      _buildKanbanColumn(context, 'TO DO', provider.todoNotes, 'todo'),
                      const SizedBox(height: 24),
                      _buildKanbanColumn(context, 'IN PROGRESS', provider.inProgressNotes, 'in_progress'),
                      const SizedBox(height: 24),
                      _buildKanbanColumn(context, 'DONE', provider.doneNotes, 'done'),
                    ],
                  ),

            const SizedBox(height: 100),
          ],
        ),
      ),
    );
  }

  // ── Kanban Column Widget ──────────────────────────────────────────────────
  Widget _buildKanbanColumn(BuildContext context, String title, List<StudyNote> notes, String status) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Column Header
        Container(
          padding: const EdgeInsets.only(bottom: 8),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                width: 1,
              ),
            ),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title,
                style: theme.textTheme.labelSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.0,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                color: theme.colorScheme.surfaceContainerLow,
                child: Text(
                  '${notes.length}',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.onSurface,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // List of cards in column
        if (notes.isEmpty)
          Container(
            height: 80,
            width: double.infinity,
            decoration: BoxDecoration(
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.1),
                style: BorderStyle.solid,
              ),
            ),
            child: Center(
              child: Text(
                'Keine Aufgaben',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                ),
              ),
            ),
          )
        else
          Column(
            children: notes.map((note) {
              final isDone = status == 'done';
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: StudyKineticCard(
                  backgroundColor: isDone
                      ? theme.colorScheme.surfaceContainerLow
                      : theme.colorScheme.surfaceContainerHighest,
                  padding: const EdgeInsets.all(16),
                  onTap: () => _showStatusSwitcher(context, note, status),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        note.title,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: isDone ? theme.colorScheme.onSurfaceVariant : theme.colorScheme.onSurface,
                          decoration: isDone ? TextDecoration.lineThrough : null,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Icon(
                                status == 'todo'
                                    ? Icons.menu_book
                                    : status == 'in_progress'
                                        ? Icons.calculate
                                        : Icons.check_circle,
                                size: 12,
                                color: status == 'in_progress' ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                note.courseName ?? 'Notiz',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                  fontSize: 9,
                                ),
                              ),
                            ],
                          ),
                          Text(
                            'Heute',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.outline,
                              fontSize: 9,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            }).toList(),
          ),
      ],
    );
  }

  // ── Status Switcher Dialog ────────────────────────────────────────────────
  void _showStatusSwitcher(BuildContext context, StudyNote note, String currentStatus) {
    final theme = Theme.of(context);
    final provider = context.read<StudyProvider>();

    showModalBottomSheet(
      context: context,
      backgroundColor: theme.colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: Text(note.title, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: const Text('Status ändern'),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.list),
                title: const Text('TO DO'),
                selected: currentStatus == 'todo',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'todo');
                  Navigator.pop(ctx);
                },
              ),
              ListTile(
                leading: const Icon(Icons.play_arrow),
                title: const Text('IN PROGRESS'),
                selected: currentStatus == 'in_progress',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'in_progress');
                  Navigator.pop(ctx);
                },
              ),
              ListTile(
                leading: const Icon(Icons.check),
                title: const Text('DONE'),
                selected: currentStatus == 'done',
                onTap: () {
                  provider.updateNoteStatus(note.id!, 'done');
                  Navigator.pop(ctx);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  // ── Dialogs ────────────────────────────────────────────────────────────────
  void _showLogHoursDialog(BuildContext context, StudyPlanGoal goal) {
    final ctrl = TextEditingController(text: '1.0');
    final provider = context.read<StudyProvider>();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${goal.emoji} ${goal.subject}'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(labelText: 'Lernzeit eintragen', suffixText: 'Std'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () {
              final hrs = double.tryParse(ctrl.text) ?? 0.0;
              if (hrs > 0.0) {
                provider.logStudyHours(goal.id, hrs);
              }
              Navigator.pop(ctx);
            },
            child: const Text('Erfassen'),
          ),
        ],
      ),
    );
  }

  void _showAddGoalDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final hoursCtrl = TextEditingController(text: '5');
    String emoji = '📚';
    int colorVal = 0xFFC2C1FF;

    final emojiOptions = ['📚', '📐', '💻', '⚛️', '🗄️', '🔬', '🎯', '📖'];
    final colorOptions = [
      0xFFC2C1FF, 0xFFEF7C8A, 0xFF8BD17B, 0xFFF59E0B,
      0xFF8B5CF6, 0xFFEC4899, 0xFF14B8A6,
    ];

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          return AlertDialog(
            title: const Text('Neues Lernziel'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Fach')),
                  const SizedBox(height: 12),
                  TextField(
                    controller: hoursCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Zielstunden', suffixText: 'Std'),
                  ),
                  const SizedBox(height: 16),
                  const Text('Symbol wählen:'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: emojiOptions.map((e) {
                      final sel = e == emoji;
                      return GestureDetector(
                        onTap: () => setSt(() => emoji = e),
                        child: Container(
                          padding: const EdgeInsets.all(8),
                          color: sel ? Theme.of(ctx).colorScheme.primaryContainer : Colors.transparent,
                          child: Text(e, style: const TextStyle(fontSize: 20)),
                        ),
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: 16),
                  const Text('Farbe wählen:'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: colorOptions.map((c) {
                      final sel = c == colorVal;
                      return GestureDetector(
                        onTap: () => setSt(() => colorVal = c),
                        child: Container(
                          width: 24,
                          height: 24,
                          color: Color(c),
                          child: sel ? const Icon(Icons.check, size: 16, color: Colors.black) : null,
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
              FilledButton(
                onPressed: () {
                  final hrs = double.tryParse(hoursCtrl.text) ?? 5.0;
                  if (nameCtrl.text.trim().isNotEmpty) {
                    context.read<StudyProvider>().addStudyGoal(
                      subject: nameCtrl.text.trim(),
                      goalHours: hrs,
                      emoji: emoji,
                      colorValue: colorVal,
                    );
                    Navigator.pop(ctx);
                  }
                },
                child: const Text('Hinzufügen'),
              ),
            ],
          );
        },
      ),
    );
  }
}
