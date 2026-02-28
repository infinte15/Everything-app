
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../providers/auth_provider.dart';
import '../../providers/task_provider.dart';
import '../../providers/calendar_provider.dart';
import '../../config/app_theme.dart';
import '../../models/task.dart';
import '../../models/calendar_event.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadData());
  }

  Future<void> _loadData() async {
    await context.read<TaskProvider>().loadTasks();
    await context.read<CalendarProvider>().loadEventsForMonth(DateTime.now());
  }

  String _getGreeting() {
    final hour = DateTime.now().hour;
    if (hour < 12) return 'Guten Morgen';
    if (hour < 17) return 'Guten Tag';
    if (hour < 21) return 'Guten Abend';
    return 'Gute Nacht';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final auth = context.watch<AuthProvider>();
    final tasks = context.watch<TaskProvider>();
    final calendar = context.watch<CalendarProvider>();
    final now = DateTime.now();
    final todayEvents = calendar.getEventsForDay(now);

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      body: RefreshIndicator(
        onRefresh: _loadData,
        child: CustomScrollView(
          slivers: [
            // App Bar
            SliverAppBar(
              expandedHeight: 180,
              floating: false,
              pinned: true,
              elevation: 0,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        AppTheme.primaryColor,
                        AppTheme.primaryColor.withOpacity(0.8),
                      ],
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Text(
                            '${_getGreeting()}, ${auth.username ?? 'User'}! 👋',
                            style: theme.textTheme.headlineSmall?.copyWith(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            DateFormat('EEEE, dd. MMMM yyyy', 'de_DE')
                                .format(now),
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: Colors.white70,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              actions: [
                IconButton(
                  icon: const Icon(Icons.notifications_outlined,
                      color: Colors.white),
                  onPressed: () {},
                ),
                IconButton(
                  icon: const Icon(Icons.settings_outlined,
                      color: Colors.white),
                  onPressed: () {},
                ),
              ],
            ),

            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  // Stats Row
                  _StatsRow(
                    totalTasks: tasks.todoTasks.length,
                    todayEvents: todayEvents.length,
                    overdueTasks: tasks.overdueTasks.length,
                  ),
                  const SizedBox(height: 24),

                  // Quick Actions
                  _SectionHeader(
                    title: 'Schnellzugriff',
                    onSeeAll: null,
                  ),
                  const SizedBox(height: 12),
                  _QuickActionsGrid(),
                  const SizedBox(height: 24),

                  // Today's Tasks
                  _SectionHeader(
                    title: "Heutige Aufgaben",
                    onSeeAll: () => context.go('/tasks'),
                  ),
                  const SizedBox(height: 12),
                  if (tasks.isLoading)
                    const Center(child: CircularProgressIndicator())
                  else if (tasks.todayTasks.isEmpty)
                    _EmptyState(
                      icon: Icons.task_alt,
                      message: 'Keine Aufgaben für heute!',
                    )
                  else
                    ...tasks.todayTasks
                        .take(5)
                        .map((t) => _TaskCard(task: t)),
                  const SizedBox(height: 24),

                  // Today's Events
                  _SectionHeader(
                    title: "Heutige Events",
                    onSeeAll: () => context.go('/calendar'),
                  ),
                  const SizedBox(height: 12),
                  if (todayEvents.isEmpty)
                    _EmptyState(
                      icon: Icons.event_available,
                      message: 'Keine Events heute',
                    )
                  else
                    ...todayEvents
                        .take(3)
                        .map((e) => _EventCard(event: e)),
                  const SizedBox(height: 24),

                  // Spaces Overview
                  _SectionHeader(
                    title: 'Meine Spaces',
                    onSeeAll: () => context.go('/spaces'),
                  ),
                  const SizedBox(height: 12),
                  _SpacesRow(),
                  const SizedBox(height: 32),
                ]),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go('/create'),
        icon: const Icon(Icons.add),
        label: const Text('Neu'),
      ),
    );
  }
}

// ─── Stat Row ─────────────────────────────────────────────────────────────────

class _StatsRow extends StatelessWidget {
  final int totalTasks;
  final int todayEvents;
  final int overdueTasks;

  const _StatsRow({
    required this.totalTasks,
    required this.todayEvents,
    required this.overdueTasks,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _StatCard(
            label: 'Offene Tasks',
            value: '$totalTasks',
            icon: Icons.checklist,
            color: AppTheme.tasksColor,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _StatCard(
            label: 'Events heute',
            value: '$todayEvents',
            icon: Icons.event,
            color: AppTheme.primaryColor,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _StatCard(
            label: 'Überfällig',
            value: '$overdueTasks',
            icon: Icons.warning_amber,
            color: overdueTasks > 0 ? Colors.red : Colors.green,
          ),
        ),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _StatCard({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(height: 8),
          Text(value,
              style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold, color: color)),
          Text(label,
              style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurface.withOpacity(0.6))),
        ],
      ),
    );
  }
}

