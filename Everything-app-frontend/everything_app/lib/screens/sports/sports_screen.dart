import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../providers/sports_provider.dart';

class SportsScreen extends StatefulWidget {
  const SportsScreen({super.key});

  @override
  State<SportsScreen> createState() => _SportsScreenState();
}

class _SportsScreenState extends State<SportsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SportsProvider>().loadData();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        leading: const BackButton(color: Color(0xFFC2C1FF)),
        title: Text(
          'Lyfta Gym', 
          style: GoogleFonts.spaceGrotesk(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 20),
        ),
        backgroundColor: const Color(0xFF131313),
        elevation: 0,
        bottom: TabBar(
          controller: _tabController,
          labelColor: const Color(0xFF5856D6),
          unselectedLabelColor: Colors.grey,
          indicatorColor: const Color(0xFF5856D6),
          indicatorSize: TabBarIndicatorSize.label,
          labelStyle: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 13),
          tabs: const [
            Tab(icon: Icon(Icons.home_filled), text: 'Home'),
            Tab(icon: Icon(Icons.fitness_center), text: 'Training'),
            Tab(icon: Icon(Icons.history), text: 'Verlauf'),
            Tab(icon: Icon(Icons.trending_up), text: 'Fortschritt'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const [
          _StartTab(),
          _TrainingTab(),
          _HistoryTab(),
          _ProgressTab(),
        ],
      ),
    );
  }
}

// ─── TAB 1: Start Tab (Home Feed - Screenshot 1 & 3) ───────────────────────────

class _StartTab extends StatelessWidget {
  const _StartTab();

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return RefreshIndicator(
      onRefresh: () => sports.loadData(),
      color: const Color(0xFF5856D6),
      backgroundColor: const Color(0xFF131313),
      child: sports.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // Active Workout Banner
                if (sports.currentWorkout != null) ...[
                  const _ActiveWorkoutBanner(),
                  const SizedBox(height: 24),
                ],

                // Weekly Overview Calendar
                Text('Wochenübersicht', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                const SizedBox(height: 12),
                const _WeeklyOverview(),
                const SizedBox(height: 24),

                // Huge "Workout starten" Button
                Container(
                  width: double.infinity,
                  decoration: BoxDecoration(
                    boxShadow: [
                      BoxShadow(
                        color: const Color(0xFF5856D6).withValues(alpha: 0.25),
                        blurRadius: 20,
                        spreadRadius: 2,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF5856D6),
                      foregroundColor: Colors.white,
                      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      padding: const EdgeInsets.symmetric(vertical: 20),
                    ),
                    onPressed: () => _showQuickStartSheet(context),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.play_arrow, size: 28),
                        const SizedBox(width: 12),
                        Text('WORKOUT STARTEN', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, letterSpacing: 1.5)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                // Social Feed / Recent Workouts (Screenshot 1 Parity)
                Text('Aktivitätsfeed', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                const SizedBox(height: 16),
                if (sports.workoutSessions.isEmpty)
                  Container(
                    padding: const EdgeInsets.all(24),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5)),
                    child: Text('Noch keine Aktivitäten im Feed', style: GoogleFonts.inter(color: Colors.grey)),
                  )
                else
                  ...sports.workoutSessions.map((session) => _SocialFeedCard(session: session)),
              ],
            ),
    );
  }

  void _showQuickStartSheet(BuildContext context) {
    final sports = context.read<SportsProvider>();
    if (sports.currentWorkout != null) {
      _showActiveWorkoutSheet(context);
      return;
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF131313),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) {
        final plans = sports.workoutPlans;
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Workout auswählen', style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
                    IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(ctx)),
                  ],
                ),
              ),
              const Divider(color: Color(0xFF222222), height: 1),
              ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: const Color(0xFF5856D6).withValues(alpha: 0.2), shape: BoxShape.circle),
                  child: const Icon(Icons.add, color: Color(0xFF5856D6)),
                ),
                title: Text('Leeres Workout starten', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                subtitle: Text('Freies Training ohne Vorlage', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
                onTap: () {
                  Navigator.pop(ctx);
                  final planId = plans.isNotEmpty ? plans.first['id'] : 1;
                  sports.startWorkout(planId).then((ok) {
                    if (ok && context.mounted) _showActiveWorkoutSheet(context);
                  });
                },
              ),
              const Divider(color: Color(0xFF222222), height: 1),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                child: Text('MEINE ROUTINEN', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1)),
              ),
              if (plans.isEmpty)
                Padding(padding: const EdgeInsets.all(20), child: Text('Keine Routinen verfügbar', style: GoogleFonts.inter(color: Colors.grey)))
              else
                ...plans.map((p) => ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 6),
                  leading: const CircleAvatar(backgroundColor: Color(0xFF1E1E1E), child: Icon(Icons.fitness_center, color: Color(0xFF5856D6), size: 18)),
                  title: Text(p['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold)),
                  subtitle: Text('${p['day']} · ${p['estimatedDuration']} Min.', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
                  trailing: const Icon(Icons.play_arrow, color: Color(0xFF5856D6)),
                  onTap: () {
                    Navigator.pop(ctx);
                    sports.startWorkout(p['id'] as int).then((ok) {
                      if (ok && context.mounted) _showActiveWorkoutSheet(context);
                    });
                  },
                )),
              const SizedBox(height: 16),
            ],
          ),
        );
      },
    );
  }
}

// ─── Social Feed Card (Screenshot 1 Parity) ────────────────────────────────────

class _SocialFeedCard extends StatelessWidget {
  final Map<String, dynamic> session;
  const _SocialFeedCard({required this.session});

