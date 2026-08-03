import 'package:flutter/material.dart';

import '../../../theme/lyfta_theme.dart';

/// Voreingestellte Pausenzeiten - deckt Aufwärm- bis Kraft-Sätze ab.
const List<int> _presets = [30, 45, 60, 90, 120, 180, 240];

/// Öffnet eine Auswahl für die Pausenzeit einer Übung. Gibt die gewählte
/// Sekundenzahl zurück, oder `null` bei Abbruch.
Future<int?> pickRestSeconds(BuildContext context, {required int initial}) {
  return showModalBottomSheet<int>(
    context: context,
    backgroundColor: LyftaTheme.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (_) => _RestSecondsSheet(initial: initial),
  );
}

class _RestSecondsSheet extends StatefulWidget {
  final int initial;

  const _RestSecondsSheet({required this.initial});

  @override
  State<_RestSecondsSheet> createState() => _RestSecondsSheetState();
}

class _RestSecondsSheetState extends State<_RestSecondsSheet> {
  late int _seconds = widget.initial;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Pause anpassen', style: LyftaTheme.headline.copyWith(fontSize: 20)),
            const SizedBox(height: 18),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _stepButton(Icons.remove, () => _adjust(-15)),
                SizedBox(
                  width: 90,
                  child: Text(
                    '${_seconds}s',
                    textAlign: TextAlign.center,
                    style: LyftaTheme.headline.copyWith(fontSize: 30),
                  ),
                ),
                _stepButton(Icons.add, () => _adjust(15)),
              ],
            ),
            const SizedBox(height: 18),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: _presets.map((s) => _presetChip(s)).toList(),
            ),
            const SizedBox(height: 22),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => Navigator.pop(context, _seconds),
                child: const Text('Übernehmen'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _adjust(int delta) {
    setState(() => _seconds = (_seconds + delta).clamp(0, 900));
  }

  Widget _stepButton(IconData icon, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        width: 40,
        height: 40,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: LyftaTheme.surfaceElevated,
          shape: BoxShape.circle,
        ),
        child: Icon(icon, size: 18, color: LyftaTheme.textPrimary),
      ),
    );
  }

  Widget _presetChip(int seconds) {
    final selected = seconds == _seconds;
    return GestureDetector(
      onTap: () => setState(() => _seconds = seconds),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? LyftaTheme.surfaceHighlight : LyftaTheme.surfaceElevated,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected ? LyftaTheme.primary : LyftaTheme.divider,
          ),
        ),
        child: Text(
          '${seconds}s',
          style: LyftaTheme.caption.copyWith(
            color: selected ? LyftaTheme.primary : LyftaTheme.textSecondary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}
