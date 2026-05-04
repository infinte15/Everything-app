import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../providers/auth_provider.dart';
import '../../providers/task_provider.dart';
import '../../providers/calendar_provider.dart';
import '../../models/task.dart';
import '../../models/calendar_event.dart';

// Stitch Design System: "Kinetic Mono"
const _backgroundColor = Color(0xFF121212);
const _surfaceColor = Color(0xFF1E1E1E);
const _onSurface = Color(0xFFF5F5F5);
const _onSurfaceVariant = Color(0xFFA0A0A0);
const _primary = Color(0xFF5856D6);
const _primaryLight = Color(0xFF9896FF); // Lighter accent for icons
const _outlineVariant = Color(0xFF333333);

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  DateTime _selectedDate = DateTime.now();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadData());
  }

  Future<void> _loadData() async {
    final taskProvider = context.read<TaskProvider>();
    final calendarProvider = context.read<CalendarProvider>();
    await taskProvider.loadTasks();
    await calendarProvider.loadEventsForMonth(DateTime.now());
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final tasks = context.watch<TaskProvider>();
    final calendar = context.watch<CalendarProvider>();
    final now = DateTime.now();
    final todayEvents = calendar.getEventsForDay(_selectedDate);

    return Scaffold(
      backgroundColor: _backgroundColor,
      body: RefreshIndicator(
        onRefresh: _loadData,
        color: _primary,
        backgroundColor: _surfaceColor,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // Kinetic Mono Custom App Bar
            SliverAppBar(
              backgroundColor: _backgroundColor,
              expandedHeight: 80,
              floating: true,
              pinned: true,
              elevation: 0,
              leading: IconButton(
                icon: const Icon(Icons.search, color: _primary),
                onPressed: () {},
              ),
              title: Text(
                'KINETIC MONO',
                style: GoogleFonts.manrope(
                  color: _onSurface,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 2.0,
                  fontSize: 18,
                ),
              ),
              centerTitle: true,
              actions: [
                Padding(
                  padding: const EdgeInsets.only(right: 16.0),
                  child: CircleAvatar(
                    radius: 16,
                    backgroundColor: _surfaceColor,
                    child: Text(
                      auth.username?.isNotEmpty == true ? auth.username![0].toUpperCase() : 'U',
                      style: GoogleFonts.inter(color: _onSurface, fontSize: 14, fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ],
            ),

            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  
                  // Today's Schedule Section
                  _SectionTitle(
                    title: "TODAY'S SCHEDULE",
                    rightLabel: DateFormat('E d').format(now).toUpperCase(),
                  ),
                  const SizedBox(height: 24),
                  _HorizontalDateSelector(  selectedDate: _selectedDate, onDateSelected: (date) => setState(() => _selectedDate = date),),
                  const SizedBox(height: 24),
                  if (todayEvents.isEmpty)
                    const _EmptyState(message: 'Keine Termine heute.')
                  else
                    ...todayEvents.take(4).map((e) => _EventItem(event: e)),

                  const SizedBox(height: 48),

                  // Pending Tasks Section
                  _SectionTitle(
                    title: "AUSSTEHENDE AUFGABEN",
                    rightAction: GestureDetector(
                      onTap: () => context.go('/tasks'),
                      child: const Icon(Icons.arrow_forward_ios, size: 12, color: _onSurfaceVariant),
                    ),
                  ),
                  const SizedBox(height: 24),
                  if (tasks.isLoading)
                    const Center(child: CircularProgressIndicator(color: _primary))
                  else if (tasks.todoTasks.isEmpty)
                    const _EmptyState(message: 'Alles erledigt!')
                  else
                    ...tasks.todoTasks.take(4).map((t) => _TaskItem(task: t)),

                  const SizedBox(height: 48),

                  // Spaces Quick Access Section
                  _SectionTitle(
                    title: "SCHNELLZUGRIFF AUF SPACES",
                    rightLabel: "SEE ALL",
                    onRightLabelTap: () => context.go('/spaces'),
                  ),
                  const SizedBox(height: 24),
                  const _SpacesHorizontalList(),

                  const SizedBox(height: 80), // Bottom padding
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Section Title (Uppercase, Wide Spacing) ──────────────────────────────────
class _SectionTitle extends StatelessWidget {
  final String title;
  final String? rightLabel;
  final Widget? rightAction;
  final VoidCallback? onRightLabelTap;

  const _SectionTitle({required this.title, this.rightLabel, this.rightAction, this.onRightLabelTap});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: GoogleFonts.manrope(
            fontSize: 12,
            fontWeight: FontWeight.w800,
            color: _onSurfaceVariant,
            letterSpacing: 2.0,
          ),
        ),
        if (rightLabel != null)
          GestureDetector(
            onTap: onRightLabelTap,
            child: Text(
              rightLabel!,
              style: GoogleFonts.manrope(
                fontSize: 10,
                fontWeight: FontWeight.w800,
                color: _onSurface,
                letterSpacing: 1.5,
              ),
            ),
          ),
        if (rightAction != null) rightAction!,
      ],
    );
  }
}

class _HorizontalDateSelector extends StatefulWidget {
  final DateTime selectedDate;
  final ValueChanged<DateTime> onDateSelected;

  const _HorizontalDateSelector({
    required this.selectedDate,
    required this.onDateSelected,
  });

  @override
  State<_HorizontalDateSelector> createState() => _HorizontalDateSelectorState();
}

class _HorizontalDateSelectorState extends State<_HorizontalDateSelector> {
  int? _hoveredIndex;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final dates = List.generate(
      7, (i) => now.subtract(Duration(days: now.weekday - 1 - i)),
    );

    return Row(
      children: List.generate(7, (index) {
        final date = dates[index];
        final isSelected = date.day == widget.selectedDate.day &&
            date.month == widget.selectedDate.month &&
            date.year == widget.selectedDate.year;
        final isHovered = _hoveredIndex == index && !isSelected;

        return Expanded(
          child: MouseRegion(
            cursor: SystemMouseCursors.click,
            onEnter: (_) => setState(() => _hoveredIndex = index),
            onExit: (_) => setState(() => _hoveredIndex = null),
            child: GestureDetector(
              onTap: () => widget.onDateSelected(date),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding: EdgeInsets.symmetric(vertical: isSelected ? 12 : 8),
                color: isSelected
                    ? _primary
                    : isHovered
                        ? _primary.withOpacity(0.15)
                        : Colors.transparent,
                child: Opacity(
                  opacity: isSelected ? 1.0 : isHovered ? 0.7 : 0.4,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        DateFormat('E').format(date).substring(0, 3).toUpperCase(),
                        style: GoogleFonts.manrope(
                          fontSize: 10,
                          fontWeight: isSelected ? FontWeight.w900 : FontWeight.w700,
                          color: isSelected ? Colors.white : _onSurface,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '${date.day}',
                        style: GoogleFonts.manrope(
                          fontSize: isSelected ? 18 : 14,
                          fontWeight: isSelected ? FontWeight.w900 : FontWeight.w700,
                          color: isSelected ? Colors.white : _onSurface,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }
}
// ─── Event Item (Brutalist blocks) ──────────────────────────────────────────────
class _EventItem extends StatelessWidget {
  final CalendarEvent event;

  const _EventItem({required this.event});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Time Column
          SizedBox(
            width: 48,
            child: Text(
              DateFormat('HH:mm').format(event.startTime),
              style: GoogleFonts.inter(
                fontSize: 10,
                fontWeight: FontWeight.w800,
                color: _onSurface,
              ),
            ),
          ),
          const SizedBox(width: 8),
          // Event Card
          Expanded(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
              decoration: const BoxDecoration(
                color: _surfaceColor,
                borderRadius: BorderRadius.zero, // Brutalist sharp corners
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    event.title,
                    style: GoogleFonts.inter(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: _onSurface,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    (event.description?.isNotEmpty == true) ? event.description! : 'Ongoing • 45m left', // Fallback context
                    style: GoogleFonts.inter(
                      fontSize: 12,
                      color: _primaryLight, // Using lighter primary for subtle metadata
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Task Item (Square checkbox) ───────────────────────────────────────────────
class _TaskItem extends StatelessWidget {
  final Task task;

  const _TaskItem({required this.task});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: _outlineVariant, width: 1)),
      ),
      child: Row(
        children: [
          // Custom sharp checkbox
          GestureDetector(
            onTap: () => context.read<TaskProvider>().completeTask(task.id!),
            child: Container(
              width: 18,
              height: 18,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.zero,
                border: Border.all(
                  color: task.isCompleted ? _primary : _outlineVariant,
                  width: 1.5,
                ),
                color: task.isCompleted ? _primary : Colors.transparent,
              ),
              child: task.isCompleted
                  ? const Icon(Icons.check, size: 14, color: _backgroundColor)
                  : null,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  task.title,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: task.isCompleted ? _onSurfaceVariant : _onSurface,
                    decoration: task.isCompleted ? TextDecoration.lineThrough : null,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Spaces Horizontal List ───────────────────────────────────────────────────
class _SpacesHorizontalList extends StatelessWidget {
  const _SpacesHorizontalList();

  static const spaces = [
    _SpaceData(icon: Icons.school, title: 'STUDY', subtitle: '12 RESOURCES', route: '/study'),
    _SpaceData(icon: Icons.account_balance_wallet, title: 'FINANCES', subtitle: 'UPDATED 2H AGO', route: '/finance'),
    _SpaceData(icon: Icons.fitness_center, title: 'GYM', subtitle: 'DAILY LOG', route: '/sports'),
    _SpaceData(icon: Icons.restaurant_menu, title: 'RECIPES', subtitle: '3 NEW', route: '/recipes'),
  ];

  @override
Widget build(BuildContext context) {
  return LayoutBuilder(
    builder: (context, constraints) {
      // Build a single card widget to avoid duplication
      Widget buildCard(int index) {
        final space = spaces[index];
        final isLast = index == spaces.length - 1;
        return GestureDetector(
          onTap: () => context.go(space.route),
          child: Container(
            margin: EdgeInsets.only(right: isLast ? 0 : 16),
            decoration: const BoxDecoration(color: _surfaceColor),
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(space.icon, color: _primaryLight, size: 28),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      space.title,
                      style: GoogleFonts.manrope(
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        color: _onSurface,
                        letterSpacing: 1.0,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      space.subtitle,
                      style: GoogleFonts.manrope(
                        fontSize: 8,
                        fontWeight: FontWeight.w600,
                        color: _onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      }

      // Wide screen: all cards fill available width equally
      if (constraints.maxWidth > 500) {
        return SizedBox(
          height: 180,
          child: Row(
            children: List.generate(
              spaces.length,
              (i) => Expanded(child: buildCard(i)),
            ),
          ),
        );
      }

      // Narrow screen: horizontal scroll with fixed card width
      return SizedBox(
        height: 180,
        child: ListView.builder(
          scrollDirection: Axis.horizontal,
          itemCount: spaces.length,
          itemBuilder: (context, index) => SizedBox(
            width: 140,
            child: buildCard(index),
          ),
        ),
      );
    },
  );
}
}

class _SpaceData {
  final IconData icon;
  final String title;
  final String subtitle;
  final String route;
  const _SpaceData({required this.icon, required this.title, required this.subtitle, required this.route});
}

// ─── Empty State ──────────────────────────────────────────────────────────────
class _EmptyState extends StatelessWidget {
  final String message;
  const _EmptyState({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 16),
      decoration: const BoxDecoration(
        color: _surfaceColor,
        borderRadius: BorderRadius.zero,
      ),
      child: Center(
        child: Text(
          message,
          style: GoogleFonts.inter(
            color: _onSurfaceVariant,
            fontStyle: FontStyle.italic,
          ),
        ),
      ),
    );
  }
}