import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../workout/active_workout_page.dart';
import '../widgets/create_routine_sheet.dart';

class GymWorkoutTab extends StatelessWidget {
  const GymWorkoutTab({super.key});

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          floating: true,
          backgroundColor: LyftaTheme.background,
          title: Text('Workout', style: LyftaTheme.title),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              Text('My Routines', style: LyftaTheme.headline.copyWith(fontSize: 22)),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => CreateRoutineSheet.show(context),
                icon: const Icon(Icons.add, color: LyftaTheme.primary),
                label: const Text('Create Routine', style: TextStyle(color: LyftaTheme.primary)),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: LyftaTheme.primary),
                  minimumSize: const Size.fromHeight(48),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
              const SizedBox(height: 16),
              if (sports.workoutPlans.isEmpty)
                Text('No routines yet', style: LyftaTheme.subtitle)
              else
                ...sports.workoutPlans.map((p) => _RoutineCard(plan: p)),
              const SizedBox(height: 28),
              Text('Programs', style: LyftaTheme.headline.copyWith(fontSize: 22)),
              const SizedBox(height: 8),
              Text('Expert templates — tap to preview', style: LyftaTheme.subtitle),
              const SizedBox(height: 12),
              ...sports.programTemplates.map((t) => _ProgramCard(template: t)),
            ]),
          ),
        ),
      ],
    );
  }
}

class _RoutineCard extends StatelessWidget {
  final Map<String, dynamic> plan;
  const _RoutineCard({required this.plan});

  @override
  Widget build(BuildContext context) {
    final exercises = plan['exercises'] as List? ?? [];
    final duration = plan['estimatedDuration'] as int? ?? 60;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(plan['name'] as String? ?? '', style: LyftaTheme.title)),
              if (plan['day'] != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: LyftaTheme.primary.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    '${plan['day']}',
                    style: const TextStyle(color: LyftaTheme.primary, fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text('$duration min · ${exercises.length} exercises', style: LyftaTheme.subtitle),
          const SizedBox(height: 12),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: exercises.take(4).map((ex) {
              final name = (ex as Map)['name'] as String? ?? '';
              return Chip(
                label: Text(name, style: const TextStyle(fontSize: 12)),
                visualDensity: VisualDensity.compact,
                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
              );
            }).toList(),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () async {
                final sports = context.read<SportsProvider>();
                final ok = await sports.startWorkout(plan['id'] as int);
                if (ok && context.mounted) {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
                  );
                }
              },
              child: const Text('Start Routine'),
            ),
          ),
        ],
      ),
    );
  }
}

class _ProgramCard extends StatelessWidget {
  final GymProgramTemplate template;
  const _ProgramCard({required this.template});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LyftaTheme.divider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(template.name, style: LyftaTheme.title)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: LyftaTheme.surfaceElevated,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(template.level, style: LyftaTheme.caption),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(template.description, style: LyftaTheme.subtitle),
          const SizedBox(height: 8),
          Text(
            '${template.daysPerWeek} days/week · ${template.routines.length} routines',
            style: LyftaTheme.caption,
          ),
        ],
      ),
    );
  }
}
