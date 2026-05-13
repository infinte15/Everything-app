import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/task_provider.dart';
import '../models/task.dart';
import '../config/app_theme.dart';

class CreateTaskSheet extends StatefulWidget {
  final String? spaceType;
  const CreateTaskSheet({super.key, this.spaceType});

  @override
  State<CreateTaskSheet> createState() => _CreateTaskSheetState();
}

class _CreateTaskSheetState extends State<CreateTaskSheet> {
  final _titleController = TextEditingController();
  final _durationController = TextEditingController(text: '60');
  final _notesController = TextEditingController();
  final _minDurationController = TextEditingController(text: '1 hr');
  final _maxDurationController = TextEditingController(text: '2 hrs');
  
  int _durationMinutes = 60;
  int _priority = 3;
  bool _splitUp = true;
  String _category = 'Personal';
  DateTime _scheduleAfter = DateTime.now();
  DateTime _dueDate = DateTime.now().add(const Duration(days: 3, hours: 3));
  
  bool _showTitleError = false;

  @override
  void dispose() {
    _titleController.dispose();
    _durationController.dispose();
    _notesController.dispose();
    _minDurationController.dispose();
    _maxDurationController.dispose();
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
      } else {
        _scheduleAfter = newDateTime;
      }
    });
  }

  Future<void> _createTask() async {
    if (_titleController.text.trim().isEmpty) {
      setState(() => _showTitleError = true);
      return;
    }

    final task = Task(
      title: _titleController.text.trim(),
      description: _notesController.text.trim().isEmpty ? null : _notesController.text.trim(),
      priority: _priority,
      estimatedDurationMinutes: _durationMinutes,
      deadline: _dueDate,
      status: 'TODO',
      spaceType: widget.spaceType ?? 'TASKS',
      category: _category, // Pass selected selection state here
    );

    await context.read<TaskProvider>().addTask(task);
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
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
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
            
            const SizedBox(height: 4),
            const Text('Tasks will schedule on finn.deuschle1@gmail.com.', style: TextStyle(fontSize: 11, color: Colors.grey)),
            
            const SizedBox(height: 16),
            
            Row(
              children: [
                Expanded(
                  child: _BuildLabeledField(
                    label: 'Schedule after',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(false),
                      child: _BuildReadonlyBox(text: DateFormat('MMM d, yyyy h:mm a').format(_scheduleAfter)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _BuildLabeledField(
                    label: 'Due date',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(true),
                      child: _BuildReadonlyBox(text: DateFormat('MMM d, yyyy h:mm a').format(_dueDate)),
                    ),
                  ),
                ),
              ],
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
            
            const SizedBox(height: 24),
            
            Row(
              children: [
                const Spacer(),
                SizedBox(
                  height: 40,
                  child: FilledButton(
                    onPressed: _createTask,
                    style: FilledButton.styleFrom(
                      backgroundColor: accentColor,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                    ),
                    child: const Text('Create', style: TextStyle(fontWeight: FontWeight.bold)),
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
  const _BuildReadonlyBox({required this.text});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final borderColor = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      decoration: BoxDecoration(
        border: Border.all(color: borderColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(text, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
    );
  }
}