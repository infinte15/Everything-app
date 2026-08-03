import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../providers/sports_provider.dart';
import '../../theme/lyfta_theme.dart';
import 'pages/gym_explore_tab.dart';
import 'pages/gym_home_tab.dart';
import 'pages/gym_profile_tab.dart';
import 'pages/gym_workout_tab.dart';
import 'widgets/gym_quick_start_sheet.dart';
import 'workout/active_workout_page.dart';

class GymScreen extends StatefulWidget {
  const GymScreen({super.key});

  @override
  State<GymScreen> createState() => _GymScreenState();
}

class _GymScreenState extends State<GymScreen> {
  int _tabIndex = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SportsProvider>().loadData();
    });
  }

  void _onStartWorkout() {
    final sports = context.read<SportsProvider>();
    // Läuft schon ein Training, führt der Knopf dorthin zurück statt ein zweites zu starten.
    if (sports.hasActiveWorkout) {
      _openActiveWorkout();
      return;
    }
    GymQuickStartSheet.show(context);
  }

  void _openActiveWorkout() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const ActiveWorkoutPage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final stackIndex = _tabIndex <= 1 ? _tabIndex : _tabIndex - 1;

    return Theme(
      data: LyftaTheme.darkTheme,
      child: Scaffold(
        backgroundColor: LyftaTheme.background,
        body: IndexedStack(
          index: stackIndex.clamp(0, 3),
          children: [
            GymHomeTab(
              onStartWorkout: _onStartWorkout,
              onOpenActiveWorkout: _openActiveWorkout,
            ),
            GymWorkoutTab(onOpenActiveWorkout: _openActiveWorkout),
            const GymExploreTab(),
            const GymProfileTab(),
          ],
        ),
        bottomNavigationBar: _LyftaBottomNav(
          selectedIndex: _tabIndex,
          onSelect: (i) {
            if (i == 2) {
              _onStartWorkout();
            } else {
              setState(() => _tabIndex = i);
            }
          },
        ),
      ),
    );
  }
}

class _LyftaBottomNav extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  const _LyftaBottomNav({
    required this.selectedIndex,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: LyftaTheme.surface,
        border: Border(top: BorderSide(color: LyftaTheme.divider)),
      ),
      child: SafeArea(
        child: SizedBox(
          height: 56,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _item(Icons.home_rounded, 'Start', 0),
              _item(Icons.fitness_center_rounded, 'Training', 1),
              _fab(context),
              _item(Icons.search_rounded, 'Übungen', 3),
              _item(Icons.person_rounded, 'Du', 4),
            ],
          ),
        ),
      ),
    );
  }

  Widget _item(IconData icon, String label, int index) {
    final selected = selectedIndex == index;
    return Expanded(
      child: InkWell(
        onTap: () => onSelect(index),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 24,
              color: selected ? LyftaTheme.primary : LyftaTheme.textTertiary,
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w500,
                color: selected ? LyftaTheme.primary : LyftaTheme.textTertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _fab(BuildContext context) {
    // Läuft ein Training, zeigt der Knopf einen Pfeil statt eines Plus.
    final running = context.select<SportsProvider, bool>((s) => s.hasActiveWorkout);

    return Expanded(
      child: GestureDetector(
        onTap: () => onSelect(2),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: LyftaTheme.primary,
                shape: BoxShape.circle,
              ),
              child: Icon(
                running ? Icons.play_arrow_rounded : Icons.add,
                color: LyftaTheme.onPrimary,
                size: 28,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
