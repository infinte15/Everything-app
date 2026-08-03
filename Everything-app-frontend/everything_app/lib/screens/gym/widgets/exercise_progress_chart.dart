import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';

/// Welche Kennzahl der Verlauf zeigt.
enum ExerciseMetric {
  /// Bestes geschätztes Einer-Maximum der Einheit - das ehrlichste Signal für
  /// Kraftfortschritt, weil es Gewicht und Wiederholungen verrechnet.
  estimated1RM,

  /// Schwerster Satz der Einheit.
  topSet,

  /// Gesamtvolumen der Einheit.
  volume,
}

extension _MetricLabel on ExerciseMetric {
  String get label => switch (this) {
        ExerciseMetric.estimated1RM => '1RM',
        ExerciseMetric.topSet => 'Top-Satz',
        ExerciseMetric.volume => 'Volumen',
      };

  double? valueOf(GymHistoryEntry e) => switch (this) {
        ExerciseMetric.estimated1RM => e.estimated1RM,
        ExerciseMetric.topSet => e.bestSetWeight,
        ExerciseMetric.volume => e.totalVolumeKg,
      };

  String format(double v) => switch (this) {
        ExerciseMetric.volume => gymFormatVolume(v),
        _ => '${gymFormatNumber(v)} kg',
      };
}

/// Verlauf einer einzelnen Übung über die protokollierten Einheiten.
class ExerciseProgressChart extends StatefulWidget {
  /// Verlauf wie vom Server geliefert: neueste Einheit zuerst.
  final List<GymHistoryEntry> history;

  const ExerciseProgressChart({super.key, required this.history});

  @override
  State<ExerciseProgressChart> createState() => _ExerciseProgressChartState();
}

class _ExerciseProgressChartState extends State<ExerciseProgressChart> {
  ExerciseMetric _metric = ExerciseMetric.estimated1RM;

  /// Chronologisch für die Achse. Der Server sortiert absteigend, und zwei
  /// Einheiten am selben Tag haben untereinander keine garantierte Reihenfolge -
  /// deshalb zusätzlich sortieren. Bewusst hier und nicht im Provider: die
  /// Verlaufsliste unter dem Diagramm braucht weiter "neueste zuerst".
  List<_Point> get _points {
    final entries = widget.history
        .where((e) => e.performedAt != null)
        .toList()
      ..sort((a, b) => a.performedAt!.compareTo(b.performedAt!));

    final points = <_Point>[];
    for (final e in entries) {
      final value = _metric.valueOf(e);
      if (value == null || value <= 0) continue;
      points.add(_Point(e.performedAt!, value));
    }
    return points;
  }

  @override
  Widget build(BuildContext context) {
    final points = _points;

    return Container(
      padding: const EdgeInsets.fromLTRB(8, 14, 14, 10),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 8, bottom: 12),
            // Der Umschalter bleibt auch ohne Daten sichtbar: eine Übung ohne
            // Gewicht hat trotzdem fast immer ein Volumen.
            child: _MetricToggle(
              selected: _metric,
              onChanged: (m) => setState(() => _metric = m),
            ),
          ),
          if (points.isEmpty)
            _empty('Noch keine Daten für diese Kennzahl.')
          else if (points.length == 1)
            _single(points.first)
          else
            SizedBox(height: 180, child: LineChart(_chartData(points))),
        ],
      ),
    );
  }

  Widget _empty(String text) => Padding(
        padding: const EdgeInsets.fromLTRB(8, 22, 8, 26),
        child: Center(child: Text(text, style: LyftaTheme.subtitle)),
      );

  /// Ein einzelner Punkt ergibt keine Linie - dann lieber die Zahl groß zeigen.
  Widget _single(_Point point) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 10, 8, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _metric.format(point.value),
            style: LyftaTheme.headline.copyWith(fontSize: 26),
          ),
          const SizedBox(height: 4),
          Text(
            'am ${DateFormat('d. MMMM yyyy', 'de_DE').format(point.date)}',
            style: LyftaTheme.caption,
          ),
          const SizedBox(height: 8),
          Text(
            'Ab der zweiten Einheit entsteht hier eine Verlaufskurve.',
            style: LyftaTheme.caption.copyWith(color: LyftaTheme.textTertiary),
          ),
        ],
      ),
    );
  }

  LineChartData _chartData(List<_Point> points) {
    var min = points.first.value;
    var max = points.first.value;
    for (final p in points) {
      if (p.value < min) min = p.value;
      if (p.value > max) max = p.value;
    }

    // Volumen ist eine Menge und gehört auf die Null bezogen. 1RM und Top-Satz
    // sind Niveaus - mit Nulllinie würde aus 100 -> 105 kg eine flache Gerade.
    final double minY;
    final double maxY;
    if (_metric == ExerciseMetric.volume) {
      minY = 0;
      maxY = max <= 0 ? 1 : max * 1.2;
    } else if (max - min < 0.0001) {
      minY = (min * 0.9).clamp(0, double.infinity);
      maxY = max <= 0 ? 1 : max * 1.1;
    } else {
      final range = max - min;
      minY = (min - range * 0.25).clamp(0, double.infinity);
      maxY = max + range * 0.25;
    }

    return LineChartData(
      minY: minY,
      maxY: maxY,
      gridData: FlGridData(
        show: true,
        drawVerticalLine: false,
        getDrawingHorizontalLine: (_) => FlLine(
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
            // Bei 50 Einheiten würde jede Beschriftung zu Matsch verlaufen.
            interval: (points.length / 4).ceilToDouble(),
            getTitlesWidget: (value, meta) {
              final i = value.round();
              if (i < 0 || i >= points.length) return const SizedBox.shrink();
              final d = points[i].date;
              return Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  '${d.day}.${d.month}.',
                  style: LyftaTheme.label.copyWith(fontSize: 9),
                ),
              );
            },
          ),
        ),
      ),
      lineTouchData: LineTouchData(
        touchTooltipData: LineTouchTooltipData(
          tooltipBgColor: LyftaTheme.surfaceHighlight,
          getTooltipItems: (spots) => spots.map((spot) {
            final p = points[spot.x.round()];
            return LineTooltipItem(
              '${_metric.format(p.value)}\n'
              '${DateFormat('d.M.yyyy', 'de_DE').format(p.date)}',
              LyftaTheme.caption.copyWith(color: LyftaTheme.textPrimary),
            );
          }).toList(),
        ),
      ),
      lineBarsData: [
        LineChartBarData(
          spots: [
            for (var i = 0; i < points.length; i++)
              FlSpot(i.toDouble(), points[i].value),
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
}

class _Point {
  final DateTime date;
  final double value;

  const _Point(this.date, this.value);
}

/// Segmentierte Umschaltung im Stil der Vorne/Hinten-Umschaltung der Körperkarte.
/// Bewusst kein [ChoiceChip]: der erbt das globale Chip-Theme und passt dann
/// nicht zum Rest der Gym-Oberfläche.
class _MetricToggle extends StatelessWidget {
  final ExerciseMetric selected;
  final ValueChanged<ExerciseMetric> onChanged;

  const _MetricToggle({required this.selected, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: ExerciseMetric.values.map((metric) {
          final active = metric == selected;
          return GestureDetector(
            onTap: () => onChanged(metric),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
              decoration: BoxDecoration(
                color: active ? LyftaTheme.surfaceHighlight : Colors.transparent,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                metric.label,
                style: LyftaTheme.caption.copyWith(
                  color: active ? LyftaTheme.textPrimary : LyftaTheme.textSecondary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}
