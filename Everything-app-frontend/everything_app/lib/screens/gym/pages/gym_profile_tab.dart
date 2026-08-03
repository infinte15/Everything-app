import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/body_activation_map.dart';
import '../widgets/gym_session_card.dart';

class GymProfileTab extends StatefulWidget {
  const GymProfileTab({super.key});

  @override
  State<GymProfileTab> createState() => _GymProfileTabState();
}

class _GymProfileTabState extends State<GymProfileTab>
    with SingleTickerProviderStateMixin {
  late final TabController _tab = TabController(length: 3, vsync: this);

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: LyftaTheme.surfaceElevated,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.person_rounded,
                    size: 28,
                    color: LyftaTheme.textSecondary,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Dein Training',
                          style: LyftaTheme.headline.copyWith(fontSize: 22)),
                      const SizedBox(height: 2),
                      Text(
                        '${sports.totalWorkouts} Trainings · '
                        '${(sports.totalVolumeAllTime / 1000).toStringAsFixed(1)} t bewegt',
                        style: LyftaTheme.subtitle,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          TabBar(
            controller: _tab,
            labelColor: LyftaTheme.primary,
            unselectedLabelColor: LyftaTheme.textSecondary,
            indicatorColor: LyftaTheme.primary,
            tabs: const [
              Tab(text: 'Fortschritt'),
              Tab(text: 'Verlauf'),
              Tab(text: 'Körper'),
            ],
          ),
          Expanded(
            child: TabBarView(
              controller: _tab,
              children: [
                _ProgressTab(sports: sports),
                _HistoryTab(sports: sports),
                _BodyTab(sports: sports),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ProgressTab extends StatelessWidget {
  final SportsProvider sports;

  const _ProgressTab({required this.sports});

  @override
  Widget build(BuildContext context) {
    final series = sports.weeklyStats.volumeSeries;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 100),
      children: [
        Text('VOLUMEN JE WOCHE', style: LyftaTheme.label),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.fromLTRB(8, 18, 14, 8),
          decoration: BoxDecoration(
            color: LyftaTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          height: 210,
          child: series.isEmpty
              ? Center(child: Text('Noch keine Daten', style: LyftaTheme.subtitle))
              : LineChart(_chartData(series)),
        ),
        const SizedBox(height: 24),
        Text('KENNZAHLEN', style: LyftaTheme.label),
        const SizedBox(height: 12),
        _statTile(
          Icons.local_fire_department_rounded,
          'Aktuelle Serie',
          '${sports.weeklyStats.currentStreakWeeks} Wochen in Folge',
        ),
        _statTile(
          Icons.emoji_events_outlined,
          'Längste Serie',
          '${sports.weeklyStats.longestStreakWeeks} Wochen',
        ),
        _statTile(
          Icons.timer_outlined,
          'Standard-Pause',
          '${sports.defaultRestSeconds} Sekunden',
        ),
      ],
    );
  }

  LineChartData _chartData(List<GymVolumePoint> series) {
    final maxVolume = series.fold<double>(0, (m, p) => p.volumeKg > m ? p.volumeKg : m);

    return LineChartData(
      minY: 0,
      // Bei durchweg 0 kg braucht die Achse trotzdem eine Höhe.
      maxY: maxVolume <= 0 ? 1 : maxVolume * 1.2,
      gridData: FlGridData(
        show: true,
        drawVerticalLine: false,
        getDrawingHorizontalLine: (_) =>
            // divider ist ein Rahmen-Wert; ueber die volle Diagrammbreite
            // gezogen ist er schwerer als der alte #38383A - daher abgesenkt.
            FlLine(
              color: LyftaTheme.divider.withValues(alpha: 0.45),
              strokeWidth: 0.6,
            ),
      ),
      borderData: FlBorderData(show: false),
      titlesData: FlTitlesData(
        leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        bottomTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 26,
            interval: 1,
            getTitlesWidget: (value, meta) {
              final index = value.toInt();
              if (index < 0 || index >= series.length) return const SizedBox.shrink();
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
      lineBarsData: [
        LineChartBarData(
          spots: [
            for (var i = 0; i < series.length; i++)
              FlSpot(i.toDouble(), series[i].volumeKg),
          ],
          isCurved: true,
          barWidth: 2.5,
          color: LyftaTheme.primary,
          dotData: FlDotData(
            show: true,
            getDotPainter: (_, _, _, _) => FlDotCirclePainter(
              radius: 3,
              color: LyftaTheme.primary,
              strokeWidth: 0,
            ),
          ),
          belowBarData: BarAreaData(
            show: true,
            color: LyftaTheme.primary.withValues(alpha: 0.15),
          ),
        ),
      ],
    );
  }

  Widget _statTile(IconData icon, String label, String value) => Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: LyftaTheme.surface,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(icon, size: 20, color: LyftaTheme.primary),
            const SizedBox(width: 14),
            Expanded(child: Text(label, style: LyftaTheme.title.copyWith(fontSize: 15))),
            Text(value, style: LyftaTheme.caption),
          ],
        ),
      );
}

class _HistoryTab extends StatelessWidget {
  final SportsProvider sports;

  const _HistoryTab({required this.sports});

  @override
  Widget build(BuildContext context) {
    if (sports.sessions.isEmpty) {
      return Center(
        child: Text('Noch keine Trainings', style: LyftaTheme.subtitle),
      );
    }
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 100),
      itemCount: sports.sessions.length,
      itemBuilder: (_, i) => GymSessionCard(session: sports.sessions[i]),
    );
  }
}