  @override
  Widget build(BuildContext context) {
    final name = session['name'] as String? ?? 'Push Workout';
    final date = session['date'] as DateTime? ?? DateTime.now();
    final duration = session['durationMinutes'] as int? ?? 60;
    final totalSets = session['totalSets'] as int? ?? 12;
    final exercises = session['exercises'] as List? ?? [
      {'name': 'Bench Press', 'sets': 4},
      {'name': 'Incline Bench Press', 'sets': 4},
      {'name': 'Cable Seated Row', 'sets': 4},
    ];

    // Mock volume calculation
    final volume = totalSets * 12 * 20; // rough calculation for display
    final records = max(1, totalSets ~/ 4);
    final dateStr = DateFormat('MMMM dd, yyyy \'at\' hh:mm a').format(date);

    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      decoration: BoxDecoration(
        color: const Color(0xFF131313),
        border: Border.all(color: const Color(0xFF222222), width: 0.5),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // User Profile Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const CircleAvatar(
                  radius: 20,
                  backgroundColor: Color(0xFF1E1E1E),
                  child: Icon(Icons.person, color: Color(0xFFC2C1FF)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text('Finn Lindberg', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15)),
                          const SizedBox(width: 4),
                          const Icon(Icons.verified, color: Color(0xFF5856D6), size: 16),
                        ],
                      ),
                      Text(dateStr, style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
                    ],
                  ),
                ),
                IconButton(icon: const Icon(Icons.more_vert, color: Colors.grey), onPressed: () {}),
              ],
            ),
          ),
          
          // Workout Title
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(name, style: GoogleFonts.manrope(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(height: 16),

          // Summary Stats Bar (Duration, Volume, Records)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(color: const Color(0xFF1A1A1A), borderRadius: BorderRadius.circular(4)),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Duration', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)),
                      const SizedBox(height: 4),
                      Text('${duration}h 0m', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Volume', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)),
                      const SizedBox(height: 4),
                      Text('$volume kg', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Records', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          const Text('🏆🏆 ', style: TextStyle(fontSize: 14)),
                          Text('$records', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Exercise Grid (3 columns with grey thumbnails)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 12,
                mainAxisSpacing: 16,
                childAspectRatio: 0.85,
              ),
              itemCount: exercises.length,
              itemBuilder: (ctx, i) {
                final ex = exercises[i];
                final exName = ex['name'] as String? ?? '';
                final exSets = ex['sets'] as int? ?? 4;

                return Column(
                  children: [
                    Expanded(
                      child: Container(
                        width: double.infinity,
                        decoration: BoxDecoration(color: const Color(0xFF252525), borderRadius: BorderRadius.circular(8)),
                        child: const Icon(Icons.fitness_center, color: Colors.white70, size: 28),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text('$exSets x $exName', textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: GoogleFonts.inter(color: Colors.grey, fontSize: 11)),
                  ],
                );
              },
            ),
          ),
          const SizedBox(height: 20),
          const Divider(color: Color(0xFF222222), height: 1),

          // Social Footer (Likes & Actions)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                const Row(
                  children: [
                    CircleAvatar(radius: 10, backgroundColor: Color(0xFF5856D6), child: Icon(Icons.person, size: 12, color: Colors.white)),
                    SizedBox(width: 4),
                    CircleAvatar(radius: 10, backgroundColor: Color(0xFFC2C1FF), child: Icon(Icons.person, size: 12, color: Color(0xFF131313))),
                  ],
                ),
                const SizedBox(width: 8),
                Text('17 Likes', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13, fontWeight: FontWeight.bold)),
                const Spacer(),
                IconButton(icon: const Icon(Icons.thumb_up_outlined, color: Colors.grey, size: 20), onPressed: () {}),
                IconButton(icon: const Icon(Icons.chat_bubble_outline, color: Colors.grey, size: 20), onPressed: () {}),
                IconButton(icon: const Icon(Icons.share_outlined, color: Colors.grey, size: 20), onPressed: () {}),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Weekly Overview Calendar Widget ───────────────────────────────────────────

class _WeeklyOverview extends StatelessWidget {
  const _WeeklyOverview();

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final sessions = sports.workoutSessions;
    final now = DateTime.now();
    final monday = now.subtract(Duration(days: now.weekday - 1));
    final workoutDays = sessions.map((s) => DateFormat('yyyy-MM-dd').format(s['date'] as DateTime)).toSet();
    final dayNames = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
      decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: List.generate(7, (index) {
          final dayDate = monday.add(Duration(days: index));
          final dayStr = DateFormat('yyyy-MM-dd').format(dayDate);
          final hasWorkout = workoutDays.contains(dayStr);
          final isToday = dayDate.year == now.year && dayDate.month == now.month && dayDate.day == now.day;

          return Column(
            children: [
              Text(dayNames[index], style: GoogleFonts.manrope(color: isToday ? const Color(0xFF5856D6) : Colors.grey, fontSize: 13, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Container(
                width: 38,
                height: 38,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: isToday ? const Color(0xFF5856D6).withValues(alpha: 0.2) : const Color(0xFF1E1E1E),
                  shape: BoxShape.circle,
                  border: Border.all(color: hasWorkout ? const Color(0xFF5856D6) : isToday ? const Color(0xFF5856D6) : Colors.white10, width: hasWorkout ? 2 : 1),
                ),
                child: hasWorkout
                    ? const Icon(Icons.check, color: Color(0xFF5856D6), size: 18)
                    : Text('${dayDate.day}', style: GoogleFonts.manrope(color: isToday ? const Color(0xFF5856D6) : Colors.white, fontWeight: FontWeight.bold, fontSize: 14)),
              ),
            ],
          );
        }),
      ),
    );
  }
}

// ─── TAB 2: Training Tab (Create New Workouts & Manage Routines) ───────────────

class _TrainingTab extends StatelessWidget {
  const _TrainingTab();

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return RefreshIndicator(
      onRefresh: () => sports.loadData(),
      color: const Color(0xFF5856D6),
      backgroundColor: const Color(0xFF131313),
      child: sports.isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6)))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 24),
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.add, size: 22),
                    label: Text('NEUE ROUTINE ERSTELLEN', style: GoogleFonts.manrope(fontSize: 16, fontWeight: FontWeight.bold, letterSpacing: 1)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF5856D6),
                      side: const BorderSide(color: Color(0xFF5856D6), width: 2),
                      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                      padding: const EdgeInsets.symmetric(vertical: 18),
                      backgroundColor: const Color(0xFF5856D6).withValues(alpha: 0.1),
                    ),
                    onPressed: () => _showCreateRoutineSheet(context),
                  ),
                ),

                Text('Meine Routinen', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                const SizedBox(height: 16),
                if (sports.workoutPlans.isEmpty)
                  Container(
                    padding: const EdgeInsets.all(24),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
                    child: Text('Keine Routinen vorhanden. Erstelle jetzt deine erste Routine!', style: GoogleFonts.inter(color: Colors.grey)),
                  )
                else
                  ...sports.workoutPlans.map((plan) => _RoutineCard(plan: plan)),
                
                const SizedBox(height: 16),
                const Divider(color: Color(0xFF222222), height: 32),
                
                Text('Übungsbibliothek', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                const SizedBox(height: 8),
                Text('Durchsuche alle verfügbaren Übungen nach Muskelgruppen', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13)),
                const SizedBox(height: 16),
                _ExerciseLibrarySection(),
              ],
            ),
    );
  }

  void _showCreateRoutineSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF0E0E0E),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (_) => const _CreateRoutineSheet(),
    );
  }
}

class _CreateRoutineSheet extends StatefulWidget {
  const _CreateRoutineSheet();
  @override
  State<_CreateRoutineSheet> createState() => _CreateRoutineSheetState();
}

