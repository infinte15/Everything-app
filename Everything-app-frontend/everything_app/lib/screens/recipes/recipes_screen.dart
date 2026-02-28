import 'package:flutter/material.dart';
import '../../config/app_theme.dart';

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
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sport'),
        backgroundColor: AppTheme.sportsColor,
        foregroundColor: Colors.white,
        bottom: TabBar(
          controller: _tabController,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          indicatorColor: Colors.white,
          tabs: const [
            Tab(icon: Icon(Icons.fitness_center), text: 'Training'),
            Tab(icon: Icon(Icons.history), text: 'Verlauf'),
            Tab(icon: Icon(Icons.bar_chart), text: 'Statistiken'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: const [
          _WorkoutTab(),
          _HistoryTab(),
          _StatsTab(),
        ],
      ),
    );
  }
}

// ─── Workout Tab ───────────────────────────────────────────────────────────────

class _WorkoutTab extends StatelessWidget {
  const _WorkoutTab();

  final List<Map<String, dynamic>> _workouts = const [
    {
      'name': 'Oberkörper',
      'day': 'Montag',
      'exercises': ['Bankdrücken', 'Schulterdrücken', 'Bizepscurls'],
      'duration': 60,
    },
    {
      'name': 'Unterkörper',
      'day': 'Mittwoch',
      'exercises': ['Kniebeugen', 'Kreuzheben', 'Beinpresse'],
      'duration': 75,
    },
    {
      'name': 'Core & Cardio',
      'day': 'Freitag',
      'exercises': ['Plank', 'Crunch', 'Laufen 30 Min.'],
      'duration': 45,
    },
  ];

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Today's Workout Highlight
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.sportsColor, AppTheme.sportsColor.withOpacity(0.7)],
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Heutiges Training',
                  style: TextStyle(color: Colors.white70, fontSize: 13)),
              const SizedBox(height: 8),
              const Text('Oberkörper',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              const Row(children: [
                Icon(Icons.timer, color: Colors.white70, size: 16),
                SizedBox(width: 4),
                Text('60 Min.',
                    style: TextStyle(color: Colors.white70)),
                SizedBox(width: 16),
                Icon(Icons.fitness_center, color: Colors.white70, size: 16),
                SizedBox(width: 4),
                Text('3 Übungen',
                    style: TextStyle(color: Colors.white70)),
              ]),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () {},
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: AppTheme.sportsColor,
                  ),
                  child: const Text('Training starten',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        const Text('Wochenplan',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        ..._workouts.map((w) => _WorkoutCard(workout: w)),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          icon: const Icon(Icons.add),
          label: const Text('Training hinzufügen'),
          onPressed: () {},
        ),
      ],
    );
  }
}

class _WorkoutCard extends StatelessWidget {
  final Map<String, dynamic> workout;
  const _WorkoutCard({required this.workout});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(workout['name'],
                    style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.bold)),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.sportsColor.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(workout['day'],
                      style: TextStyle(
                          color: AppTheme.sportsColor, fontSize: 12)),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                const Icon(Icons.timer_outlined,
                    size: 14, color: Colors.grey),
                const SizedBox(width: 4),
                Text('${workout['duration']} Min.',
                    style: const TextStyle(color: Colors.grey, fontSize: 13)),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              children: (workout['exercises'] as List<String>).map((e) {
                return Chip(
                  label: Text(e, style: const TextStyle(fontSize: 12)),
                  backgroundColor:
                      AppTheme.sportsColor.withOpacity(0.1),
                  side: BorderSide.none,
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                );
              }).toList(),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── History Tab ───────────────────────────────────────────────────────────────

class _HistoryTab extends StatelessWidget {
  const _HistoryTab();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final history = [
      {'date': DateTime.now().subtract(const Duration(days: 1)),
       'name': 'Oberkörper', 'duration': 62, 'sets': 15},
      {'date': DateTime.now().subtract(const Duration(days: 3)),
       'name': 'Unterkörper', 'duration': 78, 'sets': 18},
      {'date': DateTime.now().subtract(const Duration(days: 5)),
       'name': 'Core & Cardio', 'duration': 45, 'sets': 10},
    ];

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: history.length,
      itemBuilder: (_, i) {
        final h = history[i];
        final date = h['date'] as DateTime;
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: CircleAvatar(
              backgroundColor: AppTheme.sportsColor.withOpacity(0.1),
              child: Icon(Icons.fitness_center, color: AppTheme.sportsColor),
            ),
            title: Text(h['name'] as String,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Text(
              'vor ${DateTime.now().difference(date).inDays} Tagen • '
              '${h['duration']} Min. • ${h['sets']} Sätze',
            ),
            trailing: const Icon(Icons.chevron_right),
          ),
        );
      },
    );
  }
}

// ─── Stats Tab ─────────────────────────────────────────────────────────────────

class _StatsTab extends StatelessWidget {
  const _StatsTab();

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Weekly Summary
        Row(
          children: [
            Expanded(
              child: _SportStatCard(
                label: 'Trainings diese Woche',
                value: '3',
                icon: Icons.fitness_center,
                color: AppTheme.sportsColor,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _SportStatCard(
                label: 'Stunden diese Woche',
                value: '3.2',
                icon: Icons.timer,
                color: Colors.orange,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _SportStatCard(
                label: 'Gesamte Sätze',
                value: '43',
                icon: Icons.repeat,
                color: Colors.green,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _SportStatCard(
                label: 'Streak (Tage)',
                value: '12',
                icon: Icons.local_fire_department,
                color: Colors.red,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        const Text('Fortschritt (letzte 4 Wochen)',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16),
        _SimpleBarChart(),
      ],
    );
  }
}

class _SportStatCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _SportStatCard(
      {required this.label,
      required this.value,
      required this.icon,
      required this.color});

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
          Icon(icon, color: color),
          const SizedBox(height: 8),
          Text(value,
              style: TextStyle(
                  fontSize: 24, fontWeight: FontWeight.bold, color: color)),
          Text(label,
              style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ],
      ),
    );
  }
}

class _SimpleBarChart extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final data = [3, 4, 2, 3]; // Trainings pro Woche
    final maxVal = data.reduce((a, b) => a > b ? a : b);

    return Container(
      height: 120,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceVariant.withOpacity(0.5),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: ['Wo.1', 'Wo.2', 'Wo.3', 'Wo.4'].asMap().map((i, label) {
          return MapEntry(
            i,
            Column(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text('${data[i]}', style: const TextStyle(fontSize: 12)),
                const SizedBox(height: 4),
                Container(
                  width: 32,
                  height: (data[i] / maxVal) * 60,
                  decoration: BoxDecoration(
                    color: AppTheme.sportsColor,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
                const SizedBox(height: 4),
                Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          );
        }).values.toList(),
      ),
    );
  }
}