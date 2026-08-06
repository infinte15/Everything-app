import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../models/finance_forecast.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';
import 'finance_section.dart';

/// Saldokurve des Monats: Ist durchgezogen, Prognose gestrichelt.
///
/// Die Trennung ist der ganze Punkt. Eine durchgehende Linie bis Monatsende
/// gäbe eine Gewissheit vor, die es nicht gibt - der rechte Teil ist ein
/// Durchschnitt, kein Kontoauszug.
///
/// Achsen und Raster folgen der Regel des Gym Space: links, oben und rechts
/// nichts, horizontales Raster bei 45 % Deckkraft.
class ForecastChart extends StatelessWidget {
  final FinanceForecast forecast;

  const ForecastChart({super.key, required this.forecast});

  @override
  Widget build(BuildContext context) {
    final points = forecast.series;
    if (points.length < 2) {
      return const SizedBox.shrink();
    }

    final actual = <FlSpot>[];
    final projected = <FlSpot>[];
    double min = points.first.balance;
    double max = points.first.balance;
    int? lastActualIndex;

    for (var i = 0; i < points.length; i++) {
      final point = points[i];
      final spot = FlSpot(i.toDouble(), point.balance);
      if (point.projected) {
        projected.add(spot);
      } else {
        actual.add(spot);
        lastActualIndex = i;
      }
      if (point.balance < min) min = point.balance;
      if (point.balance > max) max = point.balance;
    }

    // Die beiden Linien müssen sich berühren, sonst klafft am heutigen Tag eine
    // Lücke in der Kurve.
    if (lastActualIndex != null && projected.isNotEmpty) {
      projected.insert(0, FlSpot(lastActualIndex.toDouble(), points[lastActualIndex].balance));
    }

    final range = (max - min).abs();
    final padding = range < 1 ? 50.0 : range * 0.2;
    // Die Nulllinie muss sichtbar sein, wenn die Kurve sie kreuzt: sonst sieht
    // ein Verlauf ins Minus aus wie ein normaler Rückgang.
    final minY = min < 0 ? min - padding : (min - padding).clamp(0.0, double.infinity);

    return FinanceCard(
      padding: const EdgeInsets.fromLTRB(8, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 8),
            child: Row(
              children: [
                Expanded(child: Text('SALDOVERLAUF', style: KineticTheme.label)),
                const _LegendDot(label: 'Ist', dashed: false),
                const SizedBox(width: 12),
                const _LegendDot(label: 'Prognose', dashed: true),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 170,
            child: LineChart(
              LineChartData(
                minY: minY,
                maxY: max + padding,
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: false,
                  getDrawingHorizontalLine: (value) => FlLine(
                    color: value.abs() < 0.01
                        ? FinanceTheme.shortfall.withValues(alpha: 0.5)
                        : KineticTheme.divider.withValues(alpha: 0.45),
                    strokeWidth: value.abs() < 0.01 ? 1 : 0.6,
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
                      reservedSize: 24,
                      // Bei 31 Tagen würde jede Beschriftung zu Matsch verlaufen.
                      interval: (points.length / 4).ceilToDouble(),
                      getTitlesWidget: (value, meta) {
                        final i = value.round();
                        if (i < 0 || i >= points.length) return const SizedBox.shrink();
                        return Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: Text(
                            '${points[i].date.day}.',
                            style: KineticTheme.label.copyWith(fontSize: 9),
                          ),
                        );
                      },
                    ),
                  ),
                ),
                lineTouchData: LineTouchData(
                  touchTooltipData: LineTouchTooltipData(
                    tooltipBgColor: KineticTheme.surfaceHighlight,
                    getTooltipItems: (spots) => spots.map((spot) {
                      final point = points[spot.x.round().clamp(0, points.length - 1)];
                      return LineTooltipItem(
                        '${FinanceFormat.shortDate(point.date)}\n'
                        '${FinanceFormat.money(point.balance)}',
                        KineticTheme.caption.copyWith(color: KineticTheme.textPrimary),
                      );
                    }).toList(),
                  ),
                ),
                lineBarsData: [
                  _line(actual, dashed: false),
                  _line(projected, dashed: true),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  LineChartBarData _line(List<FlSpot> spots, {required bool dashed}) {
    return LineChartBarData(
      spots: spots,
      isCurved: true,
      curveSmoothness: 0.2,
      barWidth: 2,
      color: dashed ? KineticTheme.primary.withValues(alpha: 0.55) : KineticTheme.primary,
      dashArray: dashed ? const [5, 4] : null,
      dotData: const FlDotData(show: false),
      belowBarData: BarAreaData(
        show: !dashed,
        color: KineticTheme.primary.withValues(alpha: 0.07),
      ),
    );
  }
}

class _LegendDot extends StatelessWidget {
  final String label;
  final bool dashed;

  const _LegendDot({required this.label, required this.dashed});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 2,
          color: KineticTheme.primary.withValues(alpha: dashed ? 0.55 : 1),
        ),
        const SizedBox(width: 5),
        Text(label, style: KineticTheme.label.copyWith(fontSize: 9)),
      ],
    );
  }
}
