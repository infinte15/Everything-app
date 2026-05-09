import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../config/app_theme.dart';
import '../models/habit.dart';
import '../providers/habit_provider.dart';

class CreateHabitSheet extends StatefulWidget {
  const CreateHabitSheet({super.key});

  @override
  State<CreateHabitSheet> createState() => _CreateHabitSheetState();
}

class _CreateHabitSheetState extends State<CreateHabitSheet> {
  final _nameController = TextEditingController();
  final _minDurationController = TextEditingController(text: '30');
  final _maxDurationController = TextEditingController(text: '30');
  final _notesController = TextEditingController();
  
  int _priority = 3;
  Color _selectedColor = Colors.blue;
  String _category = 'Work';
  
  String _repeatType = 'Weekly';
  int _timesPerWeek = 1;
  Set<int> _idealDays = {1}; // 1 = Monday, etc.
  TimeOfDay _idealTime = const TimeOfDay(hour: 9, minute: 0);
  
  // Monthly options
  String _monthlyType = 'On day'; // 'On day' or 'On the'
  int _dayOfMonth = 1;
  String _weekPosition = 'first'; // first, second, third, fourth, last
  int _dayOfWeek = 1; // 1 = Monday

  // Custom options
  int _customFreq = 1;
  String _customUnit = 'week'; // day, week, month

  bool _showNotesField = false;
  bool _showDatesFields = false;
  DateTime? _startDate;
  DateTime? _endDate;
  bool _showNameError = false;

  final List<Color> _colors = [Colors.blue, Colors.red, Colors.green, Colors.orange, Colors.purple, Colors.teal];

