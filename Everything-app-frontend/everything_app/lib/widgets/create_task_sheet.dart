import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/task_provider.dart';
import '../models/task.dart';
import '../config/app_theme.dart';

class CreateTaskSheet extends StatefulWidget {
  final String? spaceType;

  /// Gesetzt heisst BEARBEITEN statt anlegen.
  ///
  /// Bewusst dasselbe Widget und kein eigenes `edit_task_sheet.dart` — dasselbe Muster wie bei
  /// `CreateEventSheet.existingEvent`. Ein zweites Sheet waere eine Kopie desselben Formulars,
  /// und die beiden liefen bei der ersten Aenderung auseinander.
  final Task? existingTask;

  const CreateTaskSheet({super.key, this.spaceType, this.existingTask});

  @override
  State<CreateTaskSheet> createState() => _CreateTaskSheetState();
}

class _CreateTaskSheetState extends State<CreateTaskSheet> {
  final _titleController = TextEditingController();
  final _durationController = TextEditingController(text: '60');
  final _notesController = TextEditingController();

  int _durationMinutes = 60;
  int _priority = 3;
  bool _splitUp = true;
  String _category = 'Personal';
  DateTime _scheduleAfter = DateTime.now();
  /// Hat der Nutzer "Schedule after" ueberhaupt angefasst? Die Vorbelegung ist "jetzt", und das
  /// heisst "egal" — nicht "fruehestens ab dieser Sekunde".
  bool _scheduleAfterGesetzt = false;
  DateTime _dueDate = DateTime.now().add(const Duration(days: 3, hours: 3));

  /// Gegenstueck zu [_scheduleAfterGesetzt] fuer die Deadline.
  ///
  /// Beim Anlegen ist sie vorbelegt und gilt als gesetzt; im Bearbeiten-Sheet laesst sie sich ueber
  /// das "x" wieder entfernen, und dann muss der Unterschied zwischen "kein Termin" und "der
  /// vorbelegte Termin" ausdrueckbar sein.
  bool _dueDateGesetzt = true;

  /// Feineinstellung pro Aufgabe; null heisst durchgaengig "Vorgabe aus den Einstellungen",
  /// niemals 0.
  int? _minChunk;
  int? _maxChunk;
  int? _maxChunksPerDay;

  bool _erweitertOffen = false;
  bool _showTitleError = false;
  String? _feldFehler;

  bool get _isEditing => widget.existingTask != null;

  @override
  void initState() {
    super.initState();
    final vorhanden = widget.existingTask;
    if (vorhanden == null) return;

    _titleController.text = vorhanden.title;
    _notesController.text = vorhanden.description ?? '';
    _durationMinutes = vorhanden.estimatedDurationMinutes;
    _durationController.text = _durationMinutes.toString();
    _priority = vorhanden.priority;
    _splitUp = vorhanden.splittable ?? true;
    _category = vorhanden.category;
    _minChunk = vorhanden.minChunkMinutes;
    _maxChunk = vorhanden.maxChunkMinutes;
    _maxChunksPerDay = vorhanden.maxChunksPerDay;

    _dueDateGesetzt = vorhanden.deadline != null;
    if (vorhanden.deadline != null) _dueDate = vorhanden.deadline!;

    _scheduleAfterGesetzt = vorhanden.notBefore != null;
    if (vorhanden.notBefore != null) _scheduleAfter = vorhanden.notBefore!;
  }

  @override
  void dispose() {
    _titleController.dispose();
    _durationController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  void _updateDuration(int deltaMinutes) {
    setState(() {
      _durationMinutes = (_durationMinutes + deltaMinutes).clamp(15, 480);
      _durationController.text = _durationMinutes.toString();
    });
  }

  Future<void> _pickDateTime(bool isDueDate) async {
    final initialDate = isDueDate ? _dueDate : _scheduleAfter;
    final date = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: DateTime.now().subtract(const Duration(days: 30)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null) return;

    if (!mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initialDate),
    );
    if (time == null) return;

    setState(() {
      final newDateTime = DateTime(date.year, date.month, date.day, time.hour, time.minute);
      if (isDueDate) {
        _dueDate = newDateTime;
        _dueDateGesetzt = true;
      } else {
        _scheduleAfter = newDateTime;
        _scheduleAfterGesetzt = true;
      }
    });
  }

