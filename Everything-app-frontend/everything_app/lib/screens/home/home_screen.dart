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

// Stitch Design System: "The Digital Curator"
const _surfaceColor = Color(0xFFFCF9F8);
const _surfaceContainerLow = Color(0xFFF6F3F2);
const _surfaceContainerLowest = Color(0xFFFFFFFF);
const _onSurface = Color(0xFF323232);
const _onSurfaceVariant = Color(0xFF5F5F5F);
const _primary = Color(0xFF4F4CCD);
// const _primaryDim = Color(0xFF423FC0);
const _outlineVariant = Color(0xFFB3B2B1);

final _cardShadow = BoxShadow(
  color: _onSurface.withValues(alpha: 0.04),
  blurRadius: 32,
  offset: const Offset(0, 8),
);

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
    final taskProvider = context.read<TaskProvider>();
    final calendarProvider = context.read<CalendarProvider>();
    await taskProvider.loadTasks();
    await calendarProvider.loadEventsForMonth(DateTime.now());
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
    final auth = context.watch<AuthProvider>();
    final tasks = context.watch<TaskProvider>();
    final calendar = context.watch<CalendarProvider>();
    final now = DateTime.now();
    final todayEvents = calendar.getEventsForDay(now);

    return Scaffold(
      backgroundColor: _surfaceContainerLow, // Soft minimal background
      body: RefreshIndicator(
        onRefresh: _loadData,
        color: _primary,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // Custom Editorial AppBar
            SliverAppBar(
              backgroundColor: _surfaceContainerLow,
              expandedHeight: 140,
              floating: false,
              pinned: true,
              elevation: 0,
              flexibleSpace: FlexibleSpaceBar(
                titlePadding: const EdgeInsets.only(left: 24, bottom: 16, right: 24),
                title: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      DateFormat('EEEE, d. MMMM', 'de_DE').format(now).toUpperCase(),
                      style: GoogleFonts.inter(
                        color: _onSurfaceVariant,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${_getGreeting()}, ${auth.username ?? 'User'}',
                      style: GoogleFonts.manrope(
                        color: _onSurface,
                        fontWeight: FontWeight.w800,
                        fontSize: 22,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ],
                ),
              ),
              actions: [
                Container(
                  margin: const EdgeInsets.only(right: 16),
                  decoration: BoxDecoration(
                    color: _surfaceContainerLowest,
                    shape: BoxShape.circle,
                    boxShadow: [_cardShadow],
                  ),
                  child: IconButton(
                    icon: const Icon(Icons.notifications_none, color: _onSurface),
                    onPressed: () {},
                    iconSize: 20,
                  ),
                ),
              ],
            ),

            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  
                  // Today's Schedule Section
                  _SectionTitle(title: "Today's Schedule"),
                  const SizedBox(height: 16),
                  if (todayEvents.isEmpty)
                    const _EmptyState(message: 'Keine Termine heute.')
                  else
                    ...todayEvents.take(4).map((e) => _EventItem(event: e)),

                  const SizedBox(height: 40),

                  // Pending Tasks Section
                  _SectionTitle(title: "Ausstehende Aufgaben",
                      action: IconButton(
                        icon: const Icon(Icons.arrow_forward_ios, size: 14, color: _onSurfaceVariant),
                        onPressed: () => context.go('/tasks'),
                      )),
                  const SizedBox(height: 16),
                  if (tasks.isLoading)
                    const Center(child: CircularProgressIndicator(color: _primary))
                  else if (tasks.todoTasks.isEmpty)
                    const _EmptyState(message: 'Alles erledigt!')
                  else
                    ...tasks.todoTasks.take(4).map((t) => _TaskItem(task: t)),

                  const SizedBox(height: 40),

                  // Spaces Quick Access Section
                  _SectionTitle(title: "Schnellzugriff auf Spaces"),
                  const SizedBox(height: 16),
                  const _SpacesGrid(),

                  const SizedBox(height: 80), // Bottom padding
                ]),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.go('/create'),
        backgroundColor: _primary,
        foregroundColor: _surfaceContainerLowest,
        elevation: 8,
        child: const Icon(Icons.add),
      ),
    );
  }
}

