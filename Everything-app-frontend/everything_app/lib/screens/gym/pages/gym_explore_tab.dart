import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/exercise_detail_sheet.dart';

class GymExploreTab extends StatefulWidget {
  const GymExploreTab({super.key});

  @override
  State<GymExploreTab> createState() => _GymExploreTabState();
}

class _GymExploreTabState extends State<GymExploreTab> {
  String _search = '';
  String _category = 'All';

  static const _categories = [
    'All',
    'Chest',
    'Back',
    'Legs',
    'Shoulders',
    'Arms',
    'Core',
  ];

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final filtered = sports.exercises.where((ex) {
      final name = (ex['name'] as String? ?? '').toLowerCase();
      final cat = ex['category'] as String? ?? '';
      final matchSearch = name.contains(_search.toLowerCase());
      final matchCat = _category == 'All' || cat == _category;
      return matchSearch && matchCat;
    }).toList();

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          floating: true,
          backgroundColor: LyftaTheme.background,
          title: Text('Explore', style: LyftaTheme.title),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              TextField(
                onChanged: (v) => setState(() => _search = v),
                style: const TextStyle(color: LyftaTheme.textPrimary),
                decoration: const InputDecoration(
                  hintText: 'Search 5000+ exercises',
                  prefixIcon: Icon(Icons.search, color: LyftaTheme.textTertiary),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 36,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: _categories.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 8),
                  itemBuilder: (_, i) {
                    final cat = _categories[i];
                    final sel = _category == cat;
                    return FilterChip(
                      label: Text(cat),
                      selected: sel,
                      onSelected: (_) => setState(() => _category = cat),
                      selectedColor: LyftaTheme.primary.withValues(alpha: 0.25),
                      checkmarkColor: LyftaTheme.primary,
                    );
                  },
                ),
              ),
              const SizedBox(height: 16),
              Text('${filtered.length} exercises', style: LyftaTheme.caption),
              const SizedBox(height: 12),
              ...filtered.map((ex) => _ExerciseTile(
                    exercise: ex,
                    onTap: () => ExerciseDetailSheet.show(context, ex),
                  )),
            ]),
          ),
        ),
      ],
    );
  }
}

class _ExerciseTile extends StatelessWidget {
  final Map<String, dynamic> exercise;
  final VoidCallback onTap;

  const _ExerciseTile({required this.exercise, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final diff = exercise['difficulty'] as String? ?? 'beginner';
    Color badge;
    switch (diff) {
      case 'advanced':
        badge = LyftaTheme.danger;
        break;
      case 'intermediate':
        badge = LyftaTheme.warning;
        break;
      default:
        badge = LyftaTheme.primary;
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: LyftaTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: ListTile(
        onTap: onTap,
        leading: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: LyftaTheme.surfaceElevated,
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.play_circle_outline, color: LyftaTheme.primary, size: 28),
        ),
        title: Text(exercise['name'] as String? ?? '', style: LyftaTheme.title.copyWith(fontSize: 16)),
        subtitle: Text(
          '${exercise['category']} · ${exercise['equipment']}',
          style: LyftaTheme.subtitle,
        ),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: badge.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            diff[0].toUpperCase() + diff.substring(1),
            style: TextStyle(color: badge, fontSize: 11, fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }
}
