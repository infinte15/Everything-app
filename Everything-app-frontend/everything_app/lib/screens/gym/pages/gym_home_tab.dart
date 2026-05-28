import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../workout/active_workout_page.dart';
import '../widgets/gym_session_card.dart';
import '../widgets/gym_stat_card.dart';

class GymHomeTab extends StatelessWidget {
  final VoidCallback onStartWorkout;

  const GymHomeTab({super.key, required this.onStartWorkout});

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final weekly = sports.getWeeklyStats();
    final recent = sports.workoutSessions.take(3).toList();

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          floating: true,
          backgroundColor: LyftaTheme.background,
          title: Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: LyftaTheme.primary,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Center(
                  child: Text(
                    'L',
                    style: TextStyle(
                      color: LyftaTheme.onPrimary,
                      fontWeight: FontWeight.w800,
                      fontSize: 18,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Text('Lyfta', style: LyftaTheme.title),
            ],
          ),
          actions: [
            if (sports.currentWorkout != null)
              TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
                ),
                child: const Text('Resume', style: TextStyle(color: LyftaTheme.primary)),
              ),
          ],
        ),
        if (sports.isLoading)
          const SliverFillRemaining(
            child: Center(child: CircularProgressIndicator(color: LyftaTheme.primary)),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                if (sports.currentWorkout != null) ...[
                  _ActiveBanner(sports: sports),
                  const SizedBox(height: 16),
                ],
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: onStartWorkout,
                    icon: const Icon(Icons.play_arrow_rounded),
                    label: const Text('Start Workout'),
                  ),
                ),
                const SizedBox(height: 24),
                Text('This Week', style: LyftaTheme.headline.copyWith(fontSize: 22)),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: GymStatCard(
                        label: 'Workouts',
                        value: '${weekly['workouts']}',
                        sub: '/ ${weekly['goal']} goal',
                        progress: (weekly['workouts'] as int) / (weekly['goal'] as int),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: GymStatCard(
                        label: 'Volume',
                        value: '${(weekly['totalVolume'] as double).round()}',
                        sub: 'kg lifted',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: GymStatCard(
                        label: 'Duration',
                        value: '${weekly['totalMinutes']}',
                        sub: 'minutes',
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: GymStatCard(
                        label: 'Streak',
                        value: '${weekly['streak']}',
                        sub: 'weeks',
                        accent: LyftaTheme.warning,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 28),
                Text('Recent Workouts', style: LyftaTheme.title),
                const SizedBox(height: 12),
                if (recent.isEmpty)
                  Text('No workouts yet — tap Start Workout', style: LyftaTheme.subtitle)
                else
                  ...recent.map((s) => Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: GymSessionCard(session: s),
                      )),
                const SizedBox(height: 28),
                Text('Community', style: LyftaTheme.title),
                const SizedBox(height: 12),
                _FeedCard(
                  name: 'Alex',
                  workout: 'Push Day',
                  volume: '4,200 kg',
                  time: '2h ago',
                ),
                const SizedBox(height: 8),
                _FeedCard(
                  name: 'Sam',
                  workout: 'Leg Day',
                  volume: '6,100 kg',
                  time: '5h ago',
                  isPr: true,
                ),
              ]),
            ),
          ),
      ],
    );
  }
}

class _ActiveBanner extends StatelessWidget {
  final SportsProvider sports;
  const _ActiveBanner({required this.sports});

  @override
  Widget build(BuildContext context) {
    final w = sports.currentWorkout!;
    return Material(
      color: LyftaTheme.primary.withValues(alpha: 0.12),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
        ),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: const BoxDecoration(
                  color: LyftaTheme.primary,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Workout in progress', style: LyftaTheme.caption.copyWith(color: LyftaTheme.primary)),
                    Text(w['name'] as String? ?? '', style: LyftaTheme.title),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: LyftaTheme.primary),
            ],
          ),
        ),
      ),
    );
  }
}

class _FeedCard extends StatelessWidget {
  final String name;
  final String workout;
  final String volume;
  final String time;
  final bool isPr;

  const _FeedCard({
    required this.name,
    required this.workout,
    required this.volume,
    required this.time,
    this.isPr = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: LyftaTheme.surfaceElevated,
            child: Text(name[0], style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                RichText(
                  text: TextSpan(
                    style: LyftaTheme.subtitle.copyWith(color: LyftaTheme.textPrimary),
                    children: [
                      TextSpan(text: name, style: const TextStyle(fontWeight: FontWeight.w600)),
                      TextSpan(text: ' completed $workout'),
                    ],
                  ),
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Text(volume, style: LyftaTheme.caption),
                    const SizedBox(width: 8),
                    Text('· $time', style: LyftaTheme.caption),
                    if (isPr) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: LyftaTheme.prGold.withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Text('PR', style: TextStyle(color: LyftaTheme.prGold, fontSize: 10, fontWeight: FontWeight.bold)),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