// ─── Section Title (Manrope, Editorial) ────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String title;
  final Widget? action;

  const _SectionTitle({required this.title, this.action});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: GoogleFonts.manrope(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: _onSurface,
            letterSpacing: -0.3,
          ),
        ),
        if (action != null) action,
      ],
    );
  }
}

// ─── Event Item (Soft Minimalism) ──────────────────────────────────────────────

class _EventItem extends StatelessWidget {
  final CalendarEvent event;

  const _EventItem({required this.event});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16), // Round 4
        boxShadow: [_cardShadow],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Timeline indicator
          Container(
            width: 4,
            height: 48,
            decoration: BoxDecoration(
              color: event.colorObject.withValues(alpha: 0.8),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.title,
                  style: GoogleFonts.manrope(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: _onSurface,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${DateFormat('HH:mm').format(event.startTime)} - ${DateFormat('HH:mm').format(event.endTime)}',
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: _onSurfaceVariant,
                    fontWeight: FontWeight.w500,
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

// ─── Task Item (Ghost Border on interaction / subtle layering) ─────────────────

class _TaskItem extends StatelessWidget {
  final Task task;

  const _TaskItem({required this.task});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _surfaceColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _outlineVariant.withValues(alpha: 0.2), width: 1),
      ),
      child: Row(
        children: [
          // Custom soft checkbox
          GestureDetector(
            onTap: () => context.read<TaskProvider>().completeTask(task.id!),
            child: Container(
              width: 24,
              height: 24,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: task.isCompleted ? _primary : _outlineVariant,
                  width: 1.5,
                ),
                color: task.isCompleted ? _primary : Colors.transparent,
              ),
              child: task.isCompleted
                  ? const Icon(Icons.check, size: 14, color: _surfaceContainerLowest)
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
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: task.isCompleted ? _onSurfaceVariant : _onSurface,
                    decoration: task.isCompleted ? TextDecoration.lineThrough : null,
                  ),
                ),
                if (task.deadline != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    'Bis ${DateFormat('HH:mm').format(task.deadline!)}',
                    style: GoogleFonts.inter(
                      fontSize: 12,
                      color: task.isOverdue ? Colors.redAccent : _onSurfaceVariant,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ]
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Spaces Grid ──────────────────────────────────────────────────────────────

class _SpacesGrid extends StatelessWidget {
  const _SpacesGrid();

  static const spaces = [
    _SpaceData(icon: Icons.school_outlined, title: 'Study', color: Colors.blueAccent, route: '/study'),
    _SpaceData(icon: Icons.account_balance_wallet_outlined, title: 'Finances', color: Colors.orangeAccent, route: '/finance'),
    _SpaceData(icon: Icons.fitness_center_outlined, title: 'Gym', color: Colors.purpleAccent, route: '/sports'),
    _SpaceData(icon: Icons.restaurant_menu_outlined, title: 'Recipes', color: Colors.greenAccent, route: '/recipes'),
  ];

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: EdgeInsets.zero,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 16,
        crossAxisSpacing: 16,
        childAspectRatio: 1.1,
      ),
      itemCount: spaces.length,
      itemBuilder: (context, index) {
        final space = spaces[index];
        return GestureDetector(
          onTap: () => context.go(space.route),
          child: Container(
            decoration: BoxDecoration(
              color: _surfaceContainerLowest,
              borderRadius: BorderRadius.circular(20),
              boxShadow: [_cardShadow],
            ),
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: space.color.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(space.icon, color: space.color, size: 24),
                ),
                Text(
                  space.title,
                  style: GoogleFonts.manrope(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: _onSurface,
                  ),
                ),
              ],
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
  final Color color;
  final String route;
  const _SpaceData({required this.icon, required this.title, required this.color, required this.route});
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
      decoration: BoxDecoration(
        color: _surfaceColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _outlineVariant.withValues(alpha: 0.1)),
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