// ─── Section Header ────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  final VoidCallback? onSeeAll;

  const _SectionHeader({required this.title, this.onSeeAll});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(title,
            style: theme.textTheme.titleMedium
                ?.copyWith(fontWeight: FontWeight.bold)),
        if (onSeeAll != null)
          TextButton(onPressed: onSeeAll, child: const Text('Alle anzeigen')),
      ],
    );
  }
}

// ─── Quick Actions ─────────────────────────────────────────────────────────────

class _QuickActionsGrid extends StatelessWidget {
  final List<_QuickAction> actions = const [
    _QuickAction(icon: Icons.schedule, label: 'Auto-Plan',
        route: '/calendar', color: Color(0xFF6366F1)),
    _QuickAction(icon: Icons.book, label: 'Studium',
        route: '/study', color: Color(0xFF3B82F6)),
    _QuickAction(icon: Icons.fitness_center, label: 'Sport',
        route: '/sports', color: Color(0xFF8B5CF6)),
    _QuickAction(icon: Icons.restaurant, label: 'Rezepte',
        route: '/recipes', color: Color(0xFF10B981)),
  ];

  const _QuickActionsGrid();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: actions.map((a) {
        return Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 8),
            child: InkWell(
              onTap: () => context.go(a.route),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: BoxDecoration(
                  color: a.color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: a.color.withOpacity(0.2)),
                ),
                child: Column(
                  children: [
                    Icon(a.icon, color: a.color, size: 28),
                    const SizedBox(height: 8),
                    Text(a.label,
                        style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: a.color),
                        textAlign: TextAlign.center),
                  ],
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}

class _QuickAction {
  final IconData icon;
  final String label;
  final String route;
  final Color color;
  const _QuickAction(
      {required this.icon,
      required this.label,
      required this.route,
      required this.color});
}

// ─── Task Card ─────────────────────────────────────────────────────────────────

class _TaskCard extends StatelessWidget {
  final Task task;
  const _TaskCard({required this.task});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final priorityColor = AppTheme.getPriorityColor(task.priority);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceVariant.withOpacity(0.5),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: theme.colorScheme.outline.withOpacity(0.1)),
      ),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 40,
            decoration: BoxDecoration(
              color: priorityColor,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(task.title,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis),
                if (task.deadline != null)
                  Text(
                    DateFormat('HH:mm').format(task.deadline!),
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: task.isOverdue ? Colors.red : null),
                  ),
              ],
            ),
          ),
          Checkbox(
            value: task.isCompleted,
            onChanged: (_) => context.read<TaskProvider>().completeTask(task.id!),
          ),
        ],
      ),
    );
  }
}

// ─── Event Card ────────────────────────────────────────────────────────────────

class _EventCard extends StatelessWidget {
  final CalendarEvent event;
  const _EventCard({required this.event});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: event.colorObject.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: event.colorObject.withOpacity(0.3)),
      ),
      child: Row(
        children: [
          Icon(Icons.circle, size: 10, color: event.colorObject),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(event.title,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis),
                Text(
                  '${DateFormat('HH:mm').format(event.startTime)} - '
                  '${DateFormat('HH:mm').format(event.endTime)}',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Spaces Row ───────────────────────────────────────────────────────────────

class _SpacesRow extends StatelessWidget {
  final List<_SpaceItem> spaces = const [
    _SpaceItem(icon: Icons.school, label: 'Studium', route: '/study',
        color: Color(0xFF3B82F6)),
    _SpaceItem(icon: Icons.fitness_center, label: 'Sport', route: '/sports',
        color: Color(0xFF8B5CF6)),
    _SpaceItem(icon: Icons.task_alt, label: 'Tasks', route: '/tasks',
        color: Color(0xFFF97316)),
    _SpaceItem(icon: Icons.restaurant_menu, label: 'Rezepte', route: '/recipes',
        color: Color(0xFF10B981)),
    _SpaceItem(icon: Icons.account_balance_wallet, label: 'Finanzen',
        route: '/finance', color: Color(0xFFEAB308)),
  ];

  const _SpacesRow();

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: spaces.map((s) {
          return Padding(
            padding: const EdgeInsets.only(right: 12),
            child: InkWell(
              onTap: () => context.go(s.route),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                width: 80,
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: BoxDecoration(
                  color: s.color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: s.color.withOpacity(0.2)),
                ),
                child: Column(
                  children: [
                    Icon(s.icon, color: s.color, size: 32),
                    const SizedBox(height: 8),
                    Text(s.label,
                        style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: s.color),
                        textAlign: TextAlign.center),
                  ],
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _SpaceItem {
  final IconData icon;
  final String label;
  final String route;
  final Color color;
  const _SpaceItem(
      {required this.icon,
      required this.label,
      required this.route,
      required this.color});
}

// ─── Empty State ───────────────────────────────────────────────────────────────

class _EmptyState extends StatelessWidget {
  final IconData icon;
  final String message;
  const _EmptyState({required this.icon, required this.message});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceVariant.withOpacity(0.3),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: Colors.grey, size: 24),
          const SizedBox(width: 12),
          Text(message,
              style: theme.textTheme.bodyMedium
                  ?.copyWith(color: Colors.grey)),
        ],
      ),
    );
  }
}