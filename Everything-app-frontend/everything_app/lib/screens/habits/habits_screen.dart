import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import '../../providers/habit_provider.dart';
import '../../models/habit.dart';
import '../../widgets/create_habit_sheet.dart';
import 'package:fl_chart/fl_chart.dart';

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
            Tab(text: 'Tracker'),
          ],
        ),
      ),
      body: habitProvider.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : TabBarView(
              controller: _tabController,
              children: [
                _buildHabitList(allHabits),
                _buildTrackerHistory(allHabits), // 🟢 Der interaktive Habit-Tracker
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

  // ─── 🟢 TAB 2: HABIT TRACKER KALENDER (VERLAUF) ───────────────────────────
  Widget _buildTrackerHistory(List<Habit> habits) {
    if (habits.isEmpty) {
      return const Center(child: Text('Keine Daten für den Verlauf', style: TextStyle(color: Colors.grey)));
    }

    final today = DateTime.now();
    final List<DateTime> last14Days = List.generate(14, (index) => today.subtract(Duration(days: 13 - index)));
    
    // Compute data for chart
    List<FlSpot> spots = [];
    for (int i = 0; i < last14Days.length; i++) {
      final date = last14Days[i];
      final dateStr = DateFormat('yyyy-MM-dd').format(date);
      
      int completedCount = 0;
      for (var habit in habits) {
        if (habit.completedDates.contains(dateStr)) {
          completedCount++;
        }
      }
      
      double rate = habits.isEmpty ? 0 : (completedCount / habits.length);
      spots.add(FlSpot(i.toDouble(), rate));
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. CHART SECTION
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF131313),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF222222), width: 0.7),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Habit Tracker',
                  style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 24),
                ),
                const SizedBox(height: 20),
                SizedBox(
                  height: 200,
                  child: LineChart(
                    LineChartData(
                      gridData: FlGridData(
                        show: true,
                        drawVerticalLine: true,
                        getDrawingHorizontalLine: (value) => FlLine(color: const Color(0xFF222222), strokeWidth: 1),
                        getDrawingVerticalLine: (value) => FlLine(color: const Color(0xFF222222), strokeWidth: 1),
                      ),
                      titlesData: FlTitlesData(
                        show: true,
                        bottomTitles: AxisTitles(
                          sideTitles: SideTitles(
                            showTitles: true,
                            reservedSize: 22,
                            interval: 1,
                            getTitlesWidget: (value, meta) {
                              if (value.toInt() >= 0 && value.toInt() < last14Days.length) {
                                final date = last14Days[value.toInt()];
                                if (value.toInt() % 2 == 0) {
                                  return Padding(
                                    padding: const EdgeInsets.only(top: 8.0),
                                    child: Text(DateFormat('MMM dd').format(date), style: const TextStyle(color: Colors.grey, fontSize: 10)),
                                  );
                                }
                              }
                              return const SizedBox.shrink();
                            },
                          ),
                        ),
                        leftTitles: AxisTitles(
                          sideTitles: SideTitles(
                            showTitles: true,
                            interval: 0.2,
                            reservedSize: 28,
                            getTitlesWidget: (value, meta) {
                              return Text(value.toStringAsFixed(1), style: const TextStyle(color: Colors.grey, fontSize: 10));
                            },
                          ),
                        ),
                        topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                        rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                      ),
                      borderData: FlBorderData(show: false),
                      minX: 0,
                      maxX: 13,
                      minY: 0,
                      maxY: 1.0,
                      lineBarsData: [
                        LineChartBarData(
                          spots: spots,
                          isCurved: true,
                          color: const Color(0xFF5856D6),
                          barWidth: 2,
                          isStrokeCapRound: true,
                          dotData: FlDotData(
                            show: true,
                            getDotPainter: (spot, percent, barData, index) {
                              return FlDotCirclePainter(radius: 3, color: const Color(0xFFC2C1FF), strokeWidth: 1, strokeColor: const Color(0xFF5856D6));
                            },
                          ),
                          belowBarData: BarAreaData(
                            show: true,
                            color: const Color(0xFF5856D6).withOpacity(0.15),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 2. TODAY SECTION
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF131313),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF222222), width: 0.7),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.mood, color: Colors.grey, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      'Today',
                      style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: habits.map((habit) {
                      final todayStr = DateFormat('yyyy-MM-dd').format(today);
                      final isCompletedToday = habit.completedDates.contains(todayStr);
                      return Padding(
                        padding: const EdgeInsets.only(right: 8.0),
                        child: InkWell(
                          onTap: () {
                            context.read<HabitProvider>().toggleHabitComplete(habit.id!, !isCompletedToday, date: today);
                          },
                          borderRadius: BorderRadius.circular(8),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: isCompletedToday ? const Color(0xFF5856D6).withOpacity(0.2) : Colors.transparent,
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(
                                color: isCompletedToday ? const Color(0xFF5856D6) : const Color(0xFF333333),
                              ),
                            ),
                            child: Row(
                              children: [
                                if (isCompletedToday) ...[
                                  const Icon(Icons.check, size: 14, color: Color(0xFF5856D6)),
                                  const SizedBox(width: 6),
                                ],
                                Text(
                                  habit.name,
                                  style: TextStyle(
                                    color: isCompletedToday ? Colors.white : Colors.grey,
                                    fontSize: 12,
                                    fontWeight: isCompletedToday ? FontWeight.bold : FontWeight.normal,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // 3. HABIT TRACKER TABLE
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF131313),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF222222), width: 0.7),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.track_changes, color: Colors.grey, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      'Habit Tracker',
                      style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                // Tab-like Header
                Row(
                  children: const [
                    Text('Recent', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
                    SizedBox(width: 16),
                    Text('Gallery', style: TextStyle(color: Colors.grey, fontSize: 12)),
                    SizedBox(width: 16),
                    Text('Calendar', style: TextStyle(color: Colors.grey, fontSize: 12)),
                  ],
                ),
                const Divider(color: Color(0xFF222222), height: 24),
                
                // Table Rows
                ListView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: last14Days.length,
                  itemBuilder: (ctx, index) {
                    // Reverse to show most recent days at the top
                    final date = last14Days[last14Days.length - 1 - index];
                    final dateStr = DateFormat('yyyy-MM-dd').format(date);
                    final displayDate = DateFormat('MMMM dd, yyyy').format(date);

                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8.0),
                      child: Row(
                        children: [
                          const Icon(Icons.radio_button_checked, size: 14, color: Colors.grey),
                          const SizedBox(width: 8),
                          const Text('Daily Habits', style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w500)),
                          const Spacer(),
                          Text(displayDate, style: const TextStyle(color: Colors.grey, fontSize: 12)),
                          const SizedBox(width: 16),
                          // Checkboxes for each habit
                          SingleChildScrollView(
                            scrollDirection: Axis.horizontal,
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: habits.map((habit) {
                                final isDone = habit.completedDates.contains(dateStr);
                                return Padding(
                                  padding: const EdgeInsets.only(left: 8.0),
                                  child: InkWell(
                                    onTap: () {
                                      context.read<HabitProvider>().toggleHabitComplete(habit.id!, !isDone, date: date);
                                    },
                                    child: Container(
                                      width: 18,
                                      height: 18,
                                      decoration: BoxDecoration(
                                        color: isDone ? const Color(0xFF5856D6) : Colors.transparent,
                                        border: Border.all(color: isDone ? const Color(0xFF5856D6) : Colors.grey),
                                        borderRadius: BorderRadius.circular(4),
                                      ),
                                      child: isDone ? const Icon(Icons.check, size: 12, color: Colors.white) : null,
                                    ),
                                  ),
                                );
                              }).toList(),
                            ),
                          )
                        ],
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── 🟢 AUSGIEBIGES HABIT TILE (NUN MIT INFOS, PRIO & COLOR) ────────────────

class _HabitTile extends StatelessWidget {
  final Habit habit;
  const _HabitTile({required this.habit});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final String habitName = habit.name;
    final int priority = habit.priority ?? 3;
    final String category = habit.category ?? 'Personal';
    final int streak = habit.currentStreak;

    // 2. Farbauflösung aus Hex-String (z.B. "#5856D6")
    Color habitColor = const Color(0xFF5856D6);
    if (habit.color != null && habit.color!.isNotEmpty) {
      try {
        habitColor = Color(int.parse(habit.color!.replaceFirst('#', '0xFF')));
      } catch (_) {}
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF131313),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFF222222), width: 0.7),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        // Klick öffnet das Detail-Info-Sheet
        onTap: () => _showHabitDetails(context, habit, habitColor, priority, category, streak),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Farbiger Akzentbalken links geladen aus deiner Custom-Habit-Farbe
              Container(width: 4, color: habitColor),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Linke Spalte: Name & Metadaten
                      Expanded(
                        child: Text(
                          habitName,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                      
                      // Rechte Spalte: Kategorie & Prio
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.05),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              category,
                              style: const TextStyle(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.w600),
                            ),
                          ),
                          const SizedBox(height: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                            decoration: BoxDecoration(
                              color: habitColor.withOpacity(0.1),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              'P$priority',
                              style: TextStyle(color: habitColor, fontSize: 10, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ─── 🟢 DETAILED BOTTOM SHEET BEI KLICK AUF HABIT CARD ────────────────────
  void _showHabitDetails(BuildContext context, Habit h, Color habitColor, int priority, String category, int streak) {
    // Scheduling Informationen auslesen
    final String frequency = h.frequency;
    final String timeOfDay = h.preferredTime != null ? '${h.preferredTime!.hour.toString().padLeft(2, '0')}:${h.preferredTime!.minute.toString().padLeft(2, '0')}' : 'Flexibel';

    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF131313),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isScrollControlled: true,
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: EdgeInsets.only(
              left: 24.0, right: 24.0, top: 24.0, 
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 24.0
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
                      Row(
                        children: [
                          CircleAvatar(radius: 6, backgroundColor: habitColor),
                          const SizedBox(width: 10),
                          Text(
                            h.name,
                            style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.grey),
                        onPressed: () => Navigator.pop(ctx),
                      )
                    ],
                  ),
                  const Divider(color: Color(0xFF222222)),
                  const SizedBox(height: 12),

                  // Detail Grid / Liste
                  _buildDetailRow('Kategorie', category, Icons.folder_open),
                  _buildDetailRow('Priorität', 'Stufe P$priority', Icons.outlined_flag),
                  _buildDetailRow('Frequenz (Scheduling)', frequency, Icons.calendar_today_outlined),
                  _buildDetailRow('Uhrzeit', timeOfDay, Icons.access_time),

                  const SizedBox(height: 24),
                  
                  // Einstellungs-Änderungsbutton
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: FilledButton.icon(
                      onPressed: () {
                        Navigator.pop(ctx);
                        showModalBottomSheet(
                          context: context,
                          isScrollControlled: true,
                          backgroundColor: Colors.transparent,
                          builder: (ctx) => CreateHabitSheet(habitToEdit: h),
                        );
                      },
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFF5856D6),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                      icon: const Icon(Icons.settings, size: 18, color: Colors.white),
                      label: const Text('Einstellungen ändern', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildDetailRow(String label, String value, IconData icon) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Colors.grey),
          const SizedBox(width: 12),
          Text('$label:', style: const TextStyle(color: Colors.grey, fontSize: 13)),
          const Spacer(),
          Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
        ],
      ),
    );
  }
}