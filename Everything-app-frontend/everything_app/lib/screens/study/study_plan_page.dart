import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'dart:math';
import '../../providers/study_provider.dart';
import '../../models/study_plan.dart';

class StudyPlanPage extends StatelessWidget {
  const StudyPlanPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final goals = provider.studyPlan;

    // Summary stats
    final totalGoal = goals.fold<double>(0, (s, g) => s + g.weeklyGoalHours);
    final totalLogged = goals.fold<double>(0, (s, g) => s + g.loggedHours);
    final overallProgress = totalGoal > 0 ? (totalLogged / totalGoal).clamp(0.0, 1.0) : 0.0;

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Text('📅', style: TextStyle(fontSize: 28)),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text('Lernplan',
                            style: theme.textTheme.headlineSmall?.copyWith(
                                fontWeight: FontWeight.bold)),
                      ),
                      FilledButton.icon(
                        onPressed: () => _showAddGoalDialog(context),
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('Ziel'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  // Overall progress card
                  _WeekSummaryCard(
                    totalGoal: totalGoal,
                    totalLogged: totalLogged,
                    progress: overallProgress,
                  ),
                  const SizedBox(height: 20),
                  Text('Fächer diese Woche',
                      style: theme.textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                ],
              ),
            ),
          ),
          goals.isEmpty
              ? SliverFillRemaining(
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Text('📅', style: TextStyle(fontSize: 56)),
                        const SizedBox(height: 16),
                        Text('Keine Lernziele',
                            style: theme.textTheme.titleMedium
                                ?.copyWith(color: Colors.grey)),
                        const SizedBox(height: 24),
                        FilledButton.icon(
                          onPressed: () => _showAddGoalDialog(context),
                          icon: const Icon(Icons.add),
                          label: const Text('Erstes Ziel setzen'),
                        ),
                      ],
                    ),
                  ),
                )
              : SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (ctx, i) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _StudyGoalCard(goal: goals[i]),
                      ),
                      childCount: goals.length,
                    ),
                  ),
                ),
          const SliverToBoxAdapter(child: SizedBox(height: 24)),
        ],
      ),
    );
  }

  Future<void> _showAddGoalDialog(BuildContext context) async {
    final nameCtrl = TextEditingController();
    final hoursCtrl = TextEditingController(text: '5');
    String emoji = '📚';
    int colorVal = 0xFF6366F1;

    final emojiOptions = ['📚', '📐', '💻', '⚛️', '🗄️', '🔬', '🎯', '📖'];
    final colorOptions = [
      0xFF6366F1, 0xFF3B82F6, 0xFF10B981, 0xFFF59E0B,
      0xFFEF4444, 0xFF8B5CF6, 0xFFEC4899, 0xFF14B8A6,
    ];

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(builder: (ctx, setSt) {
        return AlertDialog(
          title: const Text('Neues Lernziel'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Emoji', style: Theme.of(ctx).textTheme.labelMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: emojiOptions.map((e) {
                    final sel = e == emoji;
                    return GestureDetector(
                      onTap: () => setSt(() => emoji = e),
                      child: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          color: sel
                              ? Theme.of(ctx).colorScheme.primaryContainer
                              : Colors.transparent,
                        ),
                        child: Text(e, style: const TextStyle(fontSize: 22)),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 16),
                Text('Farbe', style: Theme.of(ctx).textTheme.labelMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: colorOptions.map((c) {
                    final sel = c == colorVal;
                    return GestureDetector(
                      onTap: () => setSt(() => colorVal = c),
                      child: Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          color: Color(c),
                          shape: BoxShape.circle,
                          border: sel
                              ? Border.all(color: Colors.white, width: 3)
                              : null,
                          boxShadow: sel
                              ? [BoxShadow(color: Color(c), blurRadius: 6)]
                              : null,
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: nameCtrl,
                  autofocus: true,
                  decoration: const InputDecoration(labelText: 'Fach'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: hoursCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                      labelText: 'Wochenstunden-Ziel',
                      suffixText: 'h'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Abbrechen')),
            FilledButton(
              onPressed: () {
                final hrs = double.tryParse(hoursCtrl.text) ?? 5;
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
      }),
    );
  }
}

// ── Week summary card ─────────────────────────────────────────────────────────

class _WeekSummaryCard extends StatelessWidget {
  final double totalGoal;
  final double totalLogged;
  final double progress;

  const _WeekSummaryCard({
    required this.totalGoal,
    required this.totalLogged,
    required this.progress,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
              color: const Color(0xFF6366F1).withValues(alpha: 0.4),
              blurRadius: 16,
              offset: const Offset(0, 6)),
        ],
      ),
      child: Row(
        children: [
          // Ring
          SizedBox(
            width: 80,
            height: 80,
            child: CustomPaint(
              painter: _RingPainter(progress: progress, color: Colors.white),
              child: Center(
                child: Text(
                  '${(progress * 100).round()}%',
                  style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16),
                ),
              ),
            ),
          ),
          const SizedBox(width: 20),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Diese Woche',
                    style: TextStyle(color: Colors.white70, fontSize: 13)),
                const SizedBox(height: 4),
                Text(
                  '${totalLogged.toStringAsFixed(1)} / ${totalGoal.toStringAsFixed(0)} Stunden',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  progress >= 1.0
                      ? '🎉 Wochenziel erreicht!'
                      : '${(totalGoal - totalLogged).toStringAsFixed(1)} h verbleibend',
                  style:
                      const TextStyle(color: Colors.white70, fontSize: 13),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Goal card ─────────────────────────────────────────────────────────────────

class _StudyGoalCard extends StatelessWidget {
  final StudyPlanGoal goal;
  const _StudyGoalCard({required this.goal});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = goal.color;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          // Ring
          SizedBox(
            width: 60,
            height: 60,
            child: CustomPaint(
              painter: _RingPainter(progress: goal.progress, color: color),
              child: Center(
                child: Text(goal.emoji, style: const TextStyle(fontSize: 20)),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(goal.subject,
                    style: theme.textTheme.titleSmall
                        ?.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(
                  '${goal.loggedHours.toStringAsFixed(1)} / ${goal.weeklyGoalHours.toStringAsFixed(0)} h',
                  style: TextStyle(color: color, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: goal.progress,
                    backgroundColor: color.withValues(alpha: 0.15),
                    valueColor: AlwaysStoppedAnimation(color),
                    minHeight: 6,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            children: [
              IconButton.filledTonal(
                icon: const Icon(Icons.add, size: 18),
                tooltip: 'Stunden erfassen',
                onPressed: () => _logHours(context, goal),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline, size: 18, color: Colors.grey),
                tooltip: 'Löschen',
                onPressed: () => context.read<StudyProvider>().deleteStudyGoal(goal.id),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _logHours(BuildContext context, StudyPlanGoal goal) async {
    final ctrl = TextEditingController(text: '1.0');
    await showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${goal.emoji} ${goal.subject}'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(
              labelText: 'Gelernte Stunden', suffixText: 'h'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () {
              final hrs = double.tryParse(ctrl.text) ?? 0;
              if (hrs > 0) {
                context.read<StudyProvider>().logStudyHours(goal.id, hrs);
              }
              Navigator.pop(ctx);
            },
            child: const Text('Erfassen'),
          ),
        ],
      ),
    );
  }
}

// ── Custom ring painter ───────────────────────────────────────────────────────

class _RingPainter extends CustomPainter {
  final double progress;
  final Color color;

  _RingPainter({required this.progress, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = min(size.width, size.height) / 2 - 6;
    final stroke = 5.0;

    // Background ring
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..color = color.withValues(alpha: 0.15)
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke,
    );

    // Progress arc
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -pi / 2,
      2 * pi * progress,
      false,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.progress != progress || old.color != color;
}
