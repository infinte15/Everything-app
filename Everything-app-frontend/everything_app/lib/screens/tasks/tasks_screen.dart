import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import 'package:google_fonts/google_fonts.dart';
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
  
  // Filter & Sort State Options
  String _selectedCategoryFilter = 'All'; // 'All', 'Personal', 'Studium'
  String _selectedSortOption = 'datum_ab'; // 'datum_ab', 'datum_auf', 'prio_auf', 'prio_ab'

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<TaskProvider>().loadTasks();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  // Filter- und Sortier-Pipeline für Tasks
  List<Task> _processTasksPipeline(List<Task> rawTasks) {
    // 1. Nach spaceType filtern falls vorhanden
    List<Task> filtered = widget.spaceType == null
        ? List.from(rawTasks)
        : rawTasks.where((t) => t.spaceType == widget.spaceType).toList();

    // 2. Nach unserer Beschreibungskategorie filtern
    if (_selectedCategoryFilter != 'All') {
      filtered = filtered.where((t) => t.category == _selectedCategoryFilter).toList();
    }

    // 3. Sortierung anwenden
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

  void _showFilterSortMenu() {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF131313),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setModalState) {
            return Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Filtern & Sortieren',
                        style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.grey),
                        onPressed: () => Navigator.pop(context),
                      )
                    ],
                  ),
                  const Divider(color: Color(0xFF222222)),
                  const SizedBox(height: 8),
                  Text(
                    'Kategorie',
                    style: GoogleFonts.manrope(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: ['All', 'Personal', 'Studium'].map((cat) {
                      final isSelected = _selectedCategoryFilter == cat;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8.0),
                        child: ChoiceChip(
                          backgroundColor: const Color(0xFF1E1E1E),
                          selectedColor: const Color(0xFF5856D6),
                          labelStyle: GoogleFonts.manrope(
                            color: isSelected ? Colors.white : Colors.grey,
                            fontSize: 13,
                            fontWeight: FontWeight.w600
                          ),
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
                  const SizedBox(height: 16),
                  Text(
                    'Sortieren nach',
                    style: GoogleFonts.manrope(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _buildSortChip(setModalState, 'datum_ab', 'Datum: Späteste zuerst'),
                      _buildSortChip(setModalState, 'datum_auf', 'Datum: Früheste zuerst'),
                      _buildSortChip(setModalState, 'prio_ab', 'Prio: Hoch → Niedrig'),
                      _buildSortChip(setModalState, 'prio_auf', 'Prio: Niedrig → Hoch'),
                    ],
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildSortChip(StateSetter setModalState, String option, String label) {
    final isSelected = _selectedSortOption == option;
    return ChoiceChip(
      backgroundColor: const Color(0xFF1E1E1E),
      selectedColor: const Color(0xFF5856D6),
      labelStyle: GoogleFonts.manrope(
        color: isSelected ? Colors.white : Colors.grey,
        fontSize: 12,
        fontWeight: FontWeight.w500
      ),
      label: Text(label),
      selected: isSelected,
      onSelected: (bool selected) {
        if (selected) {
          setModalState(() => _selectedSortOption = option);
          setState(() => _selectedSortOption = option);
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final taskProvider = context.watch<TaskProvider>();

    final filteredTodo = _processTasksPipeline(taskProvider.todoTasks);
    final filteredCompleted = _processTasksPipeline(taskProvider.completedTasks);

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313),
        elevation: 0,
        leading: const BackButton(color: Color(0xFFC2C1FF)),
        title: Text(
          widget.title,
          style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 20),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: Colors.grey),
            onPressed: () {
              showSearch(
                context: context,
                delegate: _TaskSearchDelegate(tasks: taskProvider.tasks),
              );
            },
          ),
          IconButton(
            icon: Icon(
              Icons.filter_list,
              color: _selectedCategoryFilter != 'All' ? const Color(0xFF5856D6) : Colors.grey,
            ),
            onPressed: _showFilterSortMenu,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: const Color(0xFF5856D6),
          labelColor: const Color(0xFF5856D6),
          unselectedLabelColor: Colors.grey,
          labelStyle: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 13),
          tabs: [
            Tab(text: 'Offen (${filteredTodo.length})'),
            Tab(text: 'Erledigt (${filteredCompleted.length})'),
          ],
        ),
      ),
      body: taskProvider.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : TabBarView(
              controller: _tabController,
              children: [
                _buildTaskList(filteredTodo, isCompletedTab: false),
                _buildTaskList(filteredCompleted, isCompletedTab: true),
              ],
            ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: const Color(0xFF5856D6),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        onPressed: () {
          showModalBottomSheet(
            context: context,
            isScrollControlled: true,
            backgroundColor: Colors.transparent,
            builder: (ctx) => CreateTaskSheet(spaceType: widget.spaceType),
          );
        },
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }

  Widget _buildTaskList(List<Task> list, {required bool isCompletedTab}) {
    if (list.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.task_alt, size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            Text(
              'Keine Aufgaben vorhanden',
              style: GoogleFonts.manrope(color: Colors.grey, fontSize: 16),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: list.length,
      itemBuilder: (ctx, index) {
        return _TaskTile(task: list[index]);
      },
    );
  }
}

// ─── ORIGINAL TASK TILE FORMAT ─────────────────────────────────────────────────

class _TaskTile extends StatelessWidget {
  final Task task;
  const _TaskTile({required this.task});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final priorityColor = AppTheme.getPriorityColor(task.priority);

    final isCompleted = task.status == 'COMPLETED';

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
              // Die originale farbige Prioritäts-Leiste
              Container(
                width: 4,
                height: 50,
                decoration: BoxDecoration(
                  color: priorityColor,
                  borderRadius: BorderRadius.circular(4),
                ),
              ),
              const SizedBox(width: 12),

              // Die runde originale Checkbox
              Checkbox(
                value: isCompleted,
                onChanged: (_) => context.read<TaskProvider>().completeTask(task.id!),
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
                        decoration: isCompleted ? TextDecoration.lineThrough : null,
                        color: isCompleted ? Colors.grey : null,
                      ),
                    ),
                    if (task.description != null && task.description!.isNotEmpty)
                      Text(
                        task.description!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                      ),
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
                              fontWeight: task.isOverdue ? FontWeight.bold : FontWeight.normal,
                            ),
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (task.estimatedDurationMinutes > 0) ...[
                          const Icon(Icons.timer_outlined, size: 12, color: Colors.grey),
                          const SizedBox(width: 4),
                          Text(
                            '${task.estimatedDurationMinutes} Min.',
                            style: const TextStyle(fontSize: 11, color: Colors.grey),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
              
              const SizedBox(width: 8),

              // Rechter Infoblock (Kategorie-Badge & Prioritäts-Kästchen)
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    margin: const EdgeInsets.only(bottom: 6),
                    decoration: BoxDecoration(
                      color: isDark ? Colors.white.withOpacity(0.1) : Colors.black.withOpacity(0.05),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      task.category,
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
                      style: TextStyle(color: priorityColor, fontSize: 11, fontWeight: FontWeight.bold),
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
        subtitle: Text('Prio: P${filtered[i].priority} | Kategorie: ${filtered[i].category}'),
        leading: const Icon(Icons.task_alt),
        onTap: () {
          close(context, filtered[i].title);
        },
      ),
    );
  }
}