class _CreateRoutineSheetState extends State<_CreateRoutineSheet> {
  final _nameCtrl = TextEditingController();
  String _selectedDay = 'Montag';
  final List<Map<String, dynamic>> _selectedExercises = [];
  final List<String> _days = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];

  @override
  void dispose() { _nameCtrl.dispose(); super.dispose(); }

  void _save() {
    if (_nameCtrl.text.trim().isEmpty) return;
    final sports = context.read<SportsProvider>();
    sports.workoutPlans.add({
      'id': sports.workoutPlans.length + 1,
      'name': _nameCtrl.text.trim(),
      'day': _selectedDay,
      'exercises': _selectedExercises.isEmpty ? [{'name': 'Bankdrücken', 'sets': 3, 'reps': 10, 'weight': 60}] : _selectedExercises,
      'estimatedDuration': 60,
    });
    sports.clearError();
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final insets = MediaQuery.of(context).viewInsets.bottom;
    final sports = context.watch<SportsProvider>();
    final allExercises = sports.exercises;

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 40, 20, insets + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Neue Routine erstellen', style: GoogleFonts.manrope(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
              IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(context)),
            ],
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _nameCtrl,
            style: GoogleFonts.inter(color: Colors.white),
            decoration: InputDecoration(hintText: 'Routinename (z.B. Push, Pull, Beine)', hintStyle: GoogleFonts.inter(color: Colors.grey), filled: true, fillColor: const Color(0xFF131313), border: const OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide.none)),
          ),
          const SizedBox(height: 20),
          Text('Geplanter Tag', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 13, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _days.map((day) {
                final isSel = _selectedDay == day;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(day, style: GoogleFonts.manrope(color: isSel ? Colors.white : Colors.grey, fontWeight: FontWeight.bold, fontSize: 13)),
                    selected: isSel,
                    selectedColor: const Color(0xFF5856D6),
                    backgroundColor: const Color(0xFF1E1E1E),
                    side: BorderSide(color: isSel ? const Color(0xFF5856D6) : const Color(0xFF222222)),
                    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                    onSelected: (sel) { if (sel) setState(() => _selectedDay = day); },
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Übungen in dieser Routine', style: GoogleFonts.manrope(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
              TextButton.icon(icon: const Icon(Icons.add, color: Color(0xFF5856D6), size: 18), label: Text('Übung hinzufügen', style: GoogleFonts.manrope(color: const Color(0xFF5856D6), fontWeight: FontWeight.bold)), onPressed: () => _showSelectExerciseDialog(context, allExercises)),
            ],
          ),
          const SizedBox(height: 8),
          if (_selectedExercises.isEmpty)
            Container(width: double.infinity, padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)), child: Text('Noch keine Übungen hinzugefügt.', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13)))
          else
            ..._selectedExercises.asMap().entries.map((entry) {
              final idx = entry.key;
              final ex = entry.value;
              return Container(
                margin: const EdgeInsets.only(bottom: 8), padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('${ex['name']}', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold)),
                    Row(
                      children: [
                        Text('${ex['sets']} Sätze · ${ex['reps']} Wdh', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13)),
                        const SizedBox(width: 12),
                        IconButton(icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20), onPressed: () => setState(() => _selectedExercises.removeAt(idx))),
                      ],
                    ),
                  ],
                ),
              );
            }),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), foregroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), padding: const EdgeInsets.symmetric(vertical: 16)),
              onPressed: _save,
              child: Text('ROUTINE SPEICHERN', style: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 16, letterSpacing: 1)),
            ),
          ),
        ],
      ),
    );
  }

  void _showSelectExerciseDialog(BuildContext context, List<Map<String, dynamic>> allExercises) {
    showModalBottomSheet(
      context: context, backgroundColor: const Color(0xFF131313), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => Column(
        children: [
          Padding(padding: const EdgeInsets.all(16), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text('Übung auswählen', style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)), IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(ctx))])),
          const Divider(color: Color(0xFF222222), height: 1),
          Expanded(child: ListView.builder(itemCount: allExercises.length, itemBuilder: (_, i) {
            final ex = allExercises[i];
            return ListTile(
              leading: const CircleAvatar(backgroundColor: Color(0xFF1E1E1E), child: Icon(Icons.fitness_center, color: Color(0xFF5856D6), size: 18)),
              title: Text(ex['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold)),
              subtitle: Text('${ex['category']} · ${ex['equipment']}', style: GoogleFonts.inter(color: Colors.grey)),
              onTap: () { setState(() { _selectedExercises.add({'name': ex['name'], 'sets': 3, 'reps': 10, 'weight': 0}); }); Navigator.pop(ctx); },
            );
          })),
        ],
      ),
    );
  }
}

// ─── Exercise Library Section Widget (Screenshot 5 Detail Parity) ──────────────

class _ExerciseLibrarySection extends StatefulWidget {
  @override
  State<_ExerciseLibrarySection> createState() => _ExerciseLibrarySectionState();
}

class _ExerciseLibrarySectionState extends State<_ExerciseLibrarySection> {
  String _search = '';
  String _category = 'Alle';
  final List<String> _categories = ['Alle', 'Chest', 'Legs', 'Back', 'Shoulders', 'Core'];

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final exercises = sports.exercises.where((ex) {
      final matchesSearch = (ex['name'] as String? ?? '').toLowerCase().contains(_search.toLowerCase());
      final matchesCat = _category == 'Alle' || (ex['category'] as String? ?? '') == _category;
      return matchesSearch && matchesCat;
    }).toList();

    return Column(
      children: [
        TextField(
          style: GoogleFonts.inter(color: Colors.white),
          decoration: InputDecoration(hintText: 'Übung suchen...', hintStyle: GoogleFonts.inter(color: Colors.grey), prefixIcon: const Icon(Icons.search, color: Colors.grey), filled: true, fillColor: const Color(0xFF131313), border: const OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide.none)),
          onChanged: (val) => setState(() => _search = val),
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 36,
          child: ListView.builder(
            scrollDirection: Axis.horizontal, itemCount: _categories.length,
            itemBuilder: (_, i) {
              final cat = _categories[i]; final isSel = _category == cat;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(cat, style: GoogleFonts.manrope(color: isSel ? Colors.white : Colors.grey, fontWeight: FontWeight.bold, fontSize: 13)),
                  selected: isSel, selectedColor: const Color(0xFF5856D6), backgroundColor: const Color(0xFF1E1E1E), side: BorderSide(color: isSel ? const Color(0xFF5856D6) : const Color(0xFF222222)), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
                  onSelected: (sel) { if (sel) setState(() => _category = cat); },
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 16),
        if (exercises.isEmpty)
          Padding(padding: const EdgeInsets.all(20), child: Text('Keine Übungen gefunden', style: GoogleFonts.inter(color: Colors.grey)))
        else
          ...exercises.map((ex) {
            final diff = ex['difficulty'] as String? ?? 'beginner';
            final color = diff == 'advanced' ? Colors.redAccent : diff == 'intermediate' ? Colors.orangeAccent : Colors.green;

            return Container(
              margin: const EdgeInsets.only(bottom: 8), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                leading: Container(width: 40, height: 40, decoration: BoxDecoration(color: const Color(0xFF252525), borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.fitness_center, color: Colors.white70, size: 20)),
                title: Text(ex['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15)),
                subtitle: Text('${ex['category']} · ${ex['equipment']}', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
                trailing: Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4), decoration: BoxDecoration(color: color.withValues(alpha: 0.15), border: Border.all(color: color.withValues(alpha: 0.3)), borderRadius: BorderRadius.circular(2)), child: Text(diff.toUpperCase(), style: GoogleFonts.inter(color: color, fontSize: 10, fontWeight: FontWeight.bold))),
                onTap: () => _showExerciseDetailSheet(context, ex),
              ),
            );
          }),
      ],
    );
  }

