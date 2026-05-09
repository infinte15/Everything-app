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
  late DateTime _date;
  late DateTime _start;
  late DateTime _end;
  int _durationMinutes = 60;
  String _type = 'TASK';
  bool _isFixed = false;

  static const _types = ['TASK', 'STUDY', 'OTHER'];
  static const _durations = [15, 30, 45, 60, 90, 120];

  @override
  void initState() {
    super.initState();
    _date = widget.selectedDay;
    final now = DateTime.now();
    _start = DateTime(_date.year, _date.month, _date.day, now.hour, 0);
    _end = _start.add(const Duration(hours: 1));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 365 * 2)),
    );
    if (picked == null) return;
    setState(() {
      _date = picked;
      _start = DateTime(_date.year, _date.month, _date.day, _start.hour, _start.minute);
      _end = _start.add(Duration(minutes: _durationMinutes));
    });
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_start),
    );
    if (picked == null) return;
    setState(() {
      _start = DateTime(_date.year, _date.month, _date.day, picked.hour, picked.minute);
      _end = _start.add(Duration(minutes: _durationMinutes));
    });
  }

  void _setDuration(int minutes) {
    setState(() {
      _durationMinutes = minutes;
      _end = _start.add(Duration(minutes: minutes));
    });
  }

  String _formatDuration(int min) {
    if (min < 60) return '${min}m';
    if (min % 60 == 0) return '${min ~/ 60}h';
    return '${min ~/ 60}h ${min % 60}m';
  }

  Color _typeColor(String type) {
    switch (type.toUpperCase()) {
      case 'TASK': return AppTheme.primaryColor;
      case 'STUDY': return AppTheme.studyColor;
      default: return AppTheme.primaryColor;
    }
  }

  IconData _typeIcon(String type) {
    switch (type.toUpperCase()) {
      case 'TASK': return Icons.check_circle_outline_rounded;
      case 'STUDY': return Icons.menu_book_rounded;
      default: return Icons.event_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = isDark ? const Color(0xFF1A1A24) : Colors.white;
    final borderCol = isDark ? const Color(0xFF2A2A38) : const Color(0xFFE8EAF0);

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        margin: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.zero),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 40, height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text('New Event', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: -0.4)),
              const SizedBox(height: 16),

              TextField(
                controller: _titleController,
                autofocus: true,
                decoration: const InputDecoration(
                  hintText: 'Event title',
                  prefixIcon: Icon(Icons.title_rounded, size: 18),
                  border: OutlineInputBorder(borderRadius: BorderRadius.zero),
                ),
              ),
              const SizedBox(height: 12),

              const Text('TYPE', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 1.5)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: _types.map((t) {
                  final sel = _type == t;
                  final color = _typeColor(t);
                  return GestureDetector(
                    onTap: () => setState(() => _type = t),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 150),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(
                        color: sel ? color : color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.zero,
                        border: Border.all(color: sel ? color : Colors.transparent),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_typeIcon(t), size: 13, color: sel ? Colors.white : color),
                          const SizedBox(width: 5),
                          Text(t, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: sel ? Colors.white : color)),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 14),

              const Text('DATE & TIME', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 1.5)),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    flex: 3,
                    child: GestureDetector(
                      onTap: _pickDate,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        decoration: BoxDecoration(border: Border.all(color: borderCol)),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Date', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.w600)),
                            const SizedBox(height: 2),
                            Text(DateFormat('EEE, MMM d').format(_date), style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: GestureDetector(
                      onTap: _pickTime,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        decoration: BoxDecoration(border: Border.all(color: borderCol)),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Start', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.w600)),
                            const SizedBox(height: 2),
                            Text(DateFormat('h:mm a').format(_start), style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              const Text('DURATION', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 1.5)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _durations.map((min) {
                  final sel = _durationMinutes == min;
                  return GestureDetector(
                    onTap: () => _setDuration(min),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 120),
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: sel ? AppTheme.primaryColor : Colors.transparent,
                        border: Border.all(color: sel ? AppTheme.primaryColor : borderCol),
                      ),
                      child: Text(_formatDuration(min), style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: sel ? Colors.white : Colors.grey)),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),

              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(border: Border.all(color: borderCol)),
                child: Row(
                  children: [
                    const Icon(Icons.lock_outline_rounded, size: 16, color: Colors.grey),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Fixed time', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                          Text("AI won't reschedule this", style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ],
                      ),
                    ),
                    Switch(
                      value: _isFixed,
                      onChanged: (v) => setState(() => _isFixed = v),
                      activeColor: AppTheme.primaryColor,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              TextField(
                controller: _descController,
                maxLines: 2,
                decoration: const InputDecoration(
                  hintText: 'Notes (optional)',
                  prefixIcon: Icon(Icons.notes_rounded, size: 18),
                  border: OutlineInputBorder(borderRadius: BorderRadius.zero),
                ),
              ),
              const SizedBox(height: 20),

              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: AppTheme.primaryColor,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                  ),
                  onPressed: () async {
                    if (_titleController.text.trim().isEmpty) return;
                    final event = CalendarEvent(
                      title: _titleController.text.trim(),
                      description: _descController.text.trim().isEmpty ? null : _descController.text.trim(),
                      startTime: _start,
                      endTime: _end,
                      eventType: _type,
                      isFixed: _isFixed,
                    );
                    final success = await context.read<CalendarProvider>().addEvent(event);
                    if (context.mounted && success) {
                      if (_type == 'TASK') context.read<TaskProvider>().loadTasks();
                      Navigator.pop(context);
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text('✅ ${_type == 'TASK' ? 'Task' : 'Event'} "${event.title}" created'),
                        behavior: SnackBarBehavior.floating,
                        backgroundColor: const Color(0xFF1A1A24),
                      ));
                    }
                  },
                  child: const Text('Create Event', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: Colors.white)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
