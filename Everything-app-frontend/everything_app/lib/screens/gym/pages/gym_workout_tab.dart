import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/create_routine_sheet.dart';
import '../widgets/routine_card.dart';
import '../widgets/week_schedule_card.dart';
import '../workout/active_workout_page.dart';
import 'routine_detail_page.dart';

class GymWorkoutTab extends StatelessWidget {
  final VoidCallback onOpenActiveWorkout;

  const GymWorkoutTab({super.key, required this.onOpenActiveWorkout});

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return RefreshIndicator(
      color: LyftaTheme.primary,
      backgroundColor: LyftaTheme.surface,
      onRefresh: sports.loadData,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverAppBar(
            floating: true,
            backgroundColor: LyftaTheme.background,
            title: Text('Plan', style: LyftaTheme.title),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                Text(
                  'Wochenplan',
                  style: LyftaTheme.headline.copyWith(fontSize: 22),
                ),
                const SizedBox(height: 4),
                Text(
                  'Ein fester Tag legt die Routine dorthin. Tage ohne Routine bleiben frei '
                  'für Einheiten, die der Kalender selbst verteilt.',
                  style: LyftaTheme.subtitle.copyWith(fontSize: 13),
                ),
                const SizedBox(height: 12),
                const WeekScheduleCard(),
                const SizedBox(height: 26),
                Text(
                  'Meine Routinen',
                  style: LyftaTheme.headline.copyWith(fontSize: 22),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => CreateRoutineSheet.show(context),
                  icon: const Icon(Icons.add, color: LyftaTheme.primary),
                  label: const Text(
                    'Routine erstellen',
                    style: TextStyle(color: LyftaTheme.primary),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: LyftaTheme.primary),
                    minimumSize: const Size.fromHeight(48),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                if (sports.isLoading && sports.routines.isEmpty)
                  const Center(
                    child: Padding(
                      padding: EdgeInsets.symmetric(vertical: 40),
                      child: CircularProgressIndicator(color: LyftaTheme.primary),
                    ),
                  )
                else if (sports.routines.isEmpty)
                  const _NoRoutines()
                else
                  ...sports.routines.map(
                    (routine) => RoutineCard(
                      routine: routine,
                      muscleOptions: sports.muscleOptions,
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => RoutineDetailPage(routineId: routine.id),
                        ),
                      ),
                      onStart: () => _startRoutine(context, routine.id),
                    ),
                  ),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _startRoutine(BuildContext context, int routineId) async {
    final sports = context.read<SportsProvider>();
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);

    if (sports.hasActiveWorkout) {
      onOpenActiveWorkout();
      return;
    }

    final started = await sports.startRoutine(routineId);
    if (!started) {
      messenger.showSnackBar(
        SnackBar(content: Text(sports.error ?? 'Training konnte nicht gestartet werden')),
      );
      return;
    }
    navigator.push(MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()));
  }
}

class _NoRoutines extends StatelessWidget {
  const _NoRoutines();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          const Icon(Icons.list_alt_rounded, size: 32, color: LyftaTheme.textTertiary),
          const SizedBox(height: 12),
          Text('Noch keine Routinen', style: LyftaTheme.title),
          const SizedBox(height: 6),
          Text(
            'Stelle dir eine Routine aus dem Übungskatalog zusammen - '
            'sie merkt sich Sätze, Wiederholungen und Pausen.',
            style: LyftaTheme.subtitle.copyWith(fontSize: 13),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
