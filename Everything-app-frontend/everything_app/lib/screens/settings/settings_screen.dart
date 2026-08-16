import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../config/app_theme.dart';
import '../../models/user_preferences.dart';
import '../../providers/auth_provider.dart';
import '../../providers/calendar_provider.dart';
import '../../providers/preferences_provider.dart';

/// Einstellungen, inklusive der Stellschrauben des Smart Schedulers.
/// Bis hierher waren Arbeitszeiten fest auf 08:00–22:00 verdrahtet und über die API
/// überhaupt nicht erreichbar.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  UserPreferences? _draft;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final provider = context.read<PreferencesProvider>();
      await provider.load();
      if (mounted) setState(() => _draft = provider.preferences);
    });
  }

  void _patch(UserPreferences Function(UserPreferences) change) {
    final current = _draft;
    if (current == null) return;
    setState(() => _draft = change(current));
  }

  Future<void> _pickTime(BuildContext context, bool isStart) async {
    final current = _draft;
    if (current == null) return;
    final initial = (isStart ? current.workdayStart : current.workdayEnd) ??
        TimeOfDay(hour: isStart ? 8 : 22, minute: 0);

    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked == null) return;
    _patch((p) => isStart ? p.copyWith(workdayStart: picked) : p.copyWith(workdayEnd: picked));
  }

  /// Gegenstueck zu [_pickTime] fuer die Privatzeiten.
  ///
  /// Bewusst eine eigene Methode statt eines dritten Parameters an [_pickTime]: die beiden
  /// Zeitpaare haben unterschiedliche Rueckfallwerte, und ein `bool isPersonal` neben dem
  /// bestehenden `bool isStart` waere an der Aufrufstelle nicht mehr zu lesen.
  Future<void> _pickPersonalTime(BuildContext context, bool isStart) async {
    final current = _draft;
    if (current == null) return;
    final initial = (isStart ? current.personalHoursStart : current.personalHoursEnd) ??
        TimeOfDay(hour: isStart ? 6 : 23, minute: 0);

    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked == null) return;
    _patch((p) => isStart
        ? p.copyWith(personalHoursStart: picked)
        : p.copyWith(personalHoursEnd: picked));
  }

  Future<void> _save() async {
    final draft = _draft;
    if (draft == null) return;

    final prefsProvider = context.read<PreferencesProvider>();
    final calendar = context.read<CalendarProvider>();
    final messenger = ScaffoldMessenger.of(context);

    final ok = await prefsProvider.save(draft);
    if (!mounted) return;

    if (ok) {
      // Geänderte Arbeitszeiten lösen serverseitig eine Neuplanung aus — kurz danach nachladen.
      calendar.scheduleReconcile();
      messenger.showSnackBar(const SnackBar(content: Text('Settings saved')));
    } else {
      messenger.showSnackBar(
        SnackBar(content: Text(prefsProvider.error ?? 'Fehler beim Speichern der Einstellungen')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<PreferencesProvider>();
    final draft = _draft;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 20, 20, 4),
              child: Text(
                'Settings',
                style: TextStyle(
                  fontFamily: 'Manrope',
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            if (provider.isLoading || draft == null)
              const Expanded(child: Center(child: CircularProgressIndicator()))
            else
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
                  children: [
                    _SectionTitle('Working hours'),
                    _SettingRow(
                      label: 'Day starts',
                      value: _formatTime(draft.workdayStart) ?? '08:00',
                      onTap: () => _pickTime(context, true),
                    ),
                    _SettingRow(
                      label: 'Day ends',
                      value: _formatTime(draft.workdayEnd) ?? '22:00',
                      onTap: () => _pickTime(context, false),
                    ),

                    const SizedBox(height: 24),
                    // Getrennt von den Arbeitszeiten: hier liegen Gewohnheiten und Trainings.
                    // Ohne diesen Rahmen waren beide auf den Arbeitstag geklemmt — eine
                    // Abend-Gewohnheit konnte gar nicht am Abend liegen.
                    _SectionTitle('Personal hours'),
                    _SettingRow(
                      label: 'Habits & workouts from',
                      value: _formatTime(draft.personalHoursStart) ?? '06:00',
                      onTap: () => _pickPersonalTime(context, true),
                    ),
                    _SettingRow(
                      label: 'Habits & workouts until',
                      value: _formatTime(draft.personalHoursEnd) ?? '23:00',
                      onTap: () => _pickPersonalTime(context, false),
                    ),

                    const SizedBox(height: 24),
                    _SectionTitle('Scheduling'),
                    _Stepper(
                      label: 'Buffer around meetings',
                      suffix: 'min',
                      value: draft.bufferMinutes ?? 0,
                      min: 0,
                      max: 60,
                      step: 5,
                      onChanged: (v) => _patch((p) => p.copyWith(bufferMinutes: v)),
                    ),
                    _Stepper(
                      label: 'Break between blocks',
                      suffix: 'min',
                      value: draft.breakDurationMinutes ?? 0,
                      min: 0,
                      max: 60,
                      step: 5,
                      onChanged: (v) => _patch((p) => p.copyWith(breakDurationMinutes: v)),
                    ),
                    _Stepper(
                      label: 'Max task time per day',
                      suffix: 'min',
                      value: draft.maxTaskMinutesPerDay ?? 480,
                      min: 60,
                      max: 1440,
                      step: 30,
                      onChanged: (v) => _patch((p) => p.copyWith(maxTaskMinutesPerDay: v)),
                    ),
                    // Deckelt ALLES, was pro Tag automatisch geplant wird. Ohne diese Grenze
                    // konnten Gewohnheiten, Trainings und Projektzeit einen Tag füllen, bevor die
                    // Aufgaben überhaupt an die Reihe kamen — "Max task time per day" gilt nur
                    // für die Aufgaben selbst.
                    _Stepper(
                      label: 'Max scheduled time per day',
                      suffix: 'min',
                      value: draft.maxScheduledMinutesPerDay ?? 600,
                      min: 60,
                      max: 1440,
                      step: 30,
                      onChanged: (v) => _patch((p) => p.copyWith(maxScheduledMinutesPerDay: v)),
                    ),
                    _Stepper(
                      label: 'Shortest task block',
                      suffix: 'min',
                      value: draft.defaultMinChunkMinutes ?? 30,
                      min: 5,
                      max: 480,
                      step: 5,
                      onChanged: (v) => _patch((p) => p.copyWith(defaultMinChunkMinutes: v)),
                    ),
                    _Stepper(
                      label: 'Longest task block',
                      suffix: 'min',
                      value: draft.defaultMaxChunkMinutes ?? 120,
                      min: 5,
                      max: 480,
                      step: 15,
                      onChanged: (v) => _patch((p) => p.copyWith(defaultMaxChunkMinutes: v)),
                    ),
                    _SwitchRow(
                      label: 'Auto-schedule',
                      subtitle: 'Let the AI place tasks, habits and workouts for you',
                      value: draft.autoScheduleEnabled ?? true,
                      onChanged: (v) => _patch((p) => p.copyWith(autoScheduleEnabled: v)),
                    ),

                    const SizedBox(height: 24),
                    _SectionTitle('Focus'),
                    _ChoiceRow(
                      label: 'Peak productivity',
                      options: const ['MORNING', 'AFTERNOON', 'EVENING'],
                      labels: const ['Morning', 'Afternoon', 'Evening'],
                      selected: draft.peakProductivityTime ?? 'MORNING',
                      onChanged: (v) => _patch((p) => p.copyWith(peakProductivityTime: v)),
                    ),

                    const SizedBox(height: 24),
                    _SectionTitle('Notifications'),
                    _SwitchRow(
                      label: 'Notifications',
                      value: draft.notificationsEnabled ?? true,
                      onChanged: (v) => _patch((p) => p.copyWith(notificationsEnabled: v)),
                    ),
                    _Stepper(
                      label: 'Remind me before',
                      suffix: 'min',
                      value: draft.reminderMinutesBefore ?? 15,
                      min: 0,
                      max: 120,
                      step: 5,
                      onChanged: (v) => _patch((p) => p.copyWith(reminderMinutesBefore: v)),
                    ),

                    const SizedBox(height: 28),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        style: FilledButton.styleFrom(
                          backgroundColor: AppTheme.primaryColor,
                          foregroundColor: Colors.black,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                        ),
                        onPressed: provider.isSaving ? null : _save,
                        child: Text(provider.isSaving ? 'Saving…' : 'Save'),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.logout_rounded, size: 16),
                        label: const Text('Log out'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppTheme.errorColor,
                          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                          padding: const EdgeInsets.symmetric(vertical: 16),
                        ),
                        onPressed: () => context.read<AuthProvider>().logout(),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  static String? _formatTime(TimeOfDay? t) {
    if (t == null) return null;
    return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
  }
}

// ─── Bausteine ────────────────────────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Text(
        text.toUpperCase(),
        style: const TextStyle(
          fontFamily: 'Manrope',
          fontSize: 11,
          fontWeight: FontWeight.w800,
          letterSpacing: 1.4,
          color: AppTheme.onSurfaceVariant,
        ),
      ),
    );
  }
}

