import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/calendar_provider.dart';
import '../providers/task_provider.dart';
import '../models/calendar_event.dart';
import '../config/app_theme.dart';

class CreateEventSheet extends StatefulWidget {
  final DateTime selectedDay;
  const CreateEventSheet({super.key, required this.selectedDay});

  @override
  State<CreateEventSheet> createState() => _CreateEventSheetState();
}

class _CreateEventSheetState extends State<CreateEventSheet> {
  final _titleController = TextEditingController();
  final _descController = TextEditingController();
  
  late DateTime _startTime;
  late DateTime _endTime;
  String _type = 'Personal';
  bool _isFixed = false;
  bool _showTitleError = false;

  static const _types = ['Personal', 'Uni'];

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _startTime = DateTime(widget.selectedDay.year, widget.selectedDay.month, widget.selectedDay.day, now.hour, 0);
    _endTime = _startTime.add(const Duration(hours: 1));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickDateTime(bool isEndTime) async {
    final initialDate = isEndTime ? _endTime : _startTime;
    final date = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365 * 2)),
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
      if (isEndTime) {
        _endTime = newDateTime;
        if (_endTime.isBefore(_startTime)) {
          _startTime = _endTime.subtract(const Duration(hours: 1));
        }
      } else {
        _startTime = newDateTime;
        if (_endTime.isBefore(_startTime)) {
          _endTime = _startTime.add(const Duration(hours: 1));
        }
      }
    });
  }

  Future<void> _createEvent() async {
    if (_titleController.text.trim().isEmpty) {
      setState(() => _showTitleError = true);
      return;
    }

    final event = CalendarEvent(
      title: _titleController.text.trim(),
      description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
      startTime: _startTime,
      endTime: _endTime,
      eventType: _type,
      isFixed: _isFixed,
    );

    final success = await context.read<CalendarProvider>().addEvent(event);
    if (mounted && success) {
      if (_type == 'TASK') context.read<TaskProvider>().loadTasks();
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('✅ ${_type == 'TASK' ? 'Task' : 'Event'} "${event.title}" created'),
        behavior: SnackBarBehavior.floating,
        backgroundColor: const Color(0xFF1A1A24),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isDark ? const Color(0xFF131313) : Colors.white;
    final inputBgColor = isDark ? const Color(0xFF1E1E1E) : const Color(0xFFF7F8FC);
    final borderColor = isDark ? const Color(0xFF333333) : const Color(0xFFE8EAF0);
    const accentColor = Color(0xFF5856D6);

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
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('New Event', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close, size: 20),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
            const SizedBox(height: 16),
            
            // Event Title Input
            TextField(
              controller: _titleController,
              autofocus: true,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
              decoration: InputDecoration(
                hintText: 'Event title...',
                prefixIcon: const Icon(Icons.event_note, size: 22, color: Colors.grey),
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
                child: Text('Event title is required', style: TextStyle(color: Colors.red, fontSize: 12)),
              ),
            
            const SizedBox(height: 20),
            
            // Type Selection (Category style)
            _BuildLabeledField(
              label: 'Type',
              child: Container(
                height: 48,
                decoration: BoxDecoration(
                  border: Border.all(color: borderColor),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: _types.map((t) {
                    final isSel = _type == t;
                    return Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _type = t),
                        child: Container(
                          decoration: BoxDecoration(
                            color: isSel ? accentColor.withValues(alpha: 0.2) : null,
                            borderRadius: BorderRadius.circular(7),
                          ),
                          child: Center(
                            child: Text(
                              t,
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
            
            // Start Time / End Time
            Row(
              children: [
                Expanded(
                  child: _BuildLabeledField(
                    label: 'Start Time',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(false),
                      child: _BuildReadonlyBox(text: DateFormat('MMM d, h:mm a').format(_startTime)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _BuildLabeledField(
                    label: 'End Time',
                    child: GestureDetector(
                      onTap: () => _pickDateTime(true),
                      child: _BuildReadonlyBox(text: DateFormat('MMM d, h:mm a').format(_endTime)),
                    ),
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 16),

            // Fixed Time Switch
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                border: Border.all(color: borderColor),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.lock_outline_rounded, size: 18, color: Colors.grey),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Fixed time', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                        Text("AI won't reschedule this", style: TextStyle(fontSize: 11, color: Colors.grey)),
                      ],
                    ),
                  ),
                  Switch(
                    value: _isFixed,
                    onChanged: (v) => setState(() => _isFixed = v),
                    activeColor: accentColor,
                  ),
                ],
              ),
            ),
            
            const SizedBox(height: 16),

            // Notes field
            _BuildLabeledField(
              label: 'Notes',
              child: TextField(
                controller: _descController,
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
            
            // Bottom Actions
            Row(
              children: [
                const Spacer(),
                SizedBox(
                  height: 40,
                  child: FilledButton(
                    onPressed: _createEvent,
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
