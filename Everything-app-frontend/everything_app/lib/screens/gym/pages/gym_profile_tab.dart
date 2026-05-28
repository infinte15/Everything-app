import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/gym_session_card.dart';

class GymProfileTab extends StatefulWidget {
  const GymProfileTab({super.key});

  @override
  State<GymProfileTab> createState() => _GymProfileTabState();
}

class _GymProfileTabState extends State<GymProfileTab> with SingleTickerProviderStateMixin {
  late TabController _tab;

  @override
  void initState() {
    super.initState();
    _tab = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final chartPoints = sports.getVolumeChartPoints();
    final maxVol = chartPoints.isEmpty ? 1.0 : chartPoints.reduce((a, b) => a > b ? a : b);

    return NestedScrollView(
      headerSliverBuilder: (_, _) => [
        SliverAppBar(
          floating: true,
          backgroundColor: LyftaTheme.background,
          title: Text('You', style: LyftaTheme.title),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 32,
                  backgroundColor: LyftaTheme.surfaceElevated,
                  child: const Icon(Icons.person, size: 32, color: LyftaTheme.primary),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Lifter', style: LyftaTheme.headline.copyWith(fontSize: 22)),
                      Text(
                        '${sports.totalWorkouts} workouts · ${sports.totalVolumeAllTime.round()} kg total',
                        style: LyftaTheme.subtitle,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        SliverPersistentHeader(
          pinned: true,
          delegate: _TabBarDelegate(
            TabBar(
              controller: _tab,
              labelColor: LyftaTheme.primary,
              unselectedLabelColor: LyftaTheme.textTertiary,
              indicatorColor: LyftaTheme.primary,
              tabs: const [
                Tab(text: 'Progress'),
                Tab(text: 'History'),
                Tab(text: 'Body'),
              ],
            ),
          ),
        ),
      ],
      body: TabBarView(
        controller: _tab,
        children: [
          ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text('Volume (8 weeks)', style: LyftaTheme.title),
              const SizedBox(height: 16),
              SizedBox(
                height: 160,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: chartPoints.asMap().entries.map((e) {
                    final h = maxVol > 0 ? (e.value / maxVol).clamp(0.05, 1.0) : 0.05;
                    final isLast = e.key == chartPoints.length - 1;
                    return Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            if (isLast && e.value > 0)
                              Text(
                                '${e.value.round()}',
                                style: LyftaTheme.caption.copyWith(fontSize: 9),
                              ),
                            const SizedBox(height: 4),
                            Container(
                              height: 120 * h,
                              decoration: BoxDecoration(
                                color: isLast ? LyftaTheme.primary : LyftaTheme.surfaceElevated,
                                borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 24),
              _settingsTile(Icons.straighten, 'Units', 'Metric (kg, cm)'),
              _settingsTile(Icons.timer_outlined, 'Default rest', '${sports.defaultRestSeconds}s'),
            ],
          ),
          sports.workoutSessions.isEmpty
              ? Center(child: Text('No workouts yet', style: LyftaTheme.subtitle))
              : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
                  itemCount: sports.workoutSessions.length,
                  itemBuilder: (_, i) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: GymSessionCard(session: sports.workoutSessions[i]),
                  ),
                ),
          _BodyMeasuresTab(),
        ],
      ),
    );
  }

  Widget _settingsTile(IconData icon, String title, String value) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: ListTile(
        leading: Icon(icon, color: LyftaTheme.primary),
        title: Text(title, style: LyftaTheme.title.copyWith(fontSize: 16)),
        trailing: Text(value, style: LyftaTheme.subtitle),
      ),
    );
  }
}

class _TabBarDelegate extends SliverPersistentHeaderDelegate {
  final TabBar tabBar;
  _TabBarDelegate(this.tabBar);

  @override
  double get minExtent => tabBar.preferredSize.height;
  @override
  double get maxExtent => tabBar.preferredSize.height;

  @override
  Widget build(context, shrink, overlap) {
    return Container(color: LyftaTheme.background, child: tabBar);
  }

  @override
  bool shouldRebuild(covariant _TabBarDelegate old) => false;
}

class _BodyMeasuresTab extends StatelessWidget {
  static const _measures = [
    ('Weight', '78.5 kg', '+0.3'),
    ('Chest', '108 cm', '+1.0'),
    ('Waist', '82 cm', '-0.5'),
    ('Hips', '98 cm', '0'),
    ('Left bicep', '37 cm', '+0.5'),
  ];

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 100),
      itemCount: _measures.length + 1,
      itemBuilder: (_, i) {
        if (i == 0) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: OutlinedButton.icon(
              onPressed: () {},
              icon: const Icon(Icons.add, color: LyftaTheme.primary),
              label: const Text('Log measurement', style: TextStyle(color: LyftaTheme.primary)),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
                side: const BorderSide(color: LyftaTheme.primary),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          );
        }
        final m = _measures[i - 1];
        return Container(
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: LyftaTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(m.$1, style: LyftaTheme.title.copyWith(fontSize: 16)),
                    Text(m.$2, style: LyftaTheme.subtitle),
                  ],
                ),
              ),
              Text(
                m.$3,
                style: TextStyle(
                  color: m.$3.startsWith('-')
                      ? LyftaTheme.primary
                      : m.$3 == '0'
                          ? LyftaTheme.textTertiary
                          : LyftaTheme.warning,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