  void _showExerciseDetailSheet(BuildContext context, Map<String, dynamic> ex) {
    showModalBottomSheet(
      context: context, isScrollControlled: true, backgroundColor: const Color(0xFF0E0E0E), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) => _ExerciseDetailModal(ex: ex),
    );
  }
}

class _ExerciseDetailModal extends StatefulWidget {
  final Map<String, dynamic> ex;
  const _ExerciseDetailModal({required this.ex});
  @override
  State<_ExerciseDetailModal> createState() => _ExerciseDetailModalState();
}

class _ExerciseDetailModalState extends State<_ExerciseDetailModal> with SingleTickerProviderStateMixin {
  late TabController _subTab;
  @override
  void initState() { super.initState(); _subTab = TabController(length: 3, vsync: this); }
  @override
  void dispose() { _subTab.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final ex = widget.ex;
    final muscles = ex['muscleGroups'] as List? ?? [];

    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.85,
        child: Column(
          children: [
            AppBar(
              backgroundColor: const Color(0xFF131313), leading: IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(context)),
              title: Text(ex['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
              bottom: TabBar(
                controller: _subTab, indicatorColor: const Color(0xFF5856D6), labelColor: const Color(0xFF5856D6), unselectedLabelColor: Colors.grey,
                tabs: const [Tab(text: 'About'), Tab(text: 'History'), Tab(text: 'Charts')],
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _subTab,
                children: [
                  ListView(
                    padding: const EdgeInsets.all(20),
                    children: [
                      // Large white illustration card (Screenshot 5 parity)
                      Container(height: 200, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)), alignment: Alignment.center, child: const Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(Icons.fitness_center, size: 80, color: Color(0xFF131313)), SizedBox(height: 12), Text('Exercise Visual Animation', style: TextStyle(color: Color(0xFF131313), fontWeight: FontWeight.bold))])),
                      const SizedBox(height: 24),
                      Text('Category', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)), const SizedBox(height: 4), Text('${ex['category']}', style: GoogleFonts.inter(color: Colors.white, fontSize: 16)), const SizedBox(height: 16),
                      Text('Equipment', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)), const SizedBox(height: 4), Text('${ex['equipment']}', style: GoogleFonts.inter(color: Colors.white, fontSize: 16)), const SizedBox(height: 24),
                      Text('Target Muscles', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)), const SizedBox(height: 12),
                      Wrap(spacing: 8, runSpacing: 8, children: muscles.map((m) => Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6), decoration: BoxDecoration(color: const Color(0xFF5856D6).withValues(alpha: 0.15), border: Border.all(color: const Color(0xFF5856D6).withValues(alpha: 0.3)), borderRadius: BorderRadius.circular(4)), child: Text('$m', style: GoogleFonts.inter(color: const Color(0xFF5856D6), fontWeight: FontWeight.bold)))).toList()),
                    ],
                  ),
                  const Center(child: Text('No past history for this exercise', style: TextStyle(color: Colors.grey))),
                  const Center(child: Text('Charts & Volume progression', style: TextStyle(color: Colors.grey))),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.all(16), decoration: const BoxDecoration(color: Color(0xFF131313), border: Border(top: BorderSide(color: Color(0xFF222222)))),
              child: SizedBox(width: double.infinity, child: FilledButton(style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), padding: const EdgeInsets.symmetric(vertical: 16), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25))), onPressed: () => Navigator.pop(context), child: Text('Add to routine', style: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 16)))),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── TAB 3: History Tab (Verlauf) ──────────────────────────────────────────────

class _HistoryTab extends StatelessWidget {
  const _HistoryTab();
  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final sessions = sports.workoutSessions;

    return RefreshIndicator(
      onRefresh: () => sports.loadData(), color: const Color(0xFF5856D6), backgroundColor: const Color(0xFF131313),
      child: sports.isLoading ? const Center(child: CircularProgressIndicator(color: Color(0xFF5856D6))) : sessions.isEmpty ? Center(child: Text('Noch keine Trainings absolviert', style: GoogleFonts.inter(color: Colors.grey))) : ListView.builder(padding: const EdgeInsets.all(16), itemCount: sessions.length, itemBuilder: (_, i) => _HistoryCard(session: sessions[i])),
    );
  }
}

class _HistoryCard extends StatefulWidget {
  final Map<String, dynamic> session;
  const _HistoryCard({required this.session});
  @override
  State<_HistoryCard> createState() => _HistoryCardState();
}

class _HistoryCardState extends State<_HistoryCard> {
  bool _expanded = false;
  @override
  Widget build(BuildContext context) {
    final name = widget.session['name'] as String? ?? 'Workout';
    final date = widget.session['date'] as DateTime? ?? DateTime.now();
    final duration = widget.session['durationMinutes'] as int? ?? 0;
    final totalSets = widget.session['totalSets'] as int? ?? 0;
    final notes = widget.session['notes'] as String? ?? '';
    final exercises = widget.session['exercises'] as List? ?? [];
    final dateFormat = DateFormat('EEEE, dd. MMMM yyyy · HH:mm', 'de_DE').format(date);

    return Container(
      margin: const EdgeInsets.only(bottom: 16), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(name, style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)), Icon(_expanded ? Icons.expand_less : Icons.expand_more, color: Colors.grey)]),
                  const SizedBox(height: 6), Text(dateFormat, style: GoogleFonts.inter(color: const Color(0xFFC2C1FF), fontSize: 12, fontWeight: FontWeight.bold)), const SizedBox(height: 12),
                  Row(children: [const Icon(Icons.timer_outlined, size: 16, color: Colors.grey), const SizedBox(width: 6), Text('$duration Min.', style: GoogleFonts.inter(color: Colors.white70, fontSize: 14)), const SizedBox(width: 20), const Icon(Icons.repeat, size: 16, color: Colors.grey), const SizedBox(width: 6), Text('$totalSets Sätze', style: GoogleFonts.inter(color: Colors.white70, fontSize: 14))]),
                  if (notes.isNotEmpty) ...[const SizedBox(height: 12), Container(width: double.infinity, padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: const Color(0xFF1E1E1E), border: Border.all(color: Colors.white10), borderRadius: BorderRadius.circular(4)), child: Text(notes, style: GoogleFonts.inter(color: Colors.white70, fontSize: 13, fontStyle: FontStyle.italic)))],
                ],
              ),
            ),
          ),
          if (_expanded) ...[
            const Divider(color: Color(0xFF222222), height: 1),
            Container(
              color: const Color(0xFF1E1E1E).withValues(alpha: 0.5), padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: exercises.map((ex) {
                final exName = ex['name'] as String? ?? ''; final exSets = ex['sets'] as int? ?? 0; final exReps = ex['reps'] as int? ?? 0; final exWeight = ex['weight'] ?? 0;
                return Padding(padding: const EdgeInsets.only(bottom: 12), child: Row(children: [Container(width: 8, height: 8, decoration: const BoxDecoration(color: Color(0xFF5856D6), shape: BoxShape.circle)), const SizedBox(width: 12), Expanded(child: Text(exName, style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14))), Text('$exSets Sätze · $exReps Wdh · $exWeight kg', style: GoogleFonts.inter(color: Colors.white70, fontSize: 13))]));
              }).toList()),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── TAB 4: Progress Tab (Screenshot 2 & 4 Parity - Measures & Heat Map) ───────

