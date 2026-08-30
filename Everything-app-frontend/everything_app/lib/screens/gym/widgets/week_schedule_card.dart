import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';

/// Wochenplan: welcher Tag welche Routine trägt.
///
/// Eine Zuordnung ist ein Wunsch, keine Festlegung. Der Smart Scheduler sucht die Uhrzeit
/// weiterhin selbst und arbeitet um feste Termine herum - er legt die Einheit nur auf diesen
/// Tag statt sie frei über die Woche zu verteilen.
///
/// Genau deshalb steht neben der Routine die Uhrzeit, die dabei herausgekommen ist: die Karte
/// zeigt sonst nur die eine Hälfte der Abmachung. Steht dort noch keine, hat der Planer die
/// Einheit für diese Woche noch nicht gelegt.
class WeekScheduleCard extends StatelessWidget {
  const WeekScheduleCard({super.key});

  static const _dayNames = [
    'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag',
  ];

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final routines = sports.routines;
    final plannedTimes = _plannedTimesThisWeek(sports.sessions);

    return Container(
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (var weekday = 1; weekday <= 7; weekday++) ...[
            if (weekday > 1) const Divider(height: 1, color: LyftaTheme.divider),
            _DayRow(
              label: _dayNames[weekday - 1],
              routine: _routineFor(routines, weekday),
              time: plannedTimes[weekday],
              onTap: () => _assign(context, weekday, routines),
            ),
          ],
        ],
      ),
    );
  }

  /// Wochentag -> Uhrzeit der offenen Einheit, die der Planer in diese Woche gelegt hat.
  ///
  /// Nur die laufende Woche: die Karte zeigt einen Wochenplan, und die Einheit der übernächsten
  /// Woche zu nennen hieße, eine Uhrzeit zu versprechen, die bis dahin ohnehin neu berechnet wird.
  static Map<int, String> _plannedTimesThisWeek(List<GymSession> sessions) {
    final now = DateTime.now();
    final monday = DateTime(now.year, now.month, now.day)
        .subtract(Duration(days: now.weekday - 1));
    final nextMonday = monday.add(const Duration(days: 7));

    final out = <int, String>{};
    for (final s in sessions) {
      final t = s.startTime;
      if (t == null || s.isCompleted) continue;
      if (t.isBefore(monday) || !t.isBefore(nextMonday)) continue;
      out[t.weekday] = '${t.hour.toString().padLeft(2, '0')}:'
          '${t.minute.toString().padLeft(2, '0')}';
    }
    return out;
  }

  static GymRoutine? _routineFor(List<GymRoutine> routines, int weekday) {
    for (final r in routines) {
      if (r.preferredWeekday == weekday) return r;
    }
    return null;
  }

  /// Blatt zur Auswahl, welche Routine an diesem Tag liegt.
  ///
  /// Eine Routine kann nur an einem Tag liegen: sie einem zweiten zuzuweisen nimmt sie vom
  /// ersten. Alles andere hieße, dieselbe Routine zweimal pro Woche zu planen - das ist der
  /// Job mehrerer Routinen, nicht einer doppelt eingetragenen.
  Future<void> _assign(BuildContext context, int weekday, List<GymRoutine> routines) async {
    final current = _routineFor(routines, weekday);

    final picked = await showModalBottomSheet<Object?>(
      context: context,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 10),
              child: Text(_dayNames[weekday - 1], style: LyftaTheme.headline.copyWith(fontSize: 20)),
            ),
            ListTile(
              leading: const Icon(Icons.bedtime_rounded, color: LyftaTheme.textSecondary),
              title: Text('Ruhetag', style: LyftaTheme.title.copyWith(fontSize: 15)),
              trailing: current == null
                  ? const Icon(Icons.check_rounded, color: LyftaTheme.primary)
                  : null,
              // Ein Sentinel statt null: null ist schon das Ergebnis von "Blatt weggewischt",
              // und "Ruhetag gewählt" muss davon zu unterscheiden sein.
              onTap: () => Navigator.of(sheetContext).pop(#rest),
            ),
            for (final r in routines)
              ListTile(
                leading: const Icon(Icons.fitness_center_rounded, color: LyftaTheme.textSecondary),
                title: Text(r.name, style: LyftaTheme.title.copyWith(fontSize: 15)),
                subtitle: Text('${r.exerciseCount} Übungen', style: LyftaTheme.caption),
                trailing: current?.id == r.id
                    ? const Icon(Icons.check_rounded, color: LyftaTheme.primary)
                    : null,
                onTap: () => Navigator.of(sheetContext).pop(r),
              ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );

    if (picked == null || !context.mounted) return;
    final sports = context.read<SportsProvider>();

    if (picked == #rest) {
      if (current != null) await sports.setRoutineWeekday(current.id, null);
      return;
    }
    final routine = picked as GymRoutine;
    if (routine.preferredWeekday == weekday) return;
    await sports.setRoutineWeekday(routine.id, weekday);
  }
}

class _DayRow extends StatelessWidget {
  final String label;
  final GymRoutine? routine;

  /// Uhrzeit der geplanten Einheit, sofern der Planer sie schon gelegt hat.
  final String? time;
  final VoidCallback onTap;

  const _DayRow({
    required this.label,
    required this.routine,
    required this.time,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final r = routine;

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        child: Row(
          children: [
            // 108 statt 96: "Donnerstag" ist das laengste Wort und lief sonst in die
            // Routinen-Spalte hinein.
            SizedBox(
              width: 108,
              child: Text(label, style: LyftaTheme.title.copyWith(fontSize: 14)),
            ),
            Expanded(
              child: Text(
                r?.name ?? 'Ruhetag',
                style: r == null
                    ? LyftaTheme.subtitle.copyWith(fontSize: 14)
                    : LyftaTheme.subtitle
                        .copyWith(fontSize: 14, color: LyftaTheme.primary),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (r != null && time != null) ...[
              Text(time!, style: LyftaTheme.caption),
              const SizedBox(width: 8),
            ],
            const Icon(Icons.chevron_right_rounded, color: LyftaTheme.textTertiary, size: 20),
          ],
        ),
      ),
    );
  }
}
