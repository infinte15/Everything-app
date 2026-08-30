import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

/// Körpergewicht: letzter Wert, Veränderung, Verlauf und Ziel.
class BodyWeightCard extends StatelessWidget {
  final GymBodyWeightSeries series;

  const BodyWeightCard({super.key, required this.series});

  @override
  Widget build(BuildContext context) {
    final latest = series.latest;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: latest == null
                    ? Text(
                        'Noch nichts gewogen',
                        style: LyftaTheme.title.copyWith(fontSize: 15),
                      )
                    : _Headline(series: series),
              ),
              TextButton.icon(
                onPressed: () => showBodyWeightSheet(context, series),
                icon: const Icon(Icons.add_rounded, size: 18),
                label: const Text('Wiegen'),
              ),
            ],
          ),
          if (series.entries.length > 1) ...[
            const SizedBox(height: 12),
            SizedBox(height: 110, child: _Curve(series: series)),
          ],
          if (latest == null) ...[
            const SizedBox(height: 6),
            Text(
              'Trag dein Gewicht ein, damit die Kurve anfängt.',
              style: LyftaTheme.subtitle.copyWith(fontSize: 13),
            ),
          ],
        ],
      ),
    );
  }
}

class _Headline extends StatelessWidget {
  final GymBodyWeightSeries series;

  const _Headline({required this.series});

  @override
  Widget build(BuildContext context) {
    final latest = series.latest!;
    final delta = series.delta;
    final toTarget = series.toTarget;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Text(gymFormatNumber(latest.weightKg), style: LyftaTheme.headline.copyWith(fontSize: 30)),
            const SizedBox(width: 4),
            Text('kg', style: LyftaTheme.subtitle),
            if (delta != null) ...[
              const SizedBox(width: 10),
              Icon(
                delta > 0 ? Icons.arrow_upward_rounded : Icons.arrow_downward_rounded,
                size: 13,
                color: LyftaTheme.textSecondary,
              ),
              Text(
                gymFormatNumber(delta.abs()),
                style: LyftaTheme.caption.copyWith(color: LyftaTheme.textSecondary),
              ),
            ],
          ],
        ),
        if (toTarget != null) ...[
          const SizedBox(height: 3),
          Text(
            toTarget.abs() < 0.05
                ? 'Ziel erreicht'
                : '${gymFormatNumber(toTarget.abs())} kg '
                    '${toTarget > 0 ? 'bis zum Ziel' : 'über dem Ziel'}',
            style: LyftaTheme.caption.copyWith(color: LyftaTheme.prAccent),
          ),
        ],
      ],
    );
  }
}

class _Curve extends StatelessWidget {
  final GymBodyWeightSeries series;

  const _Curve({required this.series});

  @override
  Widget build(BuildContext context) {
    final entries = series.entries;
    final spots = <FlSpot>[
      for (var i = 0; i < entries.length; i++)
        FlSpot(entries[i].date.millisecondsSinceEpoch.toDouble(), entries[i].weightKg),
    ];

    final weights = entries.map((e) => e.weightKg).toList();
    final target = series.targetWeightKg;
    // Das Ziel gehört in die Skala, sonst liegt seine Linie außerhalb des Diagramms und man
    // sieht nicht, wie weit es noch ist.
    var minY = weights.reduce((a, b) => a < b ? a : b);
    var maxY = weights.reduce((a, b) => a > b ? a : b);
    if (target != null) {
      minY = minY < target ? minY : target;
      maxY = maxY > target ? maxY : target;
    }
    // Etwas Luft, damit die Linie nicht am Rahmen klebt; bei konstantem Gewicht wäre die
    // Spanne sonst 0 und fl_chart hätte keine Skala.
    final pad = ((maxY - minY) * 0.15).clamp(0.5, 5.0);

    return LineChart(
      LineChartData(
        minY: minY - pad,
        maxY: maxY + pad,
        gridData: const FlGridData(show: false),
        titlesData: const FlTitlesData(show: false),
        borderData: FlBorderData(show: false),
        lineTouchData: const LineTouchData(enabled: false),
        extraLinesData: target == null
            ? const ExtraLinesData()
            : ExtraLinesData(horizontalLines: [
                HorizontalLine(
                  y: target,
                  color: LyftaTheme.prAccent.withValues(alpha: 0.55),
                  strokeWidth: 1,
                  dashArray: [4, 4],
                ),
              ]),
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            curveSmoothness: 0.25,
            color: LyftaTheme.primary,
            barWidth: 2,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              color: LyftaTheme.primary.withValues(alpha: 0.10),
            ),
          ),
        ],
      ),
    );
  }
}