class _ProgressTab extends StatefulWidget {
  const _ProgressTab();
  @override
  State<_ProgressTab> createState() => _ProgressTabState();
}

class _ProgressTabState extends State<_ProgressTab> with SingleTickerProviderStateMixin {
  late TabController _progressTab;
  @override
  void initState() { super.initState(); _progressTab = TabController(length: 4, vsync: this); }
  @override
  void dispose() { _progressTab.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();

    return Column(
      children: [
        Container(
          color: const Color(0xFF131313),
          child: TabBar(
            controller: _progressTab, indicatorColor: const Color(0xFF5856D6), labelColor: const Color(0xFF5856D6), unselectedLabelColor: Colors.grey, labelStyle: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 13),
            tabs: const [Tab(text: 'Overview'), Tab(text: 'Exercises'), Tab(text: 'Measures'), Tab(text: 'Photos')],
          ),
        ),
        Expanded(
          child: TabBarView(
            controller: _progressTab,
            children: [
              _OverviewSubTab(sports: sports),
              const Center(child: Text('Exercise Volume Trends', style: TextStyle(color: Colors.grey))),
              const _MeasuresSubTab(),
              const Center(child: Text('Progress Photos Gallery', style: TextStyle(color: Colors.grey))),
            ],
          ),
        ),
      ],
    );
  }
}

