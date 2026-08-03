import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/body_activation_map.dart';
import '../widgets/gym_session_card.dart';
import '../widgets/routine_card.dart' show muscleLabel;
import '../widgets/week_stats_grid.dart';

class GymHomeTab extends StatelessWidget {
  final VoidCallback onStartWorkout;
  final VoidCallback onOpenActiveWorkout;

  const GymHomeTab({
    super.key,
    required this.onStartWorkout,
    required this.onOpenActiveWorkout,
  });

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
            leading: IconButton(
              icon: const Icon(Icons.arrow_back_rounded),
              tooltip: 'Zurück zu den Bereichen',
              // /sports liegt ausserhalb der Shell-Route, es gibt hier also keine
              // gemeinsame Navigationsleiste, die zurückführen würde.
              onPressed: () =>
                  context.canPop() ? context.pop() : context.go('/spaces'),
            ),
            title: Row(
              children: [
                Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: LyftaTheme.primary,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.fitness_center_rounded,
                    size: 18,
                    color: LyftaTheme.onPrimary,
                  ),
                ),
                const SizedBox(width: 10),
                Text('Gym', style: LyftaTheme.title),
              ],
            ),
            actions: [
              if (sports.hasActiveWorkout)
                TextButton(
                  onPressed: onOpenActiveWorkout,
                  child: const Text('Fortsetzen'),
                ),
            ],
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                if (sports.error != null) ...[
                  _ErrorBanner(message: sports.error!, onRetry: sports.loadData),
                  const SizedBox(height: 16),
                ],
                if (sports.hasActiveWorkout) ...[
                  _ActiveBanner(
                    name: sports.activeWorkout!.name,
                    onTap: onOpenActiveWorkout,
                  ),
                  const SizedBox(height: 16),
                ],
                FilledButton.icon(
                  onPressed: onStartWorkout,
                  icon: const Icon(Icons.play_arrow_rounded),
                  label: Text(
                    sports.hasActiveWorkout
                        ? 'Training fortsetzen'
                        : 'Training starten',
                  ),
                ),
                const SizedBox(height: 26),
                const _SectionTitle('Diese Woche'),
                const SizedBox(height: 12),
                WeekStatsGrid(stats: sports.weeklyStats),
                const SizedBox(height: 26),
                const _SectionTitle('Muskelbelastung'),
                const SizedBox(height: 4),
                Text(
                  'Wie stark die Muskelgruppen diese Woche beansprucht wurden.',
                  style: LyftaTheme.subtitle.copyWith(fontSize: 13),
                ),
                const SizedBox(height: 12),
                _BodyCard(sports: sports),
                const SizedBox(height: 26),
                const _SectionTitle('Volumen'),
                const SizedBox(height: 12),
                _VolumeChartCard(series: sports.weeklyStats.volumeSeries),
                const SizedBox(height: 26),
                const _SectionTitle('Letzte Trainings'),
                const SizedBox(height: 12),
                if (sports.sessions.isEmpty)
                  _EmptyHint(
                    icon: Icons.history_rounded,
                    text: sports.isLoading
                        ? 'Trainings werden geladen …'
                        : 'Noch keine Trainings aufgezeichnet.',
                  )
                else
                  ...sports.sessions.take(3).map((s) => GymSessionCard(session: s)),
              ]),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) =>
      Text(text, style: LyftaTheme.headline.copyWith(fontSize: 22));
}

class _BodyCard extends StatelessWidget {
  final SportsProvider sports;

  const _BodyCard({required this.sports});

  @override
  Widget build(BuildContext context) {
    final activation = sports.muscleActivation;
    final trained = sports.muscleVolumes.where((m) => m.share > 0).toList()
      ..sort((a, b) => b.share.compareTo(a.share));

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          SizedBox(
            height: 300,
            child: Row(
              children: [
                Expanded(
                  child: BodyActivationView(
                    activation: activation,
                    back: false,
                    onMuscleTap: (m) => _showMuscleDetail(context, m),
                  ),
                ),
                Expanded(
                  child: BodyActivationView(
                    activation: activation,
                    back: true,
                    onMuscleTap: (m) => _showMuscleDetail(context, m),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              Text('VORNE', style: LyftaTheme.label),
              Text('HINTEN', style: LyftaTheme.label),
            ],
          ),
          const SizedBox(height: 14),
          const BodyActivationLegend(),
          if (trained.isNotEmpty) ...[
            const SizedBox(height: 16),
            const Divider(height: 1, color: LyftaTheme.divider),
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: trained.take(5).map((m) {
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: LyftaTheme.musclePrimary.withValues(alpha: 0.12 + 0.28 * m.share),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    '${m.label} · ${_formatSets(m.weightedSets)} Sätze',
                    style: LyftaTheme.caption.copyWith(
                      color: LyftaTheme.textPrimary,
                      fontSize: 12,
                    ),
                  ),
                );
              }).toList(),
            ),
          ] else ...[
            const SizedBox(height: 14),
            Text('Noch keine Sätze diese Woche.', style: LyftaTheme.caption),
          ],
        ],
      ),
    );
  }

  void _showMuscleDetail(BuildContext context, String slug) {
    final provider = context.read<SportsProvider>();
    final volume = provider.muscleVolumes.firstWhere(
      (m) => m.muscle == slug,
      orElse: () => GymMuscleVolume(
        muscle: slug,
        label: muscleLabel(slug, provider.muscleOptions),
      ),
    );

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
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
            const SizedBox(height: 18),
            Text(volume.label, style: LyftaTheme.headline.copyWith(fontSize: 22)),
            const SizedBox(height: 4),
            Text('Diese Woche', style: LyftaTheme.subtitle),
            const SizedBox(height: 18),
            Row(
              children: [
                _stat('Sätze', _formatSets(volume.weightedSets)),
                _stat('Volumen', '${volume.volumeKg.round()} kg'),
                _stat('Trainings', '${volume.sessionCount}'),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              'Nebenbeteiligte Muskeln zählen zur Hälfte.',
              style: LyftaTheme.caption,
            ),
          ],
        ),
      ),
    );
  }

  Widget _stat(String label, String value) => Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label.toUpperCase(), style: LyftaTheme.label),
            const SizedBox(height: 4),
            Text(value, style: LyftaTheme.headline.copyWith(fontSize: 20)),
          ],
        ),
      );

  /// Nebenmuskeln zählen halb, deshalb können hier halbe Sätze stehen.
  static String _formatSets(double sets) =>
      sets % 1 == 0 ? sets.toInt().toString() : sets.toStringAsFixed(1);
}

