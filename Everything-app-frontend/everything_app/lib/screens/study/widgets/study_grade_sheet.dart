import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../models/study_grade.dart';
import '../../../models/study_subject.dart';
import '../../../providers/study_provider.dart';

const _examTypes = [
  'Klausur',
  'Hausarbeit',
  'Projekt',
  'Übung',
  'Portfolio',
  'Mündliche Prüfung',
  'Sonstiges',
];

const _weightPresets = [10, 25, 33, 50, 66, 75, 100];

class StudyGradeSheet extends StatefulWidget {
  final StudySubject? subject;
  final StudyGrade? existing;

  const StudyGradeSheet({super.key, this.subject, this.existing});

  static Future<void> show(
    BuildContext context, {
    StudySubject? subject,
    StudyGrade? existing,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF1A1A1A),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
        child: StudyGradeSheet(subject: subject, existing: existing),
      ),
    );
  }

  @override
  State<StudyGradeSheet> createState() => _StudyGradeSheetState();
}

class _StudyGradeSheetState extends State<StudyGradeSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _gradeCtrl;
  late final TextEditingController _weightCtrl;
  late final TextEditingController _notesCtrl;
  late String _examType;
  String? _subjectId;
  DateTime _date = DateTime.now();
  bool _countsTowardGrade = true;

  bool get _isEdit => widget.existing != null;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _nameCtrl = TextEditingController(text: e?.examName ?? '');
    _gradeCtrl = TextEditingController(
      text: e != null ? e.grade.toStringAsFixed(1).replaceAll('.', ',') : '',
    );
    _weightCtrl = TextEditingController(
      text: '${e?.weightPercent ?? 100}',
    );
    _notesCtrl = TextEditingController(text: e?.notes ?? '');
    _examType = e?.examType ?? _examTypes.first;
    _subjectId = widget.subject?.id ?? e?.subjectId;
    _date = e?.date ?? DateTime.now();
    _countsTowardGrade = e?.countsTowardGrade ?? true;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _gradeCtrl.dispose();
    _weightCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  double? _parseGrade() {
    final raw = _gradeCtrl.text.trim().replaceAll(',', '.');
    if (raw.isEmpty) return null;
    final v = double.tryParse(raw);
    if (v == null || v < 1.0 || v > 5.0) return null;
    return v;
  }

  int _parseWeight() {
    final v = int.tryParse(_weightCtrl.text.trim());
    if (v == null) return 100;
    return v.clamp(1, 100);
  }

  void _save() {
    final provider = context.read<StudyProvider>();
    final grade = _parseGrade();
    final subjectId = _subjectId ??
        (provider.subjects.isNotEmpty ? provider.subjects.first.id : null);
    if (grade == null || subjectId == null) return;

    final name = _nameCtrl.text.trim();
    if (name.isEmpty) return;

    final entry = StudyGrade(
      id: widget.existing?.id ?? 'g${DateTime.now().millisecondsSinceEpoch}',
      subjectId: subjectId,
      examName: name,
      examType: _examType,
      grade: grade,
      weightPercent: _parseWeight(),
      countsTowardGrade: _countsTowardGrade,
      date: _date,
      notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
    );

    if (_isEdit) {
      provider.updateGrade(entry);
    } else {
      provider.addGrade(entry);
    }
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final subjects = provider.subjects;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              _isEdit ? 'LEISTUNG BEARBEITEN' : 'LEISTUNG HINZUFÜGEN',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 0.5,
              ),
            ),
            const SizedBox(height: 20),
            if (widget.subject != null)
              _FieldLabel(
                label: 'Modul',
                child: Text(
                  widget.subject!.name,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              )
            else if (subjects.isNotEmpty)
              _FieldLabel(
                label: 'Modul',
                child: DropdownButtonFormField<String>(
                  initialValue: _subjectId,
                  decoration: _inputDecoration('Modul wählen'),
                  items: subjects
                      .map(
                        (s) => DropdownMenuItem(
                          value: s.id,
                          child: Text(s.name),
                        ),
                      )
                      .toList(),
                  onChanged: (v) => setState(() => _subjectId = v),
                ),
              ),
            const SizedBox(height: 12),
            _FieldLabel(
              label: 'Art',
              child: DropdownButtonFormField<String>(
                initialValue: _examType,
                decoration: _inputDecoration(null),
                items: _examTypes
                    .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                    .toList(),
                onChanged: (v) {
                  if (v != null) setState(() => _examType = v);
                },
              ),
            ),
            const SizedBox(height: 12),
            _FieldLabel(
              label: 'Bezeichnung',
              child: TextField(
                controller: _nameCtrl,
                decoration: _inputDecoration('z. B. Klausur 1'),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _FieldLabel(
                    label: 'Note (1,0 – 5,0)',
                    child: TextField(
                      controller: _gradeCtrl,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(
                          RegExp(r'[0-9.,]'),
                        ),
                      ],
                      decoration: _inputDecoration('z. B. 1,7'),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _FieldLabel(
                    label: 'Gewichtung %',
                    child: TextField(
                      controller: _weightCtrl,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: _inputDecoration('100'),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: _weightPresets.map((p) {
                final selected = _parseWeight() == p;
                return ChoiceChip(
                  label: Text('$p %'),
                  selected: selected,
                  onSelected: (_) {
                    setState(() => _weightCtrl.text = '$p');
                  },
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.zero,
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 4),
            // Ein Schein wird abgelegt und bestanden, verschiebt den Modulschnitt aber nicht.
            // Deshalb ein Schalter statt einer Note ohne Wert: die grade-Spalte bleibt
            // NOT NULL, und die Leistung soll trotzdem in der Liste stehen.
            SwitchListTile(
              value: _countsTowardGrade,
              onChanged: (v) => setState(() => _countsTowardGrade = v),
              contentPadding: EdgeInsets.zero,
              dense: true,
              activeThumbColor: theme.colorScheme.primary,
              title: Text(
                'Zählt in den Modulschnitt',
                style: theme.textTheme.bodyMedium,
              ),
              subtitle: Text(
                _countsTowardGrade
                    ? 'Die Note geht gewichtet in den Schnitt ein.'
                    : 'Schein — wird angezeigt, verschiebt den Schnitt aber nicht.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            const SizedBox(height: 8),
            _FieldLabel(
              label: 'Datum',
              child: InkWell(
                onTap: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: _date,
                    firstDate: DateTime(2015),
                    lastDate: DateTime.now().add(const Duration(days: 365)),
                  );
                  if (picked != null) setState(() => _date = picked);
                },
                child: InputDecorator(
                  decoration: _inputDecoration(null),
                  child: Text(
                    '${_date.day}.${_date.month}.${_date.year}',
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            _FieldLabel(
              label: 'Notizen (optional)',
              child: TextField(
                controller: _notesCtrl,
                maxLines: 2,
                decoration: _inputDecoration(null),
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                if (_isEdit)
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () {
                        context.read<StudyProvider>().deleteGrade(
                              widget.existing!.id,
                            );
                        Navigator.pop(context);
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: theme.colorScheme.error,
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.zero,
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: const Text('LÖSCHEN'),
                    ),
                  ),
                if (_isEdit) const SizedBox(width: 12),
                Expanded(
                  flex: _isEdit ? 2 : 1,
                  child: FilledButton(
                    onPressed: _parseGrade() != null ? _save : null,
                    style: FilledButton.styleFrom(
                      shape: const RoundedRectangleBorder(
                        borderRadius: BorderRadius.zero,
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(_isEdit ? 'SPEICHERN' : 'HINZUFÜGEN'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  InputDecoration _inputDecoration(String? hint) => InputDecoration(
        hintText: hint,
        border: const OutlineInputBorder(borderRadius: BorderRadius.zero),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.zero,
          borderSide: BorderSide(
            color: Theme.of(context).colorScheme.outlineVariant.withValues(
                  alpha: 0.3,
                ),
          ),
        ),
      );
}

class _FieldLabel extends StatelessWidget {
  final String label;
  final Widget child;

  const _FieldLabel({required this.label, required this.child});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label.toUpperCase(),
          style: theme.textTheme.labelSmall?.copyWith(
            fontWeight: FontWeight.bold,
            color: theme.colorScheme.primary,
            letterSpacing: 1.2,
          ),
        ),
        const SizedBox(height: 6),
        child,
      ],
    );
  }
}
