import 'package:flutter/material.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';

/// Die Woche auf einen Blick, darunter die Zeile "Heute".
///
/// Beantwortet die Frage, mit der man die App öffnet: *was steht heute an* - und nicht, wie
/// viel Volumen letzte Woche zusammenkam. Die Kennzahlen bleiben darunter stehen.
///
/// Ein Punkt unter einem Tag heißt:
/// * ausgefüllt in Gold - hier wurde trainiert,
/// * ausgefüllt in der Akzentfarbe - hier ist ein Training geplant,
/// * kein Punkt - Ruhetag.
///
/// "Trainiert" heißt *abgeschlossen*. Der Smart Scheduler legt Einheiten als offene
/// Sessions in die Zukunft; die zählen als geplant, sonst stünde am Montag schon die
/// ganze Woche in Gold da.
///
/// "Geplant" kommt aus zwei Quellen: einer offenen Session an dem Tag (was der Scheduler
/// tatsächlich gelegt hat) und dem Wunsch-Wochentag der Routinen (was eingestellt ist).
/// Beides zusammen, weil eine Neuplanung die Session verschieben kann, die Einstellung
/// aber stehen bleibt.
///
/// Die Heute-Zeile nennt zusätzlich die **Uhrzeit**, die der Smart Scheduler gewählt hat.
/// Der Wochentag allein beantwortet die Frage nicht, mit der man morgens in die App sieht:
/// "wann heute?" steht sonst nur im Kalender, obwohl es hier entschieden wird.
class WeekStripCard extends StatelessWidget {
  final List<GymRoutine> routines;
  final List<GymSession> sessions;
  final String? activeWorkoutName;
  final VoidCallback onStart;
  final VoidCallback onResume;

  const WeekStripCard({
    super.key,
    required this.routines,
    required this.sessions,
    required this.activeWorkoutName,
    required this.onStart,
    required this.onResume,
  });

  static const _dayLetters = ['M', 'D', 'M', 'D', 'F', 'S', 'S'];

  /// Die Routine, die an diesem ISO-Wochentag geplant ist.
  GymRoutine? _routineFor(int isoWeekday) {
    for (final r in routines) {
      if (r.preferredWeekday == isoWeekday) return r;
    }
    return null;
  }

  /// Die vom Planer gelegte Uhrzeit als "18:30", oder `null` wenn es keine gibt.
  static String? _timeOf(GymSession? session) {
    final t = session?.startTime;
    if (t == null) return null;
    return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
  }

  static bool _sameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  GymSession? _sessionOn(DateTime day, {required bool completed}) {
    for (final s in sessions) {
      final t = s.startTime;
      if (t != null && _sameDay(t, day) && s.isCompleted == completed) return s;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final monday = DateTime(now.year, now.month, now.day)
        .subtract(Duration(days: now.weekday - 1));

    final todayRoutine = _routineFor(now.weekday);
    final doneToday = _sessionOn(now, completed: true);
    final plannedToday = _sessionOn(now, completed: false);
    final active = activeWorkoutName;
    // Was heute ansteht, in der Reihenfolge, in der es die Zeile beantwortet.
    final todayName = plannedToday?.routineName ??
        plannedToday?.name ??
        todayRoutine?.name;
    // Nur für das, was noch aussteht. Nach dem Training ist die geplante Uhrzeit
    // Vergangenheit und stünde der Meldung "erledigt" nur im Weg.
    final plannedAt = doneToday == null ? _timeOf(plannedToday) : null;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(7, (i) {
              final day = monday.add(Duration(days: i));
              final isToday = _sameDay(day, now);
              final trained = _sessionOn(day, completed: true) != null;
              final planned = _sessionOn(day, completed: false) != null ||
                  _routineFor(i + 1) != null;

              return Expanded(
                child: Column(
                  children: [
                    Text(
                      _dayLetters[i],
                      style: LyftaTheme.label.copyWith(
                        color: isToday ? LyftaTheme.textPrimary : LyftaTheme.textTertiary,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Container(
                      width: 28,
                      height: 28,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: isToday ? LyftaTheme.surfaceHighlight : Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        '${day.day}',
                        style: LyftaTheme.caption.copyWith(
                          color: isToday ? LyftaTheme.textPrimary : LyftaTheme.textSecondary,
                          fontWeight: isToday ? FontWeight.w600 : FontWeight.w400,
                        ),
                      ),
                    ),
                    const SizedBox(height: 5),
                    // Gold für erledigt, Akzent für geplant - dieselbe Bedeutung, die
                    // prAccent im Rest des Space schon trägt: etwas ist geschafft.
                    // Erledigt gewinnt, wenn an einem Tag beides steht.
                    Container(
                      width: 5,
                      height: 5,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: trained
                            ? LyftaTheme.prAccent
                            : planned
                                ? LyftaTheme.primary
                                : Colors.transparent,
                      ),
                    ),
                  ],
                ),
              );
            }),
          ),
          const SizedBox(height: 14),
          const Divider(height: 1, color: LyftaTheme.divider),
          const SizedBox(height: 12),
          // Läuft gerade etwas, gewinnt das. Sonst: ist heute schon trainiert, fragt die
          // Zeile nicht noch einmal danach - der Tag ist erledigt.
          _TodayRow(
            icon: active != null
                ? Icons.timer_rounded
                : doneToday != null
                    ? Icons.check_circle_rounded
                    : todayName != null
                        ? Icons.fitness_center_rounded
                        : Icons.bedtime_rounded,
            iconColor: active != null
                ? LyftaTheme.primary
                : doneToday != null
                    ? LyftaTheme.prAccent
                    : LyftaTheme.textSecondary,
            title: active ??
                (doneToday != null
                    ? 'Training erledigt'
                    : todayName ?? 'Ruhetag'),
            time: plannedAt,
            tag: active != null
                ? 'Fortsetzen'
                : doneToday != null
                    ? 'Erledigt'
                    : todayName != null
                        ? 'Starten'
                        : null,
            onTap: active != null ? onResume : onStart,
          ),
        ],
      ),
    );
  }
}

class _TodayRow extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;

  /// Vom Planer gelegte Uhrzeit, z.B. "18:30".
  final String? time;
  final String? tag;
  final VoidCallback onTap;

  const _TodayRow({
    required this.icon,
    required this.iconColor,
    required this.title,
    this.time,
    required this.tag,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: LyftaTheme.surfaceElevated,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Icon(icon, size: 18, color: iconColor),
            ),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    time == null ? 'HEUTE' : 'HEUTE · $time',
                    style: LyftaTheme.label,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    title,
                    style: LyftaTheme.title.copyWith(fontSize: 15),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            if (tag != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: LyftaTheme.surfaceElevated,
                  borderRadius: BorderRadius.circular(7),
                ),
                child: Text(
                  tag!,
                  style: LyftaTheme.caption.copyWith(color: LyftaTheme.textPrimary),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