  /// Prueft, was das Backend nicht prueft (dort stehen nur `@Min`-Grenzen).
  ///
  /// Gibt den Fehlertext zurueck oder null, wenn alles stimmt.
  String? _pruefe() {
    if (_minChunk != null && _maxChunk != null && _minChunk! > _maxChunk!) {
      return 'Der kürzeste Block darf nicht länger sein als der längste.';
    }
    if (_scheduleAfterGesetzt && _dueDateGesetzt && !_scheduleAfter.isBefore(_dueDate)) {
      return '"Frühestens ab" liegt nach der Deadline — so ist die Aufgabe nicht planbar.';
    }
    return null;
  }

  Future<void> _speichern() async {
    if (_titleController.text.trim().isEmpty) {
      setState(() => _showTitleError = true);
      return;
    }
    final fehler = _pruefe();
    if (fehler != null) {
      setState(() => _feldFehler = fehler);
      return;
    }
    setState(() => _feldFehler = null);

    if (_isEditing) {
      await _updateTask();
    } else {
      await _createTask();
    }
  }

  Future<void> _createTask() async {
    final String rawNotesText = _notesController.text.trim();

    final task = Task(
      title: _titleController.text.trim(),
      description: rawNotesText.isNotEmpty ? rawNotesText : null,
      priority: _priority,
      deadline: _dueDateGesetzt ? _dueDate : null,
      estimatedDurationMinutes: _durationMinutes,
      status: 'TODO',
      spaceType: widget.spaceType ?? 'TASKS',
      category: _category,
      splittable: _splitUp,
      minChunkMinutes: _minChunk,
      maxChunkMinutes: _maxChunk,
      maxChunksPerDay: _maxChunksPerDay,
      // "Schedule after" ist auf jetzt vorbelegt; dieser Wert bedeutet "keine Einschraenkung"
      // und wird nicht mitgeschickt, sonst haette jede Aufgabe ein notBefore, das beim naechsten
      // Lauf schon in der Vergangenheit liegt.
      notBefore: _scheduleAfterGesetzt ? _scheduleAfter : null,
    );

    await context.read<TaskProvider>().addTask(task);
    if (mounted) Navigator.pop(context);
  }

  Future<void> _updateTask() async {
    final alt = widget.existingTask!;
    final String rawNotesText = _notesController.text.trim();

    // Bewusst NEU gebaut statt ueber copyWith: Task.copyWith benutzt durchgaengig `?? this.x` und
    // kann ein Feld deshalb nicht auf null zuruecksetzen. Wer hier copyWith nimmt, bekommt ein
    // Sheet, in dem sich Werte nur noch setzen, aber nie mehr entfernen lassen — und zwar
    // lautlos.
    final task = Task(
      id: alt.id,
      title: _titleController.text.trim(),
      description: rawNotesText.isNotEmpty ? rawNotesText : null,
      priority: _priority,
      deadline: _dueDateGesetzt ? _dueDate : null,
      estimatedDurationMinutes: _durationMinutes,
      status: alt.status,
      spaceType: alt.spaceType,
      projectId: alt.projectId,
      category: _category,
      splittable: _splitUp,
      minChunkMinutes: _minChunk,
      maxChunkMinutes: _maxChunk,
      maxChunksPerDay: _maxChunksPerDay,
      completedMinutes: alt.completedMinutes,
      notBefore: _scheduleAfterGesetzt ? _scheduleAfter : null,
    );

    // Was WEG soll, muss ausdruecklich benannt werden: im Rumpf heisst null "unveraendert"
    // (siehe TaskService.updateTask im Backend). Geleert wird nur, was vorher wirklich einen Wert
    // hatte — sonst schickte jedes Speichern eine Liste voller Felder, die ohnehin leer sind.
    final clear = <String>{
      if (!_dueDateGesetzt && alt.deadline != null) 'DEADLINE',
      if (!_scheduleAfterGesetzt && alt.notBefore != null) 'NOT_BEFORE',
      if (_minChunk == null && alt.minChunkMinutes != null) 'MIN_CHUNK_MINUTES',
      if (_maxChunk == null && alt.maxChunkMinutes != null) 'MAX_CHUNK_MINUTES',
      if (_maxChunksPerDay == null && alt.maxChunksPerDay != null) 'MAX_CHUNKS_PER_DAY',
      if (rawNotesText.isEmpty && alt.description != null) 'DESCRIPTION',
    };

    await context.read<TaskProvider>().updateTask(task, clear: clear);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isDark ? const Color(0xFF131313) : Colors.white;
    final inputBgColor = isDark ? const Color(0xFF1E1E1E) : const Color(0xFFF7F8FC);
    final borderColor = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);
    final accentColor = const Color(0xFF5856D6);