class _OverviewSubTab extends StatelessWidget {
  final SportsProvider sports;
  const _OverviewSubTab({required this.sports});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // This Week Volume Header (Screenshot 4 Parity)
        Container(
          padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('This Week', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 14)),
              const SizedBox(height: 4),
              Text('34 135 kg', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
              const SizedBox(height: 24),
              _LyftaBarChart(),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Anatomical Muscle Heat Map Graphic (Screenshot 4 Parity)
        Text('Muscle Recovery & Heat Map', style: GoogleFonts.manrope(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Column(
                    children: [
                      Text('FRONT', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 12),
                      const _AnatomyModel(isFront: true),
                    ],
                  ),
                  Column(
                    children: [
                      Text('BACK', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 12),
                      const _AnatomyModel(isFront: false),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(width: 12, height: 12, decoration: const BoxDecoration(color: Colors.redAccent, shape: BoxShape.circle)), const SizedBox(width: 6), Text('Heavily Worked', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)), const SizedBox(width: 16),
                  Container(width: 12, height: 12, decoration: const BoxDecoration(color: Colors.orangeAccent, shape: BoxShape.circle)), const SizedBox(width: 6), Text('Moderately Worked', style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _AnatomyModel extends StatelessWidget {
  final bool isFront;
  const _AnatomyModel({required this.isFront});

  @override
  Widget build(BuildContext context) {
    // Beautiful abstract anatomical representation using custom stacked containers
    return Container(
      width: 120, height: 220,
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(color: const Color(0xFF1A1A1A), borderRadius: BorderRadius.circular(8), border: Border.all(color: const Color(0xFF252525))),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Head
          CircleAvatar(radius: 16, backgroundColor: Colors.grey[800]),
          // Torso / Chest / Back
          Container(
            width: 70, height: 60,
            decoration: BoxDecoration(color: isFront ? Colors.redAccent : Colors.orangeAccent, borderRadius: BorderRadius.circular(8)),
            alignment: Alignment.center,
            child: Text(isFront ? 'CHEST\n& DELTS' : 'LATS\n& TRAPS', textAlign: TextAlign.center, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
          ),
          // Core / Lower Back
          Container(width: 50, height: 30, decoration: BoxDecoration(color: Colors.grey[800], borderRadius: BorderRadius.circular(6))),
          // Legs / Quads / Hamstrings
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              Container(width: 25, height: 60, decoration: BoxDecoration(color: isFront ? Colors.orangeAccent : Colors.grey[800], borderRadius: BorderRadius.circular(6))),
              Container(width: 25, height: 60, decoration: BoxDecoration(color: isFront ? Colors.orangeAccent : Colors.grey[800], borderRadius: BorderRadius.circular(6))),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── Measures Sub Tab (Screenshot 2 Parity - Custom Sparklines) ────────────────

class _MeasuresSubTab extends StatelessWidget {
  const _MeasuresSubTab();

  @override
  Widget build(BuildContext context) {
    final measures = [
      {'name': 'Chest', 'val': '108 cm', 'diff': '~ 5 cm', 'points': [0.2, 0.3, 0.4, 0.6, 0.8, 0.9, 0.95]},
      {'name': 'Right bicep', 'val': '37.5 cm', 'diff': '~ 1.5 cm', 'points': [0.4, 0.45, 0.5, 0.55, 0.7, 0.75, 0.85]},
      {'name': 'Left forearm', 'val': '30 cm', 'diff': '~ 0.5 cm', 'points': [0.3, 0.3, 0.35, 0.4, 0.6, 0.75, 0.9]},
      {'name': 'Right forearm', 'val': '30 cm', 'diff': '~ 1 cm', 'points': [0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 0.95]},
      {'name': 'Waist', 'val': '85 cm', 'diff': '~ 3 cm', 'points': [0.8, 0.75, 0.7, 0.65, 0.6, 0.55, 0.5]},
      {'name': 'Hips', 'val': '98 cm', 'diff': '~ 2 cm', 'points': [0.6, 0.65, 0.68, 0.7, 0.75, 0.8, 0.85]},
      {'name': 'Left thigh', 'val': '59 cm', 'diff': '~ 2 cm', 'points': [0.4, 0.45, 0.5, 0.6, 0.65, 0.7, 0.8]},
    ];

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: measures.length,
      itemBuilder: (ctx, i) {
        final m = measures[i];
        final points = m['points'] as List<double>;

        return Container(
          margin: const EdgeInsets.only(bottom: 12), padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
          child: Row(
            children: [
              Container(width: 40, height: 40, decoration: BoxDecoration(color: const Color(0xFF1E1E1E), borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.accessibility_new, color: Color(0xFF5856D6))),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${m['name']}', style: GoogleFonts.manrope(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text('${m['val']} · ${m['diff']}', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13)),
                  ],
                ),
              ),
              SizedBox(
                width: 100, height: 40,
                child: CustomPaint(painter: _SparklinePainter(points: points, color: const Color(0xFF5856D6))),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _SparklinePainter extends CustomPainter {
  final List<double> points;
  final Color color;
  _SparklinePainter({required this.points, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;
    final paint = Paint()..color = color..strokeWidth = 2.5..style = PaintingStyle.stroke..strokeCap = StrokeCap.round;
    final path = Path();
    final stepX = size.width / (points.length - 1);
    for (int i = 0; i < points.length; i++) {
      final x = i * stepX;
      // Invert Y because canvas 0,0 is top left
      final y = size.height * (1 - points[i]);
      if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
    }
    canvas.drawPath(path, paint);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

// ─── Active Workout Modal Sheet (Screenshot 3 Parity - Finish Pill & Box) ──────

void _showActiveWorkoutSheet(BuildContext context, {bool openFinish = false}) {
  showModalBottomSheet(
    context: context, isScrollControlled: true, backgroundColor: const Color(0xFF0E0E0E), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
    builder: (_) => _ActiveWorkoutSheet(openFinish: openFinish),
  );
}

class _ActiveWorkoutSheet extends StatefulWidget {
  final bool openFinish;
  const _ActiveWorkoutSheet({required this.openFinish});
  @override
  State<_ActiveWorkoutSheet> createState() => _ActiveWorkoutSheetState();
}

class _ActiveWorkoutSheetState extends State<_ActiveWorkoutSheet> {
  late Timer _timer;
  String _elapsed = '00:00';

  @override
  void initState() {
    super.initState(); _initWorkoutState(); _updateTimer();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _updateTimer());
    if (widget.openFinish) { WidgetsBinding.instance.addPostFrameCallback((_) { _showFinishDialog(context); }); }
  }

  void _initWorkoutState() {
    final sports = context.read<SportsProvider>();
    final workout = sports.currentWorkout;
    if (workout == null) return;
    final currentExercises = workout['exercises'] as List? ?? [];
    final updatedExercises = List<Map<String, dynamic>>.from(currentExercises.map((ex) {
      final exMap = Map<String, dynamic>.from(ex);
      if (exMap['loggedSets'] == null) {
        final setsCount = exMap['sets'] as int? ?? 3; final defaultReps = exMap['reps'] as int? ?? 10; final defaultWeight = exMap['weight'] ?? 0;
        exMap['loggedSets'] = List.generate(setsCount, (index) => {'set': index + 1, 'weight': defaultWeight, 'reps': defaultReps, 'done': false});
      }
      return exMap;
    }));
    workout['exercises'] = updatedExercises;
  }

  @override
  void dispose() { _timer.cancel(); super.dispose(); }

  void _updateTimer() {
    final sports = context.read<SportsProvider>();
    if (sports.currentWorkout == null) return;
    final start = sports.currentWorkout!['startTime'] as DateTime;
    final diff = DateTime.now().difference(start);
    final minutes = diff.inMinutes.toString().padLeft(2, '0');
    final seconds = (diff.inSeconds % 60).toString().padLeft(2, '0');
    if (mounted) setState(() { _elapsed = '$minutes:$seconds'; });
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final workout = sports.currentWorkout;
    if (workout == null) return Scaffold(backgroundColor: const Color(0xFF0E0E0E), body: Center(child: Text('Kein aktives Workout', style: GoogleFonts.inter(color: Colors.white))));

    final exercises = workout['exercises'] as List<Map<String, dynamic>>? ?? [];
    int totalSets = 0; int completedSets = 0; double totalVolume = 0;
    for (var ex in exercises) {
      final logged = ex['loggedSets'] as List<Map<String, dynamic>>? ?? [];
      totalSets += logged.length;
      for (var s in logged) {
        if (s['done'] == true) { completedSets++; totalVolume += (s['weight'] as num) * (s['reps'] as num); }
      }
    }

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313), foregroundColor: Colors.white, elevation: 0,
        leading: IconButton(icon: const Icon(Icons.keyboard_arrow_down, size: 28), tooltip: 'Minimieren', onPressed: () => Navigator.pop(context)),
        title: Row(
          children: [
            const Icon(Icons.timer_outlined, color: Color(0xFF5856D6), size: 20), const SizedBox(width: 6),
            Text(_elapsed, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: FilledButton(
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
              onPressed: () => _showFinishDialog(context),
              child: Text('Finish', style: GoogleFonts.manrope(fontWeight: FontWeight.bold)),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Top Summary Box (Screenshot 3 Parity)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              decoration: BoxDecoration(color: const Color(0xFF1A1A1A), borderRadius: BorderRadius.circular(12), border: Border.all(color: const Color(0xFF252525))),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Column(children: [Text('Duration', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)), const SizedBox(height: 4), Text(_elapsed, style: GoogleFonts.spaceGrotesk(color: const Color(0xFFC2C1FF), fontSize: 18, fontWeight: FontWeight.bold))]),
                  Column(children: [Text('Volume', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)), const SizedBox(height: 4), Text('${totalVolume.round()} kg', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold))]),
                  Column(children: [Text('Sets', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12)), const SizedBox(height: 4), Text('$completedSets / $totalSets', style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold))]),
                ],
              ),
            ),
          ),

          Expanded(
            child: exercises.isEmpty
                ? Center(child: Text('Keine Übungen vorhanden', style: GoogleFonts.inter(color: Colors.grey)))
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16), itemCount: exercises.length,
                    itemBuilder: (ctx, exIdx) {
                      final exMap = exercises[exIdx]; final loggedSets = exMap['loggedSets'] as List<Map<String, dynamic>>? ?? [];
                      int exCompleted = loggedSets.where((s) => s['done'] == true).length;

                      return _ActiveExerciseCard(exMap: exMap, loggedSets: loggedSets, exCompleted: exCompleted, onAddSet: () { setState(() { final lastSet = loggedSets.isNotEmpty ? loggedSets.last : null; loggedSets.add({'set': loggedSets.length + int.parse('1'), 'weight': lastSet?['weight'] ?? 0, 'reps': lastSet?['reps'] ?? 10, 'done': false}); }); }, onSetChange: () => setState(() {}));
                    },
                  ),
          ),
          Container(
            padding: const EdgeInsets.all(16), decoration: const BoxDecoration(color: Color(0xFF131313), border: Border(top: BorderSide(color: Color(0xFF222222)))),
            child: Column(
              children: [
                Row(children: [Expanded(child: OutlinedButton.icon(icon: const Icon(Icons.add), label: Text('Übung hinzufügen', style: GoogleFonts.manrope(fontWeight: FontWeight.bold)), style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFF5856D6), side: const BorderSide(color: Color(0xFF5856D6)), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), padding: const EdgeInsets.symmetric(vertical: 12)), onPressed: () => _showAddExerciseModal(context)))]),
                const SizedBox(height: 8), TextButton(style: TextButton.styleFrom(foregroundColor: Colors.redAccent), onPressed: () => _showCancelDialog(context), child: Text('Workout abbrechen', style: GoogleFonts.manrope())),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showAddExerciseModal(BuildContext context) {
    final sports = context.read<SportsProvider>();
    showModalBottomSheet(
      context: context, backgroundColor: const Color(0xFF131313), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (ctx) {
        final exercises = sports.exercises;
        return Column(
          children: [
            Padding(padding: const EdgeInsets.all(16), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text('Übung auswählen', style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)), IconButton(icon: const Icon(Icons.close, color: Colors.grey), onPressed: () => Navigator.pop(ctx))])),
            const Divider(color: Color(0xFF222222), height: 1),
            Expanded(child: ListView.builder(itemCount: exercises.length, itemBuilder: (_, i) {
              final ex = exercises[i];
              return ListTile(
                leading: const CircleAvatar(backgroundColor: Color(0xFF1E1E1E), child: Icon(Icons.fitness_center, color: Color(0xFF5856D6), size: 18)),
                title: Text(ex['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold)), subtitle: Text('${ex['category']} · ${ex['equipment']}', style: GoogleFonts.inter(color: Colors.grey)),
                onTap: () { setState(() { final workout = sports.currentWorkout; if (workout != null) { final currentExercises = workout['exercises'] as List<Map<String, dynamic>>? ?? []; currentExercises.add({'name': ex['name'], 'sets': 3, 'reps': 10, 'weight': 0, 'loggedSets': List.generate(3, (index) => {'set': index + 1, 'weight': 0, 'reps': 10, 'done': false})}); } }); Navigator.pop(ctx); },
              );
            })),
          ],
        );
      },
    );
  }

  void _showCancelDialog(BuildContext context) {
    showDialog(
      context: context, builder: (ctx) => AlertDialog(backgroundColor: const Color(0xFF1F2020), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), title: Text('Workout abbrechen?', style: GoogleFonts.manrope(color: Colors.white)), content: Text('Bist du sicher, dass du dieses Training abbrechen möchtest?', style: GoogleFonts.inter(color: Colors.white70)), actions: [TextButton(child: Text('Nein, weiter', style: GoogleFonts.manrope(color: Colors.grey)), onPressed: () => Navigator.pop(ctx)), FilledButton(style: FilledButton.styleFrom(backgroundColor: Colors.redAccent, foregroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero)), onPressed: () { context.read<SportsProvider>().cancelWorkout(); Navigator.pop(ctx); Navigator.pop(context); }, child: Text('Abbrechen', style: GoogleFonts.manrope()))]),
    );
  }
}

class _ActiveExerciseCard extends StatefulWidget {
  final Map<String, dynamic> exMap;
  final List<Map<String, dynamic>> loggedSets;
  final int exCompleted;
  final VoidCallback onAddSet;
  final VoidCallback onSetChange;

  const _ActiveExerciseCard({required this.exMap, required this.loggedSets, required this.exCompleted, required this.onAddSet, required this.onSetChange});
  @override
  State<_ActiveExerciseCard> createState() => _ActiveExerciseCardState();
}

class _ActiveExerciseCardState extends State<_ActiveExerciseCard> {
  bool _expanded = true;
  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final exMap = widget.exMap; final loggedSets = widget.loggedSets; final exCompleted = widget.exCompleted;

    return Container(
      margin: const EdgeInsets.only(bottom: 16), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(8)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = !_expanded),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Container(width: 48, height: 48, decoration: BoxDecoration(color: const Color(0xFF252525), borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.fitness_center, color: Colors.white70)), const SizedBox(width: 16),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(exMap['name'] as String? ?? 'Übung', style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)), const SizedBox(height: 4), Text('$exCompleted/${loggedSets.length} done', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13))])),
                  IconButton(icon: const Icon(Icons.add, color: Color(0xFF5856D6)), tooltip: 'Set hinzufügen', onPressed: widget.onAddSet),
                  Icon(_expanded ? Icons.expand_less : Icons.expand_more, color: Colors.grey),
                ],
              ),
            ),
          ),
          if (_expanded) ...[
            Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Row(children: [SizedBox(width: 40, child: Text('SET', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold))), Expanded(child: Text('VORHER', style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold))), SizedBox(width: 70, child: Text('KG', textAlign: TextAlign.center, style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold))), SizedBox(width: 70, child: Text('WDH', textAlign: TextAlign.center, style: GoogleFonts.manrope(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold))), const SizedBox(width: 50, child: Icon(Icons.check, color: Colors.grey, size: 16))])),
            const Divider(color: Color(0xFF222222), height: 1),
            ...loggedSets.asMap().entries.map((entry) {
              final setMap = entry.value; final isDone = setMap['done'] as bool? ?? false;
              return AnimatedContainer(
                duration: const Duration(milliseconds: 200), color: isDone ? const Color(0xFF5856D6).withValues(alpha: 0.15) : Colors.transparent, padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  children: [
                    SizedBox(width: 40, child: Text('${setMap['set']}', style: GoogleFonts.manrope(color: isDone ? const Color(0xFF5856D6) : Colors.white, fontWeight: FontWeight.bold, fontSize: 15))),
                    Expanded(child: Text('${setMap['weight']} kg × ${setMap['reps']}', style: GoogleFonts.inter(color: Colors.white38, fontSize: 13))),
                    SizedBox(width: 70, child: TextFormField(initialValue: '${setMap['weight']}', keyboardType: const TextInputType.numberWithOptions(decimal: true), textAlign: TextAlign.center, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold), decoration: const InputDecoration(filled: true, fillColor: Color(0xFF1E1E1E), contentPadding: EdgeInsets.symmetric(vertical: 8, horizontal: 4), border: OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide.none)), onChanged: (val) { final numVal = num.tryParse(val.replaceAll(',', '.')); if (numVal != null) { setMap['weight'] = numVal; widget.onSetChange(); } })),
                    const SizedBox(width: 12),
                    SizedBox(width: 70, child: TextFormField(initialValue: '${setMap['reps']}', keyboardType: TextInputType.number, textAlign: TextAlign.center, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold), decoration: const InputDecoration(filled: true, fillColor: Color(0xFF1E1E1E), contentPadding: EdgeInsets.symmetric(vertical: 8, horizontal: 4), border: OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide.none)), onChanged: (val) { final intVal = int.tryParse(val); if (intVal != null) { setMap['reps'] = intVal; widget.onSetChange(); } })),
                    const SizedBox(width: 16),
                    GestureDetector(onTap: () { setState(() { final nowDone = !isDone; setMap['done'] = nowDone; if (nowDone) sports.completeSet(); widget.onSetChange(); }); }, child: Container(width: 34, height: 34, decoration: BoxDecoration(color: isDone ? const Color(0xFF5856D6) : const Color(0xFF1E1E1E), border: Border.all(color: isDone ? const Color(0xFF5856D6) : const Color(0xFF222222)), borderRadius: BorderRadius.circular(4)), child: isDone ? const Icon(Icons.check, color: Colors.white, size: 20) : const SizedBox.shrink())),
                  ],
                ),
              );
            }),
            const SizedBox(height: 8),
          ],
        ],
      ),
    );
  }
}

