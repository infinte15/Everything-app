import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/body_activation_map.dart';
import '../widgets/training_heatmap.dart';
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
        Text('TRAININGSTAGE', style: LyftaTheme.label),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: LyftaTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: TrainingHeatmap(sessions: sports.sessions),
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
        const SizedBox(height: 24),
        Text('TRAINING', style: LyftaTheme.label),
        const SizedBox(height: 12),
        _switchTile(
          Icons.whatshot_outlined,
          'Aufwärmsätze automatisch',
          'Legt beim Start die Rampe zum Arbeitsgewicht an.',
          sports.autoWarmup,
          sports.setAutoWarmup,
        ),
        _switchTile(
          Icons.speed_outlined,
          'RIR/RPE-Spalte',
          'Gefühlte Anstrengung neben jedem Satz mitschreiben.',
          sports.showRpe,
          sports.setShowRpe,
        ),
      ],
    );
  }

  Widget _switchTile(IconData icon, String title, String subtitle, bool value,
      ValueChanged<bool> onChanged) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(icon, size: 18, color: LyftaTheme.textTertiary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: LyftaTheme.title.copyWith(fontSize: 14)),
                const SizedBox(height: 2),
                Text(subtitle, style: LyftaTheme.caption),
              ],
            ),
          ),
          Switch(
            value: value,
            onChanged: onChanged,
            activeThumbColor: LyftaTheme.primary,
          ),
        ],
      ),
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

  /// Zwei Sichten auf dieselbe Grafik: was zuletzt *belastet* wurde und was davon noch
  /// *nachwirkt*. Die erste beantwortet "habe ich genug für den Rücken getan", die zweite
  /// "kann ich heute Beine machen" - dieselben Flächen, andere Frage.
  bool _showRecovery = false;

  @override
  void initState() {
    super.initState();
    // Der Erholungsstand wird nicht mit dem Rest geladen: er greift auf acht Wochen
    // Verlauf zu und wird nur hier gebraucht.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.sports.loadRecovery();
    });
  }

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
    final recovery = widget.sports.recovery;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 100),
      children: [
        SegmentedButton<bool>(
          segments: const [
            ButtonSegment(value: false, label: Text('Belastung')),
            ButtonSegment(value: true, label: Text('Erholung')),
          ],
          selected: {_showRecovery},
          showSelectedIcon: false,
          onSelectionChanged: (v) => setState(() => _showRecovery = v.first),
          // Gold ist im Space die Farbe für Bestleistungen; eine Umschaltung ist keine.
          // Deshalb dieselbe Auszeichnung wie bei den Zeitraum-Chips darunter.
          style: SegmentedButton.styleFrom(
            backgroundColor: LyftaTheme.surfaceElevated,
            foregroundColor: LyftaTheme.textSecondary,
            selectedBackgroundColor: LyftaTheme.surfaceHighlight,
            selectedForegroundColor: LyftaTheme.primary,
            side: const BorderSide(color: LyftaTheme.divider),
          ),
        ),
        const SizedBox(height: 16),
        if (!_showRecovery)
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
            activation: _showRecovery
                ? {for (final m in recovery) m.muscle: m.fatigue}
                : widget.sports.muscleActivation,
          ),
        ),
        const SizedBox(height: 16),
        BodyActivationLegend(
          low: _showRecovery ? 'erholt' : 'wenig',
          high: _showRecovery ? 'ermüdet' : 'viel',
        ),
        const SizedBox(height: 26),
        if (_showRecovery) ...[
          Text('ERHOLUNG JE MUSKELGRUPPE', style: LyftaTheme.label),
          const SizedBox(height: 6),
          Text(
            'Rot heißt: noch ermüdet. Der Maßstab ist deine härteste Einheit der '
            'letzten acht Wochen.',
            style: LyftaTheme.caption,
          ),
          const SizedBox(height: 12),
          if (recovery.every((m) => m.lastTrainedAt == null))
            Text('Noch keine Einheit, an der sich etwas messen ließe.',
                style: LyftaTheme.subtitle)
          else
            ...(recovery.toList()
                  ..sort((a, b) => b.fatigue.compareTo(a.fatigue)))
                .map((m) => _RecoveryBar(recovery: m)),
        ] else ...[
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
      ],
    );
  }
}

/// Eine Zeile der Erholungsliste: wie viel noch nachwirkt und wann es vorbei ist.
class _RecoveryBar extends StatelessWidget {
  final GymMuscleRecovery recovery;

  const _RecoveryBar({required this.recovery});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(recovery.label,
                    style: LyftaTheme.caption
                        .copyWith(color: LyftaTheme.textPrimary)),
              ),
              Text(
                recovery.readyLabel,
                style: LyftaTheme.caption.copyWith(
                  color: recovery.isReady
                      ? LyftaTheme.textSecondary
                      : LyftaTheme.musclePrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: recovery.fatigue.clamp(0.0, 1.0),
              minHeight: 6,
              backgroundColor: LyftaTheme.surfaceElevated,
              // Dieselbe Farbcodierung wie die Grafik darüber: rot ist belasteter Muskel.
              valueColor: const AlwaysStoppedAnimation(LyftaTheme.musclePrimary),
            ),
          ),
        ],
      ),
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