/// Ganzseitige Körper-Grafik mit Zeitraum-Auswahl.
class _BodyTab extends StatefulWidget {
  final SportsProvider sports;

  const _BodyTab({required this.sports});

  @override
  State<_BodyTab> createState() => _BodyTabState();
}

class _BodyTabState extends State<_BodyTab> {
  static const List<(String, int)> _ranges = [
    ('Woche', 7),
    ('Monat', 30),
    ('3 Monate', 90),
  ];

  int _days = 7;

  void _select(int days) {
    setState(() => _days = days);
    widget.sports.loadMuscleVolume(
      from: DateTime.now().subtract(Duration(days: days - 1)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final volumes = widget.sports.muscleVolumes.where((m) => m.share > 0).toList()
      ..sort((a, b) => b.weightedSets.compareTo(a.weightedSets));

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 100),
      children: [
        Row(
          children: _ranges
              .map((range) => Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: GestureDetector(
                      onTap: () => _select(range.$2),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 8),
                        decoration: BoxDecoration(
                          color: _days == range.$2
                              ? LyftaTheme.surfaceHighlight
                              : LyftaTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          range.$1,
                          style: LyftaTheme.caption.copyWith(
                            color: _days == range.$2
                                ? LyftaTheme.primary
                                : LyftaTheme.textSecondary,
                          ),
                        ),
                      ),
                    ),
                  ))
              .toList(),
        ),
        const SizedBox(height: 18),
        SizedBox(
          height: 420,
          child: BodyActivationMap(
            activation: widget.sports.muscleActivation,
          ),
        ),
        const SizedBox(height: 16),
        const BodyActivationLegend(),
        const SizedBox(height: 26),
        Text('SÄTZE JE MUSKELGRUPPE', style: LyftaTheme.label),
        const SizedBox(height: 12),
        if (volumes.isEmpty)
          Text(
            'Für diesen Zeitraum sind keine Sätze aufgezeichnet.',
            style: LyftaTheme.subtitle,
          )
        else
          ...volumes.map((m) => _MuscleBar(volume: m, max: volumes.first.weightedSets)),
      ],
    );
  }
}

class _MuscleBar extends StatelessWidget {
  final GymMuscleVolume volume;
  final double max;

  const _MuscleBar({required this.volume, required this.max});

  @override
  Widget build(BuildContext context) {
    final fraction = max > 0 ? (volume.weightedSets / max).clamp(0.0, 1.0) : 0.0;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(volume.label, style: LyftaTheme.caption.copyWith(
                  color: LyftaTheme.textPrimary,
                )),
              ),
              Text(
                volume.weightedSets % 1 == 0
                    ? '${volume.weightedSets.toInt()} Sätze'
                    : '${volume.weightedSets.toStringAsFixed(1)} Sätze',
                style: LyftaTheme.caption,
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: fraction,
              minHeight: 6,
              backgroundColor: LyftaTheme.surfaceElevated,
              color: LyftaTheme.primary,
            ),
          ),
        ],
      ),
    );
  }
}