class _LyftaBarChart extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final data = [3, 4, 2, 5, 6, 8, 7];
    final maxVal = data.reduce((a, b) => a > b ? a : b);

    return Container(
      height: 140, padding: const EdgeInsets.only(top: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end, mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].asMap().map((i, label) {
          return MapEntry(
            i,
            Column(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Container(width: 28, height: (data[i] / maxVal) * 100, decoration: BoxDecoration(color: const Color(0xFF5856D6), borderRadius: BorderRadius.circular(2), boxShadow: [BoxShadow(color: const Color(0xFF5856D6).withValues(alpha: 0.2), blurRadius: 6, spreadRadius: 1)])),
                const SizedBox(height: 8), Text(label, style: GoogleFonts.inter(color: Colors.grey, fontSize: 12)),
              ],
            ),
          );
        }).values.toList(),
      ),
    );
  }
}

class _ActiveWorkoutBanner extends StatefulWidget {
  const _ActiveWorkoutBanner();
  @override
  State<_ActiveWorkoutBanner> createState() => _ActiveWorkoutBannerState();
}

class _ActiveWorkoutBannerState extends State<_ActiveWorkoutBanner> {
  late Timer _timer; String _elapsed = '00:00';
  @override
  void initState() { super.initState(); _updateTimer(); _timer = Timer.periodic(const Duration(seconds: 1), (_) => _updateTimer()); }
  @override
  void dispose() { _timer.cancel(); super.dispose(); }
  void _updateTimer() {
    final sports = context.read<SportsProvider>(); if (sports.currentWorkout == null) return;
    final start = sports.currentWorkout!['startTime'] as DateTime; final diff = DateTime.now().difference(start);
    final minutes = diff.inMinutes.toString().padLeft(2, '0'); final seconds = (diff.inSeconds % 60).toString().padLeft(2, '0');
    if (mounted) setState(() { _elapsed = '$minutes:$seconds'; });
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>(); final workout = sports.currentWorkout; if (workout == null) return const SizedBox.shrink();
    final completedSets = workout['completedSets'] as int? ?? 0;

    return Container(
      padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF5856D6).withValues(alpha: 0.5), width: 1.5), borderRadius: BorderRadius.circular(4), boxShadow: [BoxShadow(color: const Color(0xFF5856D6).withValues(alpha: 0.15), blurRadius: 12, spreadRadius: 2)]),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [Container(width: 10, height: 10, decoration: const BoxDecoration(color: Color(0xFF5856D6), shape: BoxShape.circle)), const SizedBox(width: 8), Text('AKTIVES WORKOUT', style: GoogleFonts.manrope(color: const Color(0xFF5856D6), fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1)), const Spacer(), Text(_elapsed, style: GoogleFonts.spaceGrotesk(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold))]),
          const SizedBox(height: 12), Text(workout['name'] as String? ?? 'Training', style: GoogleFonts.manrope(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)), const SizedBox(height: 6), Text('$completedSets Sätze absolviert', style: GoogleFonts.inter(color: Colors.white70, fontSize: 14)), const SizedBox(height: 16),
          Row(children: [Expanded(child: FilledButton.icon(icon: const Icon(Icons.play_arrow), label: Text('Fortsetzen', style: GoogleFonts.manrope(fontWeight: FontWeight.bold)), style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), foregroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), padding: const EdgeInsets.symmetric(vertical: 12)), onPressed: () => _showActiveWorkoutSheet(context))), const SizedBox(width: 12), OutlinedButton(style: OutlinedButton.styleFrom(foregroundColor: Colors.white, side: const BorderSide(color: Color(0xFF222222)), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16)), onPressed: () => _showActiveWorkoutSheet(context, openFinish: true), child: Text('Beenden', style: GoogleFonts.manrope()))]),
        ],
      ),
    );
  }
}