class _SettingRow extends StatelessWidget {
  final String label;
  final String value;
  final VoidCallback onTap;

  const _SettingRow({required this.label, required this.value, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 14),
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
          borderRadius: BorderRadius.zero,
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(label,
                  style: const TextStyle(fontFamily: 'Manrope', fontWeight: FontWeight.w600)),
            ),
            Text(value,
                style: const TextStyle(
                  fontFamily: 'Manrope',
                  fontWeight: FontWeight.w800,
                  color: AppTheme.primaryColor,
                )),
            const SizedBox(width: 6),
            const Icon(Icons.chevron_right_rounded, size: 18, color: AppTheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}

/// +/- Stepper im Stil von create_habit_sheet.dart.
class _Stepper extends StatelessWidget {
  final String label;
  final String suffix;
  final int value;
  final int min;
  final int max;
  final int step;
  final ValueChanged<int> onChanged;

  const _Stepper({
    required this.label,
    required this.suffix,
    required this.value,
    required this.min,
    required this.max,
    required this.step,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 14),
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
        borderRadius: BorderRadius.zero,
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(label,
                style: const TextStyle(fontFamily: 'Manrope', fontWeight: FontWeight.w600)),
          ),
          IconButton(
            icon: const Icon(Icons.remove_rounded, size: 18),
            onPressed: value - step >= min ? () => onChanged(value - step) : null,
          ),
          SizedBox(
            width: 64,
            child: Text(
              '$value $suffix',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Manrope',
                fontWeight: FontWeight.w800,
                color: AppTheme.primaryColor,
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.add_rounded, size: 18),
            onPressed: value + step <= max ? () => onChanged(value + step) : null,
          ),
        ],
      ),
    );
  }
}

