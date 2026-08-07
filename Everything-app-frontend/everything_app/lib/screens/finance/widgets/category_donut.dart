import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';
import 'finance_section.dart';

/// Ausgaben nach Kategorie.
///
/// Ein Ring statt einer Torte: die Mitte trägt die Gesamtsumme, und die ist
/// wichtiger als die Fläche der einzelnen Segmente - Kreissegmente lassen sich
/// ohnehin schlecht vergleichen.
///
/// Ab dem siebten Eintrag wird zusammengefasst. Neun Segmente sind noch
/// auseinanderzuhalten, fünfzehn ergeben einen Farbkreis ohne Aussage.
class CategoryDonut extends StatefulWidget {
  final Map<String, double> spendingByCategory;

  const CategoryDonut({super.key, required this.spendingByCategory});

  @override
  State<CategoryDonut> createState() => _CategoryDonutState();
}

class _CategoryDonutState extends State<CategoryDonut> {
  int? _touched;

  static const _maxSlices = 6;

  @override
  Widget build(BuildContext context) {
    final entries = widget.spendingByCategory.entries
        .where((e) => e.value > 0)
        .toList()
      ..sort((a, b) => b.value.compareTo(a.value));

    if (entries.isEmpty) {
      return const SizedBox.shrink();
    }

    final slices = <MapEntry<String, double>>[];
    for (var i = 0; i < entries.length; i++) {
      if (i < _maxSlices) {
        slices.add(entries[i]);
      } else {
        final rest = entries.skip(_maxSlices).fold(0.0, (sum, e) => sum + e.value);
        slices.add(MapEntry('Weitere', rest));
        break;
      }
    }

    final total = entries.fold(0.0, (sum, e) => sum + e.value);
    final highlighted = _touched != null && _touched! < slices.length
        ? slices[_touched!]
        : null;

    return FinanceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('AUSGABEN NACH KATEGORIE', style: KineticTheme.label),
          const SizedBox(height: 16),
          SizedBox(
            height: 160,
            child: Stack(
              alignment: Alignment.center,
              children: [
                PieChart(
                  PieChartData(
                    sectionsSpace: 2,
                    centerSpaceRadius: 52,
                    startDegreeOffset: -90,
                    pieTouchData: PieTouchData(
                      touchCallback: (event, response) {
                        // fl_chart meldet -1, sobald der Finger nicht auf einem
                        // Segment liegt: im Loch, im Zwischenraum, außerhalb des
                        // Rings. Beim Scrollen über die Karte ist das der
                        // Normalfall - ungeprüft übernommen wird daraus ein
                        // Zugriff auf slices[-1].
                        final index =
                            response?.touchedSection?.touchedSectionIndex ?? -1;
                        setState(() {
                          _touched = event.isInterestedForInteractions && index >= 0
                              ? index
                              : null;
                        });
                      },
                    ),
                    sections: [
                      for (var i = 0; i < slices.length; i++)
                        PieChartSectionData(
                          value: slices[i].value,
                          color: FinanceTheme.categoryColor(slices[i].key),
                          radius: _touched == i ? 26 : 22,
                          showTitle: false,
                        ),
                    ],
                  ),
                ),
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      FinanceFormat.moneyShort(highlighted?.value ?? total),
                      style: KineticTheme.amount.copyWith(fontSize: 19),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      highlighted?.key ?? 'gesamt',
                      style: KineticTheme.label.copyWith(fontSize: 9),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          for (var i = 0; i < slices.length; i++)
            _LegendRow(
              label: slices[i].key,
              amount: slices[i].value,
              share: total > 0 ? slices[i].value / total : 0,
              highlighted: _touched == i,
            ),
        ],
      ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  final String label;
  final double amount;
  final double share;
  final bool highlighted;

  const _LegendRow({
    required this.label,
    required this.amount,
    required this.share,
    required this.highlighted,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: FinanceTheme.categoryColor(label),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              style: KineticTheme.caption.copyWith(
                color: highlighted ? KineticTheme.textPrimary : KineticTheme.textSecondary,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Text(
            '${(share * 100).round()} %',
            style: KineticTheme.label.copyWith(fontSize: 10),
          ),
          const SizedBox(width: 12),
          SizedBox(
            width: 80,
            child: Text(
              FinanceFormat.money(amount),
              style: KineticTheme.amount.copyWith(fontSize: 13),
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }
}