  @override
  void dispose() {
    _nameController.dispose();
    _minDurationController.dispose();
    _maxDurationController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  void _updateDuration(TextEditingController controller, int delta) {
    int val = int.tryParse(controller.text) ?? 30;
    val = (val + delta).clamp(15, 1440);
    setState(() => controller.text = val.toString());
  }

  Future<void> _pickDate(bool isStart) async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365 * 2)),
    );
    if (date != null) {
      setState(() {
        if (isStart) _startDate = date; else _endDate = date;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isDark ? const Color(0xFF0E0E0E) : Colors.white;
    final inputBgColor = isDark ? const Color(0xFF131313) : const Color(0xFFF7F8FC);
    final borderColor = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);
    final accentColor = AppTheme.primaryColor;

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
            Center(
              child: Container(
                width: 40, height: 4, margin: const EdgeInsets.only(bottom: 20),
                decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 0.3), borderRadius: BorderRadius.circular(2)),
              ),
            ),

            // --- Habit Details ---
            const Text('Habit details', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const Text('Name, priority, & general settings', style: TextStyle(fontSize: 13, color: Colors.grey)),
            const SizedBox(height: 20),
            
            _BuildLabeledField(
              label: 'Habit name',
              child: TextField(
                controller: _nameController,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                decoration: InputDecoration(
                  hintText: 'Enter a Habit name...',
                  filled: true, fillColor: inputBgColor,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: borderColor)),
                  enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: borderColor)),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                ),
              ),
            ),
            if (_showNameError) const Text('Habit name is required', style: TextStyle(color: Colors.red, fontSize: 12)),

            const SizedBox(height: 20),
            const Text('Priority', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Row(
              children: [1, 2, 3, 4, 5].map((p) {
                final isSel = _priority == p;
                final pColor = AppTheme.getPriorityColor(p);
                return Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _priority = p),
                    child: Container(
                      margin: EdgeInsets.only(right: p == 5 ? 0 : 8),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: isSel ? pColor : pColor.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: isSel ? pColor : borderColor),
                      ),
                      child: Text('P$p', textAlign: TextAlign.center, style: TextStyle(color: isSel ? Colors.white : pColor, fontWeight: FontWeight.bold)),
                    ),
                  ),
                );
              }).toList(),
            ),

            const SizedBox(height: 20),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  flex: 1,
                  child: _BuildLabeledField(
                    label: 'Color',
                    child: PopupMenuButton<Color>(
                      onSelected: (c) => setState(() => _selectedColor = c),
                      itemBuilder: (context) => _colors.map((c) => PopupMenuItem(value: c, child: Container(width: 30, height: 30, color: c))).toList(),
                      child: Container(
                        height: 48,
                        decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)),
                        child: Center(child: Container(width: 24, height: 24, decoration: BoxDecoration(color: _selectedColor, shape: BoxShape.circle))),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: _BuildLabeledField(
                    label: 'Category',
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)),
                      child: Row(
                        children: ['Studying', 'Work'].map((cat) {
                          final isSel = _category == cat;
                          return Expanded(
                            child: GestureDetector(
                              onTap: () => setState(() => _category = cat),
                              child: Container(
                                decoration: BoxDecoration(color: isSel ? accentColor.withValues(alpha: 0.2) : null, borderRadius: BorderRadius.circular(7)),
                                child: Center(child: Text(cat, style: TextStyle(fontWeight: isSel ? FontWeight.bold : FontWeight.normal, color: isSel ? accentColor : Colors.grey))),
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),
            if (!_showNotesField)
              TextButton.icon(onPressed: () => setState(() => _showNotesField = true), icon: const Icon(Icons.add, size: 18), label: const Text('Add notes')),
            if (_showNotesField)
              _BuildLabeledField(
                label: 'Notes',
                child: TextField(controller: _notesController, maxLines: 2, decoration: InputDecoration(filled: true, fillColor: inputBgColor, border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)))),
              ),

            const Divider(height: 40),

            // --- Scheduling ---
            const Text('Scheduling', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const Text('Frequency, repeat, & time preferences', style: TextStyle(fontSize: 13, color: Colors.grey)),
            const SizedBox(height: 20),

            _BuildLabeledField(
              label: 'Repeat',
              child: Container(
                height: 45,
                decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)),
                child: Row(
                  children: ['Daily', 'Weekly', 'Monthly', 'Custom'].map((r) {
                    final isSel = _repeatType == r;
                    return Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _repeatType = r),
                        child: Container(
                          decoration: BoxDecoration(color: isSel ? accentColor.withValues(alpha: 0.2) : null, borderRadius: BorderRadius.circular(7)),
                          child: Center(child: Text(r, style: TextStyle(fontSize: 12, fontWeight: isSel ? FontWeight.bold : FontWeight.normal, color: isSel ? accentColor : Colors.grey))),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),

            if (_repeatType == 'Weekly') ...[
              const SizedBox(height: 20),
              Row(
                children: [
                  const Text('Times a week:', style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(width: 12),
                  DropdownButton<int>(
                    value: _timesPerWeek,
                    items: List.generate(7, (i) => i + 1).map((i) => DropdownMenuItem(value: i, child: Text('$i'))).toList(),
                    onChanged: (v) {
                      setState(() {
                        _timesPerWeek = v!;
                        // Truncate selected days if they exceed the new limit
                        while (_idealDays.length > _timesPerWeek) {
                          _idealDays.remove(_idealDays.last);
                        }
                      });
                    },
                  ),
                ],
              ),
              const SizedBox(height: 20),
              const Text('Ideal days', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: List.generate(7, (i) {
                  final day = i + 1;
                  final isSel = _idealDays.contains(day);
                  final labels = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        if (isSel) {
                          _idealDays.remove(day);
                        } else if (_idealDays.length < _timesPerWeek) {
                          _idealDays.add(day);
                        }
                      });
                    },
                    child: Container(
                      width: 40, height: 40,
                      decoration: BoxDecoration(color: isSel ? accentColor : borderColor.withValues(alpha: 0.1), shape: BoxShape.circle),
                      child: Center(child: Text(labels[i], style: TextStyle(color: isSel ? Colors.white : Colors.grey, fontWeight: FontWeight.bold))),
                    ),
                  );
                }),
              ),
            ],

            if (_repeatType == 'Monthly' || (_repeatType == 'Custom' && _customUnit == 'month')) ...[
              const SizedBox(height: 20),
              Column(
                children: [
                  RadioListTile<String>(
                    title: Row(
                      children: [
                        const Text('On day ', style: TextStyle(fontSize: 14)),
                        DropdownButton<int>(
                          value: _dayOfMonth, 
                          items: List.generate(31, (i)=>i+1).map((i)=>DropdownMenuItem(value:i, child:Text('$i'))).toList(), 
                          onChanged: (v)=>setState(()=>_dayOfMonth=v!)
                        )
                      ]
                    ),
                    value: 'On day', groupValue: _monthlyType, onChanged: (v)=>setState(()=>_monthlyType=v!),
                  ),
                  RadioListTile<String>(
                    title: Wrap(
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        const Text('On the ', style: TextStyle(fontSize: 14)),
                        DropdownButton<String>(
                          value: _weekPosition, 
                          items: ['first','second','third','fourth','last'].map((s)=>DropdownMenuItem(value:s, child:Text(s))).toList(), 
                          onChanged: (v)=>setState(()=>_weekPosition=v!)
                        ),
                        const SizedBox(width: 4),
                        DropdownButton<int>(
                          value: _dayOfWeek, 
                          items: [1,2,3,4,5,6,7].map((i)=>DropdownMenuItem(value:i, child:Text(['Mon','Tue','Wed','Thu','Fri','Sat','Sun'][i-1]))).toList(), 
                          onChanged: (v)=>setState(()=>_dayOfWeek=v!)
                        ),
                      ]
                    ),
                    value: 'On the', groupValue: _monthlyType, onChanged: (v)=>setState(()=>_monthlyType=v!),
                  ),
                ],
              ),
            ],

            if (_repeatType == 'Custom') ...[
              const SizedBox(height: 20),
              Row(
                children: [
                  const Text('Every '),
                  SizedBox(width: 50, child: TextField(keyboardType: TextInputType.number, controller: TextEditingController(text: _customFreq.toString()), onChanged: (v)=>_customFreq=int.tryParse(v)??1, decoration: const InputDecoration(isDense: true))),
                  const SizedBox(width: 8),
                  DropdownButton<String>(value: _customUnit, items: ['day','week','month'].map((s)=>DropdownMenuItem(value:s, child:Text(s))).toList(), onChanged: (v)=>setState(()=>_customUnit=v!)),
                ],
              ),
              if (_customUnit == 'week') ...[
                const SizedBox(height: 12),
                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: List.generate(7, (i)=>GestureDetector(onTap: ()=>setState(()=>_idealDays.contains(i+1)?_idealDays.remove(i+1):_idealDays.add(i+1)), child: Container(width: 35, height: 35, decoration: BoxDecoration(color: _idealDays.contains(i+1)?accentColor:borderColor, shape: BoxShape.circle), child: Center(child: Text(['M','T','W','T','F','S','S'][i])))))),
              ],
            ],

            const SizedBox(height: 24),
            const Text('Ideal time', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            GestureDetector(
              onTap: () async { final t = await showTimePicker(context: context, initialTime: _idealTime); if (t != null) setState(()=>_idealTime=t); },
              child: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)), child: Text(_idealTime.format(context))),
            ),

            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(child: _BuildDurationInput(label: 'Minimum', controller: _minDurationController, onDelta: (d) => _updateDuration(_minDurationController, d))),
                const SizedBox(width: 12),
                Expanded(child: _BuildDurationInput(label: 'Maximum', controller: _maxDurationController, onDelta: (d) => _updateDuration(_maxDurationController, d))),
              ],
            ),

            const SizedBox(height: 16),
            if (!_showDatesFields)
              TextButton(onPressed: () => setState(() => _showDatesFields = true), child: const Text('+ Add start or end date')),
            if (_showDatesFields)
              Row(
                children: [
                  Expanded(child: _BuildLabeledField(label: 'Start Date', child: GestureDetector(onTap: () => _pickDate(true), child: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)), child: Text(_startDate == null ? 'Select' : DateFormat('MMM d, y').format(_startDate!)))))),
                  const SizedBox(width: 12),
                  Expanded(child: _BuildLabeledField(label: 'End Date', child: GestureDetector(onTap: () => _pickDate(false), child: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(border: Border.all(color: borderColor), borderRadius: BorderRadius.circular(8)), child: Text(_endDate == null ? 'Select' : DateFormat('MMM d, y').format(_endDate!)))))),
                ],
              ),

            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: FilledButton(
                onPressed: () async {
                  if (_nameController.text.isEmpty) {
                    setState(() => _showNameError = true);
                    return;
                  }

                  final now = DateTime.now();
                  final preferredTime = DateTime(now.year, now.month, now.day, _idealTime.hour, _idealTime.minute);
                  
                  final habit = Habit(
                    name: _nameController.text,
                    description: _notesController.text.isEmpty ? null : _notesController.text,
                    frequency: _repeatType.toUpperCase(),
                    monday: _idealDays.contains(1),
                    tuesday: _idealDays.contains(2),
                    wednesday: _idealDays.contains(3),
                    thursday: _idealDays.contains(4),
                    friday: _idealDays.contains(5),
                    saturday: _idealDays.contains(6),
                    sunday: _idealDays.contains(7),
                    preferredTime: preferredTime,
                    durationMinutes: int.tryParse(_minDurationController.text) ?? 30,
                    startDate: _startDate,
                    endDate: _endDate,
                  );

                  final success = await context.read<HabitProvider>().addHabit(habit);
                  if (success && mounted) {
                    Navigator.pop(context);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Habit gespeichert')),
                    );
                  }
                },
                style: FilledButton.styleFrom(
                  backgroundColor: accentColor,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: const Text('Save Habit', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
            const SizedBox(height: 20),
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
  @override Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [if (label.isNotEmpty) Text(label, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold)), const SizedBox(height: 8), child]);
  }
}

class _BuildDurationInput extends StatelessWidget {
  final String label;
  final TextEditingController controller;
  final Function(int) onDelta;
  const _BuildDurationInput({required this.label, required this.controller, required this.onDelta});
  @override Widget build(BuildContext context) {
    final borderColor = Theme.of(context).brightness == Brightness.dark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(child: TextField(controller: controller, keyboardType: TextInputType.number, decoration: const InputDecoration(isDense: true, border: OutlineInputBorder(), suffixText: 'min'))),
            const SizedBox(width: 4),
            IconButton(icon: const Icon(Icons.remove_circle_outline, color: Colors.blue), onPressed: () => onDelta(-15)),
            IconButton(icon: const Icon(Icons.add_circle_outline, color: Colors.blue), onPressed: () => onDelta(15)),
          ],
        ),
      ],
    );
  }
}
