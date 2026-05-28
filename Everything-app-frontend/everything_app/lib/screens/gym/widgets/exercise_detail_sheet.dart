import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

class ExerciseDetailSheet extends StatelessWidget {
  final Map<String, dynamic> exercise;

  const ExerciseDetailSheet({super.key, required this.exercise});

  static Future<void> show(BuildContext context, Map<String, dynamic> ex) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: LyftaTheme.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => ExerciseDetailSheet(exercise: ex),
    );
  }

  @override
  Widget build(BuildContext context) {
    final muscles = (exercise['muscleGroups'] as List?)?.cast<String>() ?? [];
    final sports = context.read<SportsProvider>();
    final pr = sports.getPersonalRecord(exercise['name'] as String? ?? '');

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 0.92,
      builder: (_, scroll) => ListView(
        controller: scroll,
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: LyftaTheme.surfaceHighlight,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Container(
            height: 200,
            decoration: BoxDecoration(
              color: LyftaTheme.surfaceElevated,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.play_circle_fill, size: 56, color: LyftaTheme.primary),
                  SizedBox(height: 8),
                  Text('Video guide', style: TextStyle(color: LyftaTheme.textSecondary)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),
          Text(exercise['name'] as String? ?? '', style: LyftaTheme.headline.copyWith(fontSize: 24)),
          const SizedBox(height: 8),
          Text(
            '${exercise['category']} · ${exercise['equipment']}',
            style: LyftaTheme.subtitle,
          ),
          if (pr != null) ...[
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: LyftaTheme.prGold.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  const Icon(Icons.emoji_events, color: LyftaTheme.prGold, size: 20),
                  const SizedBox(width: 10),
                  Text('Personal record: ${pr.round()} kg', style: LyftaTheme.title.copyWith(fontSize: 15)),
                ],
              ),
            ),
          ],
          const SizedBox(height: 20),
          Text('Target muscles', style: LyftaTheme.title),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: muscles
                .map((m) => Chip(label: Text(m), backgroundColor: LyftaTheme.surfaceElevated))
                .toList(),
          ),
          const SizedBox(height: 24),
          DefaultTabController(
            length: 3,
            child: Column(
              children: [
                TabBar(
                  labelColor: LyftaTheme.primary,
                  unselectedLabelColor: LyftaTheme.textTertiary,
                  indicatorColor: LyftaTheme.primary,
                  tabs: const [
                    Tab(text: 'About'),
                    Tab(text: 'History'),
                    Tab(text: 'Charts'),
                  ],
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 80,
                  child: TabBarView(
                    children: [
                      Text('Keep elbows at 45°. Control the eccentric.', style: LyftaTheme.subtitle),
                      Text(
                        sports.getPreviousPerformance(exercise['name'] as String? ?? '') ??
                            'No history yet',
                        style: LyftaTheme.subtitle,
                      ),
                      Text('Volume progression chart', style: LyftaTheme.subtitle),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