    return Container(
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        left: 20,
        right: 20,
        top: 12,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  _isEditing ? 'Aufgabe bearbeiten' : 'Neue Aufgabe',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close, size: 20),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            
            TextField(
              controller: _titleController,
              autofocus: true,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
              decoration: InputDecoration(
                hintText: 'Task name...',
                prefixIcon: const Icon(Icons.sentiment_satisfied_alt_outlined, size: 22, color: Colors.grey),
                filled: true,
                fillColor: Colors.transparent,
                enabledBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: _showTitleError ? Colors.red : borderColor),
                  borderRadius: BorderRadius.circular(8),
                ),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: _showTitleError ? Colors.red : accentColor, width: 2),
                  borderRadius: BorderRadius.circular(8),
                ),
                contentPadding: const EdgeInsets.symmetric(vertical: 12),
              ),
              onChanged: (val) {
                if (_showTitleError && val.isNotEmpty) {
                  setState(() => _showTitleError = false);
                }
              },
            ),
            if (_showTitleError)
              const Padding(
                padding: EdgeInsets.only(top: 4, left: 4),
                child: Text('Task title is required', style: TextStyle(color: Colors.red, fontSize: 12)),
              ),
            
            const SizedBox(height: 20),
            
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Duration', style: TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _durationController,
                              keyboardType: TextInputType.number,
                              decoration: InputDecoration(
                                isDense: true,
                                filled: true,
                                fillColor: inputBgColor,
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(8),
                                  borderSide: BorderSide(color: borderColor),
                                ),
                                enabledBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(8),
                                  borderSide: BorderSide(color: borderColor),
                                ),
                                suffixText: 'min',
                                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              ),
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                              onChanged: (val) {
                                final mins = int.tryParse(val);
                                if (mins != null) {
                                  setState(() => _durationMinutes = mins.clamp(1, 1440));
                                }
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                          IconButton(
                            onPressed: () => _updateDuration(-15),
                            icon: const Icon(Icons.remove_circle_outline, size: 24, color: Colors.blue),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                          ),
                          const SizedBox(width: 4),
                          IconButton(
                            onPressed: () => _updateDuration(15),
                            icon: const Icon(Icons.add_circle_outline, size: 24, color: Colors.blue),
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Checkbox(
                          value: _splitUp,
                          onChanged: (val) => setState(() => _splitUp = val ?? false),
                          activeColor: accentColor,
                          materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          visualDensity: VisualDensity.compact,
                        ),
                        const Text('Split up', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ],
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            const Text('Priority', style: TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Row(
              children: [1, 2, 3, 4, 5].map((p) {
                final isSelected = _priority == p;
                final pColor = AppTheme.getPriorityColor(p);
                return Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _priority = p),
                    child: Container(
                      margin: EdgeInsets.only(right: p == 5 ? 0 : 8),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: isSelected ? pColor : pColor.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: isSelected ? pColor : borderColor),
                      ),
                      child: Text(
                        'P$p',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: isSelected ? Colors.white : pColor,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
            
            const SizedBox(height: 16),
            
            _BuildLabeledField(
              label: 'Category',
              child: Container(
                height: 48,
                decoration: BoxDecoration(
                  border: Border.all(color: borderColor),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: ['Personal', 'Studium'].map((cat) {
                    final isSel = _category == cat;
                    return Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _category = cat),
                        child: Container(
                          decoration: BoxDecoration(
                            color: isSel ? accentColor.withValues(alpha: 0.2) : null,
                            borderRadius: BorderRadius.circular(7),
                          ),
                          child: Center(
                            child: Text(
                              cat,
                              style: TextStyle(
                                fontWeight: isSel ? FontWeight.bold : FontWeight.normal,
                                color: isSel ? accentColor : Colors.grey,
                                fontSize: 14,
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
            
            const SizedBox(height: 16),

            Row(
              children: [
                Expanded(
                  child: _BuildLabeledField(
                    label: 'Schedule after',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(false),
                      child: _BuildReadonlyBox(
                        text: _scheduleAfterGesetzt
                            ? DateFormat('MMM d, yyyy h:mm a').format(_scheduleAfter)
                            : 'Egal',
                        // Das "x" ist der einzige Weg, ein gesetztes notBefore wieder loszuwerden.
                        onClear: _scheduleAfterGesetzt
                            ? () => setState(() => _scheduleAfterGesetzt = false)
                            : null,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _BuildLabeledField(
                    label: 'Due date',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(true),
                      child: _BuildReadonlyBox(
                        text: _dueDateGesetzt
                            ? DateFormat('MMM d, yyyy h:mm a').format(_dueDate)
                            : 'Keine',
                        onClear: _dueDateGesetzt
                            ? () => setState(() => _dueDateGesetzt = false)
                            : null,
                      ),
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Feineinstellung, standardmaessig zugeklappt: die drei Werte haben fuer die meisten
            // Aufgaben eine gute Vorgabe aus den Einstellungen und wuerden das Formular sonst nur
            // laenger machen.
            _ErweiterterBereich(
              offen: _erweitertOffen,
              onToggle: () => setState(() => _erweitertOffen = !_erweitertOffen),
              // Ohne Aufteilen sind alle drei Werte bedeutungslos — ausgegraut statt versteckt,
              // damit der Zusammenhang zum Haken sichtbar bleibt.
              aktiv: _splitUp,
              minChunk: _minChunk,
              maxChunk: _maxChunk,
              maxChunksPerDay: _maxChunksPerDay,
              onMinChunk: (v) => setState(() => _minChunk = v),
              onMaxChunk: (v) => setState(() => _maxChunk = v),
              onMaxChunksPerDay: (v) => setState(() => _maxChunksPerDay = v),
              borderColor: borderColor,
              accentColor: accentColor,
            ),

            const SizedBox(height: 16),

            _BuildLabeledField(
              label: 'Notes',
              child: TextField(
                controller: _notesController,
                maxLines: 3,
                style: const TextStyle(fontSize: 14),
                decoration: InputDecoration(
                  hintText: 'Add notes...',
                  filled: true,
                  fillColor: inputBgColor,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: borderColor),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: borderColor),
                  ),
                  contentPadding: const EdgeInsets.all(12),
                ),
              ),
            ),
            
            if (_feldFehler != null) ...[
              const SizedBox(height: 12),
              Text(
                _feldFehler!,
                style: const TextStyle(fontSize: 12, color: Color(0xFFD9534F)),
              ),
            ],

            const SizedBox(height: 24),

            Row(
              children: [
                const Spacer(),
                SizedBox(
                  height: 40,
                  child: FilledButton(
                    onPressed: _speichern,
                    style: FilledButton.styleFrom(
                      backgroundColor: accentColor,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                    ),
                    child: Text(_isEditing ? 'Speichern' : 'Create',
                        style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _BuildLabeledField extends StatelessWidget {
  final String label;
  final Widget child;
  const _BuildLabeledField({required this.label, required this.child});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
}

class _BuildReadonlyBox extends StatelessWidget {
  final String text;

  /// Gesetzt zeigt ein "x" zum Leeren des Feldes. Null heisst: es gibt nichts zu leeren.
  final VoidCallback? onClear;

  const _BuildReadonlyBox({required this.text, this.onClear});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final borderColor = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);

    return Container(
      width: double.infinity,
      padding: EdgeInsets.only(left: 12, right: onClear != null ? 4 : 12, top: 12, bottom: 12),
      decoration: BoxDecoration(
        border: Border.all(color: borderColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              text,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ),
          if (onClear != null)
            GestureDetector(
              onTap: onClear,
              behavior: HitTestBehavior.opaque,
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 6),
                child: Icon(Icons.close, size: 15, color: Colors.grey),
              ),
            ),
        ],
      ),
    );
  }
}

/// Zugeklappte Feineinstellung: wie lang ein einzelner Block sein darf und wie viele pro Tag.
///
/// Die Wertebereiche sind absichtlich dieselben wie bei "Shortest/Longest task block" in den
/// Einstellungen — die Werte hier ueberschreiben genau jene. Null heisst "Vorgabe von dort".
class _ErweiterterBereich extends StatelessWidget {
  final bool offen;
  final VoidCallback onToggle;
  final bool aktiv;
  final int? minChunk;
  final int? maxChunk;
  final int? maxChunksPerDay;
  final ValueChanged<int?> onMinChunk;
  final ValueChanged<int?> onMaxChunk;
  final ValueChanged<int?> onMaxChunksPerDay;
  final Color borderColor;
  final Color accentColor;

  const _ErweiterterBereich({
    required this.offen,
    required this.onToggle,
    required this.aktiv,
    required this.minChunk,
    required this.maxChunk,
    required this.maxChunksPerDay,
    required this.onMinChunk,
    required this.onMaxChunk,
    required this.onMaxChunksPerDay,
    required this.borderColor,
    required this.accentColor,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        GestureDetector(
          onTap: onToggle,
          behavior: HitTestBehavior.opaque,
          child: Row(
            children: [
              Icon(offen ? Icons.expand_less : Icons.expand_more, size: 18, color: Colors.grey),
              const SizedBox(width: 4),
              const Text('Erweitert',
                  style: TextStyle(fontSize: 12, color: Colors.grey, fontWeight: FontWeight.w600)),
            ],
          ),
        ),
        if (offen) ...[
          const SizedBox(height: 8),
          if (!aktiv)
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: Text(
                'Ohne "Split up" wird die Aufgabe am Stück geplant — diese Werte gelten dann nicht.',
                style: TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ),
          Opacity(
            opacity: aktiv ? 1 : 0.4,
            child: IgnorePointer(
              ignoring: !aktiv,
              child: Column(
                children: [
                  _NullableStepper(
                    label: 'Kürzester Block',
                    suffix: 'min',
                    value: minChunk,
                    min: 5,
                    max: 480,
                    step: 5,
                    fallback: 30,
                    onChanged: onMinChunk,
                    borderColor: borderColor,
                    accentColor: accentColor,
                  ),
                  _NullableStepper(
                    label: 'Längster Block',
                    suffix: 'min',
                    value: maxChunk,
                    min: 5,
                    max: 480,
                    step: 15,
                    fallback: 120,
                    onChanged: onMaxChunk,
                    borderColor: borderColor,
                    accentColor: accentColor,
                  ),
                  _NullableStepper(
                    label: 'Blöcke pro Tag',
                    suffix: '',
                    value: maxChunksPerDay,
                    min: 1,
                    max: 8,
                    step: 1,
                    fallback: 2,
                    onChanged: onMaxChunksPerDay,
                    borderColor: borderColor,
                    accentColor: accentColor,
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

/// Wie `_Stepper` in den Einstellungen, aber mit einem dritten Zustand: "nicht gesetzt".
///
/// Der Unterschied ist nicht kosmetisch. Ein Stepper ohne Null-Zustand zwingt jede Aufgabe zu
/// einem eigenen Wert, und damit haetten die Vorgaben aus den Einstellungen fuer bearbeitete
/// Aufgaben keine Wirkung mehr.
class _NullableStepper extends StatelessWidget {
  final String label;
  final String suffix;
  final int? value;
  final int min;
  final int max;
  final int step;

  /// Startwert, wenn aus "Vorgabe" heraus zum ersten Mal getippt wird.
  final int fallback;

  final ValueChanged<int?> onChanged;
  final Color borderColor;
  final Color accentColor;

  const _NullableStepper({
    required this.label,
    required this.suffix,
    required this.value,
    required this.min,
    required this.max,
    required this.step,
    required this.fallback,
    required this.onChanged,
    required this.borderColor,
    required this.accentColor,
  });

  @override
  Widget build(BuildContext context) {
    final gesetzt = value != null;
    final anzeige = gesetzt ? '${value!}${suffix.isEmpty ? '' : ' $suffix'}' : 'Vorgabe';

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label, style: const TextStyle(fontSize: 13))),
          IconButton(
            onPressed: () => onChanged(
                gesetzt ? (value! - step).clamp(min, max) : fallback),
            icon: const Icon(Icons.remove_circle_outline, size: 20),
            visualDensity: VisualDensity.compact,
          ),
          SizedBox(
            width: 68,
            child: Text(
              anzeige,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: gesetzt ? null : Colors.grey,
              ),
            ),
          ),
          IconButton(
            onPressed: () => onChanged(
                gesetzt ? (value! + step).clamp(min, max) : fallback),
            icon: const Icon(Icons.add_circle_outline, size: 20),
            visualDensity: VisualDensity.compact,
          ),
          // Zurueck auf "Vorgabe" — ohne diesen Weg waere ein einmal gesetzter Wert endgueltig.
          IconButton(
            onPressed: gesetzt ? () => onChanged(null) : null,
            icon: const Icon(Icons.close, size: 15),
            visualDensity: VisualDensity.compact,
            tooltip: 'Vorgabe aus den Einstellungen benutzen',
          ),
        ],
      ),
    );
  }
}