class _SwitchRow extends StatelessWidget {
  final String label;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _SwitchRow({
    required this.label,
    this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 14),
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
        borderRadius: BorderRadius.zero,
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: const TextStyle(fontFamily: 'Manrope', fontWeight: FontWeight.w600)),
                if (subtitle != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(subtitle!,
                        style: const TextStyle(
                            fontFamily: 'Manrope',
                            fontSize: 11,
                            color: AppTheme.onSurfaceVariant)),
                  ),
              ],
            ),
          ),
          Switch(value: value, activeThumbColor: AppTheme.primaryColor, onChanged: onChanged),
        ],
      ),
    );
  }
}

class _ChoiceRow extends StatelessWidget {
  final String label;
  final List<String> options;
  final List<String> labels;
  final String selected;
  final ValueChanged<String> onChanged;

  const _ChoiceRow({
    required this.label,
    required this.options,
    required this.labels,
    required this.selected,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 2, bottom: 6),
          child: Text(label,
              style: const TextStyle(fontFamily: 'Manrope', fontWeight: FontWeight.w600)),
        ),
        Row(
          children: [
            for (var i = 0; i < options.length; i++) ...[
              Expanded(
                child: GestureDetector(
                  onTap: () => onChanged(options[i]),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    decoration: BoxDecoration(
                      color: selected == options[i]
                          ? AppTheme.primaryColor.withValues(alpha: 0.18)
                          : Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest
                              .withValues(alpha: 0.35),
                      border: Border.all(
                        color: selected == options[i]
                            ? AppTheme.primaryColor
                            : Colors.transparent,
                      ),
                      borderRadius: BorderRadius.zero,
                    ),
                    child: Text(
                      labels[i],
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontFamily: 'Manrope',
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: selected == options[i]
                            ? AppTheme.primaryColor
                            : AppTheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
              ),
              if (i < options.length - 1) const SizedBox(width: 8),
            ],
          ],
        ),
      ],
    );
  }
}
