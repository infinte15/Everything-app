import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../providers/task_provider.dart';
import '../../providers/habit_provider.dart';
import '../../config/app_theme.dart';
import '../../models/task.dart';
import '../../models/habit.dart';
import '../../widgets/create_task_sheet.dart';
import '../../widgets/create_habit_sheet.dart';

class TasksScreen extends StatefulWidget {
  final String title;
  final String? spaceType;

  const TasksScreen({
    super.key,
    this.title = 'Aufgaben',
    this.spaceType,
  });

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  
  // Consolidated Filter & Sort State Options
  String _selectedCategoryFilter = 'All'; // 'All', 'Personal', 'Studium'
  String _selectedSortOption = 'datum_ab'; // 'datum_ab', 'datum_auf', 'prio_auf', 'prio_ab'

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.spaceType == 'HABITS') {
        context.read<HabitProvider>().loadHabits();
      } else {
        context.read<TaskProvider>().loadTasks();
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  // Helper method to display a bottom modal sheet containing all parameters at once
  void _showFilterAndSortMenu() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setModalState) {
            final isDark = Theme.of(context).brightness == Brightness.dark;
            
            return Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Filter & Sortierung',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.pop(context),
                      )
                    ],
                  ),
                  const Divider(),
                  const SizedBox(height: 8),
                  
                  // Category Filter Section
                  const Text(
                    'Kategorie filtern',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: ['All', 'Personal', 'Studium'].map((cat) {
                      final isSelected = _selectedCategoryFilter == cat;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8.0),
                        child: ChoiceChip(
                          label: Text(cat == 'All' ? 'Alle' : cat),
                          selected: isSelected,
                          onSelected: (bool selected) {
                            if (selected) {
                              setModalState(() => _selectedCategoryFilter = cat);
                              setState(() => _selectedCategoryFilter = cat);
                            }
                          },
                        ),
                      );
                    }).toList(),
                  ),
                  
                  const SizedBox(height: 20),
                  
                  // Sorting Direction Section
                  const Text(
                    'Sortieren nach',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<String>(
                    value: _selectedSortOption,
                    decoration: InputDecoration(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                      filled: true,
                      fillColor: isDark ? const Color(0xFF1E1E1E) : const Color(0xFFF7F8FC),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'datum_ab', child: Text('Fälligkeit: Späteste zuerst')),
                      DropdownMenuItem(value: 'datum_auf', child: Text('Fälligkeit: Früheste zuerst')),
                      DropdownMenuItem(value: 'prio_ab', child: Text('Priorität: Hoch zu Niedrig')),
                      DropdownMenuItem(value: 'prio_auf', child: Text('Priorität: Niedrig zu Hoch')),
                    ],
                    onChanged: (String? newValue) {
                      if (newValue != null) {
                        setModalState(() => _selectedSortOption = newValue);
                        setState(() => _selectedSortOption = newValue);
                      }
                    },
                  ),
                  const SizedBox(height: 16),
                ],
              ),
            );
          },
        );
      },
    );
  }

  List<Task> _processTasksPipeline(List<Task> rawTasks) {
  List<Task> filtered = widget.spaceType == null 
      ? List.from(rawTasks)
      : rawTasks.where((t) => t.spaceType == widget.spaceType).toList();
  if (_selectedCategoryFilter != 'All') {
    filtered = filtered.where((t) => t.displayCategory == _selectedCategoryFilter).toList();
  }
  filtered.sort((a, b) {
    if (_selectedSortOption.startsWith('datum')) {
      if (a.deadline == null && b.deadline == null) return 0;
      if (a.deadline == null) return 1;
      if (b.deadline == null) return -1;
      return _selectedSortOption == 'datum_auf'
          ? a.deadline!.compareTo(b.deadline!)
          : b.deadline!.compareTo(a.deadline!);
    } else if (_selectedSortOption.startsWith('prio')) {
      return _selectedSortOption == 'prio_ab'
          ? b.priority.compareTo(a.priority)
          : a.priority.compareTo(b.priority);
    }
    return 0;
  });

  return filtered;
}

  @override
  Widget build(BuildContext context) {
    if (widget.spaceType == 'HABITS') {
      final habitProvider = context.watch<HabitProvider>();
      final habits = habitProvider.habits;

      return Scaffold(
        appBar: AppBar(
          leading: const BackButton(),
          title: Text(widget.title),
        ),
        body: habitProvider.isLoading
            ? const Center(child: CircularProgressIndicator())
            : ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: habits.length,
                itemBuilder: (_, i) {
                  final h = habits[i];
                  return Card(
                    margin: const EdgeInsets.only(bottom: 12),
                    child: ListTile(
                      leading: const CircleAvatar(child: Icon(Icons.repeat)),
                      title: Text(h.name),
                      subtitle: Text(h.frequency),
                      trailing: Checkbox(value: false, onChanged: (v) {}),
                    ),
                  );
                },
              ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () => _showCreateHabitDialog(context),
          icon: const Icon(Icons.add),
          label: const Text('Neuer Habit'),
          backgroundColor: const Color(0xFF81C784),
        ),
      );
    }

    final tasksProvider = context.watch<TaskProvider>();
    
    // Process input pipelines through sorting/filtering logic mappings
    final todoProcessed = _processTasksPipeline(tasksProvider.todoTasks);
    final completedProcessed = _processTasksPipeline(tasksProvider.completedTasks);

    return Scaffold(
      appBar: AppBar(
        leading: const BackButton(),
        title: Text(widget.title),
        bottom: TabBar(
          controller: _tabController,
          tabs: [
            Tab(text: 'Offen (${todoProcessed.length})'),
            Tab(text: 'Fertig (${completedProcessed.length})'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () => showSearch(
              context: context,
              delegate: _TaskSearchDelegate(tasks: todoProcessed + completedProcessed),
            ),
          ),
          IconButton(
            icon: Icon(
              Icons.filter_list,
              color: (_selectedCategoryFilter != 'All' || _selectedSortOption != 'datum_ab')
                  ? AppTheme.tasksColor
                  : null,
            ),
            onPressed: _showFilterAndSortMenu,
          ),
        ],
      ),
      body: tasksProvider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                _TaskList(tasks: todoProcessed),
                _TaskList(tasks: completedProcessed),
              ],
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateTaskDialog(context),
        icon: const Icon(Icons.add),
        label: const Text('Neue Aufgabe'),
        backgroundColor: AppTheme.tasksColor,
      ),
    );
  }

  void _showCreateHabitDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateHabitSheet(),
    );
  }

  void _showCreateTaskDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => CreateTaskSheet(spaceType: widget.spaceType),
    );
  }
}

