import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/study_provider.dart';
import 'study_dashboard_page.dart';
import 'study_subjects_page.dart';
import 'study_timetable_page.dart';
import 'study_plan_page.dart';
import 'study_grades_page.dart';
import 'lern_zone/study_decks_page.dart';

class StudyScreen extends StatefulWidget {
  const StudyScreen({super.key});

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StudyProvider>().loadData();
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final selectedIndex = provider.activeTab;

    // Matches the order and casing of the Stitch top navigation:
    // Übersicht, Stundenplan, Fächer, Lernplan, Flashcards, Notenrechner
    final tabs = [
      'ÜBERSICHT',
      'STUNDENPLAN',
      'FÄCHER',
      'LERNPLAN',
      'FLASHCARDS',
      'NOTENRECHNER',
    ];

    final pages = [
      const StudyDashboardPage(),
      const StudyTimetablePage(),
      const StudySubjectsPage(),
      const StudyPlanPage(),
      const StudyDecksPage(),
      const StudyGradesPage(),
    ];

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: Stack(
        children: [
          // Background Glows (adopted from Stitch templates)
          Positioned.fill(
            child: IgnorePointer(
              child: Stack(
                children: [
                  Positioned(
                    top: -100,
                    right: -100,
                    width: 300,
                    height: 300,
                    child: Container(
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: theme.colorScheme.primary.withValues(alpha: 0.15),
                            blurRadius: 140,
                            spreadRadius: 100,
                          ),
                        ],
                      ),
                    ),
                  ),
                  Positioned(
                    bottom: -100,
                    left: -100,
                    width: 300,
                    height: 300,
                    child: Container(
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: theme.colorScheme.primaryContainer.withValues(alpha: 0.12),
                            blurRadius: 140,
                            spreadRadius: 100,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Main Header & Content Navigation Layout
          Column(
            children: [
              // Top Header with Title and Scrollable Tab Bar
              Container(
                color: const Color(0xFF0E0E0E),
                padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 8),
                child: SafeArea(
                  bottom: false,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Header Row
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const BackButton(
                            color: Color(0xFFC2C1FF),
                          ),
                          Text(
                            'STUDIUM SPACE',
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontFamily: 'Manrope',
                              fontWeight: FontWeight.w900,
                              letterSpacing: -1.0,
                              fontSize: 22,
                              color: const Color(0xFFC2C1FF),
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.settings, color: Color(0xFFC2C1FF)),
                            onPressed: () {},
                            tooltip: 'Einstellungen',
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      // Custom Tab Navigation Row
                      LayoutBuilder(
                        builder: (context, constraints) {
                          // Stretch tabs evenly across the full width of the screen on wide windows (> 720px).
                          // Otherwise, fall back to a horizontally scrollable row for narrow layouts.
                          final useStretched = constraints.maxWidth > 720;

                          if (useStretched) {
                            return Row(
                              children: List.generate(tabs.length, (index) {
                                final isSelected = index == selectedIndex;
                                return Expanded(
                                  child: InkWell(
                                    onTap: () {
                                      provider.setActiveTab(index);
                                    },
                                    child: Center(
                                      child: Container(
                                        padding: const EdgeInsets.only(bottom: 6),
                                        decoration: isSelected
                                            ? const BoxDecoration(
                                                border: Border(
                                                  bottom: BorderSide(
                                                    color: Color(0xFF5856D6),
                                                    width: 2,
                                                  ),
                                                ),
                                              )
                                            : null,
                                        child: Text(
                                          tabs[index],
                                          style: TextStyle(
                                            fontFamily: 'Manrope',
                                            fontWeight: FontWeight.bold,
                                            fontSize: 15,
                                            letterSpacing: -0.5,
                                            color: isSelected
                                                ? const Color(0xFFC2C1FF)
                                                : const Color(0xFFACABAA),
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                );
                              }),
                            );
                          } else {
                            return SingleChildScrollView(
                              scrollDirection: Axis.horizontal,
                              physics: const BouncingScrollPhysics(),
                              child: Row(
                                children: List.generate(tabs.length, (index) {
                                  final isSelected = index == selectedIndex;
                                  return Padding(
                                    padding: const EdgeInsets.only(right: 24, left: 4),
                                    child: InkWell(
                                      onTap: () {
                                        provider.setActiveTab(index);
                                      },
                                      child: Container(
                                        padding: const EdgeInsets.only(bottom: 6),
                                        decoration: isSelected
                                            ? const BoxDecoration(
                                                border: Border(
                                                  bottom: BorderSide(
                                                    color: Color(0xFF5856D6),
                                                    width: 2,
                                                  ),
                                                ),
                                              )
                                            : null,
                                        child: Text(
                                          tabs[index],
                                          style: TextStyle(
                                            fontFamily: 'Manrope',
                                            fontWeight: FontWeight.bold,
                                            fontSize: 15,
                                            letterSpacing: -0.5,
                                            color: isSelected
                                                ? const Color(0xFFC2C1FF)
                                                : const Color(0xFFACABAA),
                                          ),
                                        ),
                                      ),
                                    ),
                                  );
                                }),
                              ),
                            );
                          }
                        },
                      ),
                    ],
                  ),
                ),
              ),
              // Divider
              Container(
                height: 1,
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
              ),
              // Active Page Content
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  child: KeyedSubtree(
                    key: ValueKey(selectedIndex),
                    child: pages[selectedIndex],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}