class _RoutineCard extends StatelessWidget {
  final Map<String, dynamic> plan;
  const _RoutineCard({required this.plan});
  @override
  Widget build(BuildContext context) {
    final exercises = plan['exercises'] as List? ?? []; final duration = plan['estimatedDuration'] as int? ?? 60;
    return Container(
      margin: const EdgeInsets.only(bottom: 16), decoration: BoxDecoration(color: const Color(0xFF131313), border: Border.all(color: const Color(0xFF222222), width: 0.5), borderRadius: BorderRadius.circular(4)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(plan['name'] as String? ?? '', style: GoogleFonts.manrope(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)), Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4), decoration: BoxDecoration(color: const Color(0xFFC2C1FF).withValues(alpha: 0.06), borderRadius: BorderRadius.circular(2)), child: Text(plan['day'] as String? ?? '', style: GoogleFonts.inter(color: const Color(0xFFC2C1FF), fontSize: 11, fontWeight: FontWeight.bold)))]),
                const SizedBox(height: 8), Row(children: [const Icon(Icons.timer_outlined, size: 14, color: Colors.grey), const SizedBox(width: 4), Text('$duration Min.', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13)), const SizedBox(width: 16), const Icon(Icons.fitness_center, size: 14, color: Colors.grey), const SizedBox(width: 4), Text('${exercises.length} Übungen', style: GoogleFonts.inter(color: Colors.grey, fontSize: 13))]),
                const SizedBox(height: 16), Wrap(spacing: 6, runSpacing: 6, children: exercises.map((ex) { final name = ex['name'] as String? ?? ''; final sets = ex['sets'] as int? ?? 3; return Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6), decoration: BoxDecoration(color: const Color(0xFF1E1E1E), border: Border.all(color: Colors.white10), borderRadius: BorderRadius.circular(2)), child: Text('$sets× $name', style: GoogleFonts.inter(color: Colors.white70, fontSize: 12))); }).toList()),
              ],
            ),
          ),
          Container(width: double.infinity, padding: const EdgeInsets.fromLTRB(16, 0, 16, 16), child: FilledButton(style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), foregroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), padding: const EdgeInsets.symmetric(vertical: 14)), onPressed: () { final sports = context.read<SportsProvider>(); if (sports.currentWorkout != null) { _showActiveWorkoutSheet(context); } else { sports.startWorkout(plan['id'] as int).then((ok) { if (ok && context.mounted) _showActiveWorkoutSheet(context); }); } }, child: Text('Workout starten', style: GoogleFonts.manrope(fontWeight: FontWeight.bold, fontSize: 15)))),
        ],
      ),
    );
  }
}

void _showFinishDialog(BuildContext context) {
  final notesCtrl = TextEditingController();
  showDialog(
    context: context, builder: (ctx) => AlertDialog(backgroundColor: const Color(0xFF1F2020), shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero), title: Text('Workout beenden', style: GoogleFonts.manrope(color: Colors.white, fontWeight: FontWeight.bold)), content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Wie war das Training? Füge optionale Notizen hinzu:', style: GoogleFonts.inter(color: Colors.white70, fontSize: 14)), const SizedBox(height: 16), TextField(controller: notesCtrl, style: GoogleFonts.inter(color: Colors.white), maxLines: 3, decoration: InputDecoration(hintText: 'Z.B. Gutes Training...', hintStyle: GoogleFonts.inter(color: Colors.grey), filled: true, fillColor: const Color(0xFF0E0E0E), border: const OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide.none)))]), actions: [TextButton(child: Text('Abbrechen', style: GoogleFonts.manrope(color: Colors.grey)), onPressed: () => Navigator.pop(ctx)), FilledButton(style: FilledButton.styleFrom(backgroundColor: const Color(0xFF5856D6), foregroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero)), onPressed: () { context.read<SportsProvider>().finishWorkout(notes: notesCtrl.text.trim()).then((ok) { if (ctx.mounted) Navigator.pop(ctx); if (context.mounted) Navigator.pop(context); }); }, child: Text('Speichern', style: GoogleFonts.manrope(fontWeight: FontWeight.bold)))]),
  );
}