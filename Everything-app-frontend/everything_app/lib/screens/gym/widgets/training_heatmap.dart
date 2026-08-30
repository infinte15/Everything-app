import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';

/// Trainingstage der letzten Wochen als Raster - eine Spalte je Woche, eine Zeile je Tag.
///
/// Beantwortet die Frage, die keine Wochensumme beantwortet: *ist das regelmäßig*. Zwölf
/// Wochen, weil danach die Spalten schmaler wären als der Abstand dazwischen.
///
/// Die Helligkeit steht für das Volumen des Tages, nicht für die Anzahl der Einheiten:
/// zwei kurze Einheiten sind kein härterer Tag als eine lange.
class TrainingHeatmap extends StatelessWidget {
  final List<GymSession> sessions;
  final int weeks;

  const TrainingHeatmap({super.key, required this.sessions, this.weeks = 12});

  static const _dayLetters = ['M', 'D', 'M', 'D', 'F', 'S', 'S'];

  @override
  Widget build(BuildContext context) {
    final today = DateTime.now();
    final thisMonday = DateTime(today.year, today.month, today.day)
        .subtract(Duration(days: today.weekday - 1));
    final firstMonday = thisMonday.subtract(Duration(days: 7 * (weeks - 1)));

    // Volumen je Tag, nur abgeschlossene Einheiten - geplante Zukunft ist kein Training.
    final byDay = <DateTime, double>{};
    for (final s in sessions) {
      final t = s.startTime;
      if (t == null || !s.isCompleted) continue;
      final day = DateTime(t.year, t.month, t.day);
      if (day.isBefore(firstMonday)) continue;
      byDay.update(day, (v) => v + s.totalVolumeKg, ifAbsent: () => s.totalVolumeKg);
    }
    final peak = byDay.values.fold<double>(0, (m, v) => v > m ? v : m);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          reverse: true,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                children: [
                  for (final letter in _dayLetters)
                    SizedBox(
                      height: 18,
                      width: 16,
                      child: Center(
                        child: Text(letter,
                            style: LyftaTheme.label.copyWith(fontSize: 9)),
                      ),
                    ),
                ],
              ),
              for (var week = 0; week < weeks; week++)
                Column(
                  children: [
                    for (var day = 0; day < 7; day++)
                      _cell(firstMonday.add(Duration(days: week * 7 + day)), byDay, peak,
                          today),
                  ],
                ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Text('weniger', style: LyftaTheme.label.copyWith(fontSize: 9)),
            const SizedBox(width: 6),
            for (final step in [0.0, 0.25, 0.5, 0.75, 1.0])
              Padding(
                padding: const EdgeInsets.only(right: 3),
                child: Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: _shade(step),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
            const SizedBox(width: 3),
            Text('mehr', style: LyftaTheme.label.copyWith(fontSize: 9)),
          ],
        ),
      ],
    );
  }

  Widget _cell(DateTime day, Map<DateTime, double> byDay, double peak, DateTime today) {
    final volume = byDay[day];
    final future = day.isAfter(DateTime(today.year, today.month, today.day));
    // Ein Tag ganz ohne Last (reines Körpergewicht) ist trotzdem ein Trainingstag und
    // bekommt die unterste Stufe, nicht die Farbe eines Ruhetags.
    final intensity = volume == null
        ? -1.0
        : (peak > 0 ? (volume / peak).clamp(0.15, 1.0) : 0.5);

    return Padding(
      padding: const EdgeInsets.all(1.5),
      child: Tooltip(
        message: volume == null
            ? '${day.day}.${day.month}. — kein Training'
            : '${day.day}.${day.month}. — ${volume.round()} kg',
        child: Container(
          width: 15,
          height: 15,
          decoration: BoxDecoration(
            color: future
                ? Colors.transparent
                : (intensity < 0 ? LyftaTheme.surfaceElevated : _shade(intensity)),
            border: future
                ? Border.all(color: LyftaTheme.divider, width: 0.8)
                : null,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
      ),
    );
  }

  /// Eine Akzent-Stufe. 0 ist die Grundfläche, alles darüber wird zunehmend deutlicher -
  /// die Farbe trägt hier Bedeutung und darf deshalb nach der Gestaltungslinie Akzent sein.
  static Color _shade(double intensity) => intensity <= 0
      ? LyftaTheme.surfaceElevated
      : Color.lerp(LyftaTheme.surfaceElevated, LyftaTheme.primary, intensity)!;
}
