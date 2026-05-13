import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../providers/habit_provider.dart';
import '../../models/habit.dart';
import '../../widgets/create_habit_sheet.dart';

class HabitsScreen extends StatefulWidget {
  final String title;

  const HabitsScreen({
    super.key,
    this.title = 'Gewohnheiten',
  });

  @override
  State<HabitsScreen> createState() => _HabitsScreenState();
}

class _HabitsScreenState extends State<HabitsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<HabitProvider>().loadHabits();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final habitProvider = context.watch<HabitProvider>();
    final allHabits = habitProvider.habits;

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
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: const Color(0xFF5856D6),
          labelColor: const Color(0xFF5856D6),
          unselectedLabelColor: Colors.grey,
          labelStyle: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 13),
          tabs: const [
            Tab(text: 'Aktiv'),
            Tab(text: 'Verlauf'),
          ],
        ),
      ),
      body: habitProvider.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : TabBarView(
              controller: _tabController,
              children: [
                _buildHabitList(allHabits),
                _buildHabitList([]),
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
            builder: (ctx) => const CreateHabitSheet(),
          );
        },
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }

  Widget _buildHabitList(List<Habit> list) {
    if (list.isEmpty) {
      return Center(
        child: Text(
          'Keine Gewohnheiten vorhanden',
          style: GoogleFonts.manrope(color: Colors.grey, fontSize: 14),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      itemCount: list.length,
      itemBuilder: (ctx, index) {
        return _HabitTile(habit: list[index]);
      },
    );
  }
}

class _HabitTile extends StatelessWidget {
  final Habit habit;
  const _HabitTile({required this.habit});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    String habitTitle = 'Unbenannte Gewohnheit';
    try {
      habitTitle = (habit as dynamic).title ?? (habit as dynamic).name ?? 'Gewohnheit';
    } catch (_) {}

    String habitSub = 'Aktiv';
    try {
      final currentStreak = (habit as dynamic).streak ?? (habit as dynamic).count ?? 0;
      habitSub = 'Streak: $currentStreak Tage';
    } catch (_) {}

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF131313) : Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: isDark ? const Color(0xFF222222) : const Color(0xFFE8EAF0),
          width: 0.7,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween, 
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  habitTitle,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: isDark ? Colors.white : Colors.black87,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  habitSub,
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.check_box_outline_blank, color: Color(0xFF5856D6)),
              onPressed: () {
                // Hier kannst du später deine Logik einbauen
              },
            ),
          ],
        ),
      ),
    );
  }
}