class _VolumeChartCard extends StatelessWidget {
  final List<GymVolumePoint> series;

  const _VolumeChartCard({required this.series});

  @override
  Widget build(BuildContext context) {
    if (series.isEmpty) {
      return const _EmptyHint(
        icon: Icons.bar_chart_rounded,
        text: 'Noch keine Daten für die Volumen-Kurve.',
      );
    }

    final maxVolume =
        series.fold<double>(0, (m, p) => p.volumeKg > m ? p.volumeKg : m);

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 18, 14, 10),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4),
            child: Text('LETZTE 8 WOCHEN', style: LyftaTheme.label),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 150,
            child: BarChart(
              BarChartData(
                alignment: BarChartAlignment.spaceAround,
                // Ohne Mindesthöhe kollabiert die Achse in einer trainingsfreien Zeit.
                maxY: maxVolume <= 0 ? 1 : maxVolume * 1.2,
                borderData: FlBorderData(show: false),
                gridData: const FlGridData(show: false),
                titlesData: FlTitlesData(
                  leftTitles:
                      const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  topTitles:
                      const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  rightTitles:
                      const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 24,
                      getTitlesWidget: (value, meta) {
                        final index = value.toInt();
                        if (index < 0 || index >= series.length) {
                          return const SizedBox.shrink();
                        }
                        final week = series[index].weekStart;
                        return Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: Text(
                            week == null ? '' : '${week.day}.${week.month}.',
                            style: LyftaTheme.label.copyWith(fontSize: 9),
                          ),
                        );
                      },
                    ),
                  ),
                ),
                barTouchData: BarTouchData(
                  touchTooltipData: BarTouchTooltipData(
                    tooltipBgColor: LyftaTheme.surfaceHighlight,
                    getTooltipItem: (group, groupIndex, rod, rodIndex) {
                      final point = series[group.x];
                      return BarTooltipItem(
                        '${point.volumeKg.round()} kg\n${point.workouts} Trainings',
                        LyftaTheme.caption.copyWith(color: LyftaTheme.textPrimary),
                      );
                    },
                  ),
                ),
                barGroups: [
                  for (var i = 0; i < series.length; i++)
                    BarChartGroupData(
                      x: i,
                      barRods: [
                        BarChartRodData(
                          toY: series[i].volumeKg,
                          width: 16,
                          borderRadius:
                              const BorderRadius.vertical(top: Radius.circular(4)),
                          // Die laufende Woche wird hervorgehoben.
                          color: i == series.length - 1
                              ? LyftaTheme.primary
                              : LyftaTheme.surfaceHighlight,
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ActiveBanner extends StatelessWidget {
  final String name;
  final VoidCallback onTap;

  const _ActiveBanner({required this.name, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: LyftaTheme.surfaceElevated,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              Container(
                width: 8,
                height: 8,
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
                    Text('Training läuft',
                        style: LyftaTheme.title.copyWith(fontSize: 15)),
                    Text(name, style: LyftaTheme.caption),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: LyftaTheme.primary),
            ],
          ),
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _ErrorBanner({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.danger.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: LyftaTheme.danger.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline_rounded,
              color: LyftaTheme.danger, size: 20),
          const SizedBox(width: 12),
          Expanded(child: Text(message, style: LyftaTheme.caption)),
          TextButton(onPressed: onRetry, child: const Text('Erneut')),
        ],
      ),
    );
  }
}

class _EmptyHint extends StatelessWidget {
  final IconData icon;
  final String text;

  const _EmptyHint({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Icon(icon, size: 28, color: LyftaTheme.textTertiary),
          const SizedBox(height: 10),
          Text(text, style: LyftaTheme.subtitle, textAlign: TextAlign.center),
        ],
      ),
    );
  }
}