/// Blatt zum Eintragen des Gewichts und Setzen des Ziels.
Future<void> showBodyWeightSheet(BuildContext context, GymBodyWeightSeries series) {
  return showModalBottomSheet<void>(
    context: context,
    backgroundColor: LyftaTheme.surface,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (_) => _BodyWeightSheet(series: series),
  );
}

class _BodyWeightSheet extends StatefulWidget {
  final GymBodyWeightSeries series;

  const _BodyWeightSheet({required this.series});

  @override
  State<_BodyWeightSheet> createState() => _BodyWeightSheetState();
}

class _BodyWeightSheetState extends State<_BodyWeightSheet> {
  late final TextEditingController _weight;
  late final TextEditingController _target;

  @override
  void initState() {
    super.initState();
    // Der letzte Wert als Vorbelegung: das Gewicht ändert sich zwischen zwei Tagen um
    // Nachkommastellen, alles von Hand zu tippen wäre unnötige Arbeit.
    _weight = TextEditingController(
      text: widget.series.latest == null
          ? ''
          : gymFormatNumber(widget.series.latest!.weightKg),
    );
    _target = TextEditingController(
      text: widget.series.targetWeightKg == null
          ? ''
          : gymFormatNumber(widget.series.targetWeightKg!),
    );
  }

  @override
  void dispose() {
    _weight.dispose();
    _target.dispose();
    super.dispose();
  }

  double? _parse(TextEditingController c) =>
      double.tryParse(c.text.trim().replaceAll(',', '.'));

  Future<void> _save() async {
    final sports = context.read<SportsProvider>();
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);

    final weight = _parse(_weight);
    if (weight == null) {
      messenger.showSnackBar(const SnackBar(content: Text('Bitte ein Gewicht eintragen')));
      return;
    }

    final ok = await sports.logBodyWeight(weight);
    // Das Ziel nur anfassen, wenn es sich geändert hat - ein leeres Feld entfernt es.
    final target = _parse(_target);
    if (target != widget.series.targetWeightKg) {
      await sports.setBodyWeightTarget(target);
    }

    if (!mounted) return;
    if (ok) {
      navigator.pop();
    } else {
      messenger.showSnackBar(SnackBar(content: Text(sports.error ?? 'Speichern fehlgeschlagen')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final saving = context.watch<SportsProvider>().isSaving;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        20, 18, 20, MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Gewicht eintragen', style: LyftaTheme.headline.copyWith(fontSize: 20)),
          const SizedBox(height: 4),
          Text(
            'Ein Eintrag pro Tag - ein zweiter ersetzt den ersten.',
            style: LyftaTheme.subtitle.copyWith(fontSize: 13),
          ),
          const SizedBox(height: 18),
          _Field(controller: _weight, label: 'Heute', suffix: 'kg', autofocus: true),
          const SizedBox(height: 12),
          _Field(controller: _target, label: 'Ziel (optional)', suffix: 'kg'),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: saving ? null : _save,
              child: Text(saving ? 'Speichert …' : 'Speichern'),
            ),
          ),
        ],
      ),
    );
  }
}

class _Field extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final String suffix;
  final bool autofocus;

  const _Field({
    required this.controller,
    required this.label,
    required this.suffix,
    this.autofocus = false,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      autofocus: autofocus,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      style: LyftaTheme.title.copyWith(fontSize: 16),
      decoration: InputDecoration(
        labelText: label,
        suffixText: suffix,
        filled: true,
        fillColor: LyftaTheme.surfaceElevated,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}
