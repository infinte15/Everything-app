import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../models/gym/gym_models.dart';
import '../../../theme/lyfta_theme.dart';

/// Eine abgeschlossene Trainingseinheit in der Verlaufsliste.
class GymSessionCard extends StatelessWidget {
  final GymSession session;
  final VoidCallback? onTap;

  const GymSessionCard({super.key, required this.session, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        color: LyftaTheme.surfaceElevated,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.fitness_center_rounded,
                        size: 20,
                        color: LyftaTheme.primary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            session.name,
                            style: LyftaTheme.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          Text(_formatDate(session.startTime), style: LyftaTheme.caption),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Divider(height: 1, color: LyftaTheme.divider),
                const SizedBox(height: 12),
                Row(
                  children: [
                    _metric('Volumen', '${session.totalVolumeKg.round()} kg'),
                    _metric('Dauer', '${session.durationMinutes} min'),
                    _metric('Sätze', '${session.totalSets}'),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _metric(String label, String value) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(), style: LyftaTheme.label),
          const SizedBox(height: 3),
          Text(value, style: LyftaTheme.title.copyWith(fontSize: 15)),
        ],
      ),
    );
  }

  /// "Heute", "Gestern" oder Datum. Das Backend liefert einen ISO-String, den das
  /// Modell bereits in ein DateTime übersetzt hat.
  String _formatDate(DateTime? date) {
    if (date == null) return 'Ohne Datum';
    final today = DateTime.now();
    final day = DateTime(date.year, date.month, date.day);
    final diff = DateTime(today.year, today.month, today.day).difference(day).inDays;

    if (diff == 0) return 'Heute, ${DateFormat('HH:mm').format(date)}';
    if (diff == 1) return 'Gestern, ${DateFormat('HH:mm').format(date)}';
    if (diff < 7 && diff > 0) return 'vor $diff Tagen';
    return DateFormat('d. MMMM', 'de_DE').format(date);
  }
}