// ─── Task List ─────────────────────────────────────────────────────────────────

class _TaskList extends StatelessWidget {
  final List<Task> tasks;
  const _TaskList({required this.tasks});

  @override
  Widget build(BuildContext context) {
    if (tasks.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.task_alt, size: 64, color: Colors.grey),
            SizedBox(height: 16),
            Text('Keine Aufgaben vorhanden',
                style: TextStyle(color: Colors.grey, fontSize: 16)),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: tasks.length,
      itemBuilder: (_, i) => _TaskTile(task: tasks[i]),
    );
  }
}

// ─── Task Tile ─────────────────────────────────────────────────────────────────

class _TaskTile extends StatelessWidget {
  final Task task;
  const _TaskTile({required this.task});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final priorityColor = AppTheme.getPriorityColor(task.priority);

    return Dismissible(
      key: Key('task_${task.id}'),
      background: Container(
        color: Colors.green,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.only(left: 16),
        child: const Icon(Icons.check, color: Colors.white),
      ),
      secondaryBackground: Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 16),
        child: const Icon(Icons.delete, color: Colors.white),
      ),
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.startToEnd) {
          await context.read<TaskProvider>().completeTask(task.id!);
          return false;
        } else {
          return await showDialog<bool>(
            context: context,
            builder: (_) => AlertDialog(
              title: const Text('Aufgabe löschen?'),
              content: Text('„${task.title}" wirklich löschen?'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('Abbrechen')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('Löschen')),
              ],
            ),
          );
        }
      },
      onDismissed: (direction) {
        if (direction == DismissDirection.endToStart) {
          context.read<TaskProvider>().deleteTask(task.id!);
        }
      },
      child: Card(
        margin: const EdgeInsets.only(bottom: 8),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Container(
                width: 4,
                height: 50,
                decoration: BoxDecoration(
                  color: priorityColor,
                  borderRadius: BorderRadius.circular(4),
                ),
              ),
              const SizedBox(width: 12),

              Checkbox(
                value: task.isCompleted,
                onChanged: (_) =>
                    context.read<TaskProvider>().completeTask(task.id!),
                shape: const CircleBorder(),
              ),

              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      task.title,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                        decoration: task.isCompleted
                            ? TextDecoration.lineThrough
                            : null,
                        color: task.isCompleted ? Colors.grey : null,
                      ),
                    ),
                    if (task.description != null)
                      Text(task.description!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall
                              ?.copyWith(color: Colors.grey)),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        if (task.deadline != null) ...[
                          Icon(
                            Icons.access_time,
                            size: 12,
                            color: task.isOverdue ? Colors.red : Colors.grey,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            DateFormat('dd.MM.yyyy').format(task.deadline!),
                            style: TextStyle(
                              fontSize: 11,
                              color: task.isOverdue ? Colors.red : Colors.grey,
                              fontWeight: task.isOverdue
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                            ),
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (task.estimatedDurationMinutes > 0) ...[
                          const Icon(Icons.timer_outlined,
                              size: 12, color: Colors.grey),
                          const SizedBox(width: 4),
                          Text(
                            '${task.estimatedDurationMinutes} Min.',
                            style: const TextStyle(
                                fontSize: 11, color: Colors.grey),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
              
              const SizedBox(width: 8),

              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 1. Inside the _TaskTile description paragraph display element step:
if (task.displayDescription.isNotEmpty)
  Text(
    task.displayDescription, // 🟢 Renders notes without revealing the technical tag bracket prefix
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
  ),

// 2. Inside the right-side layout details segment column area badge:
Container(
  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
  margin: const EdgeInsets.only(bottom: 6),
  decoration: BoxDecoration(
    color: isDark 
        ? Colors.white.withOpacity(0.1) 
        : Colors.black.withOpacity(0.05),
    borderRadius: BorderRadius.circular(4),
  ),
  child: Text(
    task.displayCategory, // 🟢 Nicely displays 'Personal' or 'Studium' on your card rows
    style: TextStyle(
      fontSize: 10,
      fontWeight: FontWeight.w600,
      color: isDark ? Colors.grey[300] : Colors.grey[700],
    ),
  ),
),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: priorityColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'P${task.priority}',
                      style: TextStyle(
                          color: priorityColor,
                          fontSize: 11,
                          fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Search Delegate ───────────────────────────────────────────────────────────

class _TaskSearchDelegate extends SearchDelegate<String> {
  final List<Task> tasks;
  _TaskSearchDelegate({required this.tasks});

  @override
  List<Widget> buildActions(BuildContext context) =>
      [IconButton(icon: const Icon(Icons.clear), onPressed: () => query = '')];

  @override
  Widget buildLeading(BuildContext context) =>
      IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => close(context, ''));

  @override
  Widget buildResults(BuildContext context) => _buildList(context);

  @override
  Widget buildSuggestions(BuildContext context) => _buildList(context);

  Widget _buildList(BuildContext context) {
    final filtered = tasks
        .where((t) => t.title.toLowerCase().contains(query.toLowerCase()))
        .toList();
    return ListView.builder(
      itemCount: filtered.length,
      itemBuilder: (_, i) => ListTile(
        title: Text(filtered[i].title),
        subtitle: Text('Prio: P${filtered[i].priority} | Kategorie: ${filtered[i].spaceType ?? 'Personal'}'),
        leading: const Icon(Icons.task_alt),
        onTap: () {
          close(context, filtered[i].title);
        },
      ),
    );
  }
}