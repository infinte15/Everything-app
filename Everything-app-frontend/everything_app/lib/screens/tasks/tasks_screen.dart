import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../providers/task_provider.dart';
import '../../config/app_theme.dart';
import '../../models/task.dart';
import '../../widgets/create_task_sheet.dart';

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
  final String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<TaskProvider>().loadTasks();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tasksProvider = context.watch<TaskProvider>();
    
    // Filter tasks based on spaceType if provided
    final todo = _applySpaceFilter(tasksProvider.todoTasks);
    final inProgress = _applySpaceFilter(tasksProvider.inProgressTasks);
    final completed = _applySpaceFilter(tasksProvider.completedTasks);

    return Scaffold(
      appBar: AppBar(
        leading: const BackButton(),
        title: Text(widget.title),
        bottom: TabBar(
          controller: _tabController,
          tabs: [
            Tab(text: 'Offen (${todo.length})'),
            Tab(text: 'Aktiv (${inProgress.length})'),
            Tab(text: 'Fertig (${completed.length})'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () => _showSearch(context, todo + inProgress + completed),
          ),
        ],
      ),
      body: tasksProvider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                _TaskList(tasks: _filterTasks(todo)),
                _TaskList(tasks: _filterTasks(inProgress)),
                _TaskList(tasks: _filterTasks(completed)),
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

  List<Task> _applySpaceFilter(List<Task> tasks) {
    if (widget.spaceType == null) return tasks;
    return tasks.where((t) => t.spaceType == widget.spaceType).toList();
  }

  List<Task> _filterTasks(List<Task> tasks) {
    if (_searchQuery.isEmpty) return tasks;
    return tasks
        .where((t) =>
            t.title.toLowerCase().contains(_searchQuery.toLowerCase()))
        .toList();
  }

  void _showSearch(BuildContext context, List<Task> filteredTasks) {
    showSearch(
      context: context,
      delegate: _TaskSearchDelegate(
          tasks: filteredTasks),
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
              // Priority Bar
              Container(
                width: 4,
                height: 50,
                decoration: BoxDecoration(
                  color: priorityColor,
                  borderRadius: BorderRadius.circular(4),
                ),
              ),
              const SizedBox(width: 12),

              // Checkbox
              Checkbox(
                value: task.isCompleted,
                onChanged: (_) =>
                    context.read<TaskProvider>().completeTask(task.id!),
                shape: const CircleBorder(),
              ),

              // Content
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

              // Priority Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: priorityColor.withValues(alpha: 0.1),
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
        subtitle: Text(filtered[i].status),
        leading: const Icon(Icons.task_alt),
        onTap: () => close(context, filtered[i].title),
      ),
    );
  }
}