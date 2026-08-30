import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import '../widgets/equipment_profile_sheet.dart';
import '../widgets/exercise_detail_sheet.dart';
import '../widgets/exercise_media.dart';
import '../widgets/exercise_picker_sheet.dart' show ExerciseListTile;

/// Durchsuchbarer Übungskatalog.
class GymExploreTab extends StatefulWidget {
  const GymExploreTab({super.key});

  @override
  State<GymExploreTab> createState() => _GymExploreTabState();
}

class _GymExploreTabState extends State<GymExploreTab> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final List<GymExercise> _results = [];

  Timer? _debounce;
  String? _muscle;
  int _page = 0;
  int _total = 0;
  bool _loading = false;
  bool _last = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 400) {
        _search();
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _search(reset: true));
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  /// Suche wird entprellt - sonst geht pro Tastendruck eine Anfrage raus.
  void _onQueryChanged(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () => _search(reset: true));
  }

  Future<void> _search({bool reset = false}) async {
    if (_loading || (!reset && _last)) return;
    setState(() {
      _loading = true;
      if (reset) {
        _page = 0;
        _last = false;
        _results.clear();
        _error = null;
      }
    });

    try {
      final page = await context.read<SportsProvider>().searchExercises(
            search: _searchController.text.trim(),
            muscle: _muscle,
            page: _page,
          );
      if (!mounted) return;
      setState(() {
        _results.addAll(page.content);
        _total = page.totalElements;
        _last = page.last;
        _page++;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'Übungen konnten nicht geladen werden';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final sports = context.watch<SportsProvider>();
    final options = sports.muscleOptions;
    final profile = sports.activeEquipmentProfile;

    return SafeArea(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Row(
              children: [
                Expanded(
                  child: Text('Übungen',
                      style: LyftaTheme.headline.copyWith(fontSize: 24)),
                ),
                // Das aktive Profil steht im Kopf und nicht in der Filterleiste: es gilt
                // dauerhaft und erklärt, warum die Liste kürzer ist als erwartet.
                TextButton.icon(
                  onPressed: () async {
                    await EquipmentProfileSheet.show(context);
                    if (mounted) _search(reset: true);
                  },
                  icon: const Icon(Icons.handyman_outlined, size: 16),
                  label: Text(profile?.name ?? 'Ausrüstung'),
                  style: TextButton.styleFrom(
                    foregroundColor: profile == null
                        ? LyftaTheme.textSecondary
                        : LyftaTheme.primary,
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: TextField(
              controller: _searchController,
              onChanged: _onQueryChanged,
              decoration: const InputDecoration(
                hintText: 'Übung suchen',
                prefixIcon: Icon(Icons.search_rounded, color: LyftaTheme.textTertiary),
              ),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 36,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                _chip('Alle', _muscle == null, () {
                  setState(() => _muscle = null);
                  _search(reset: true);
                }),
                ...options.map(
                  (option) => _chip(option.label, _muscle == option.slug, () {
                    setState(() => _muscle = option.slug);
                    _search(reset: true);
                  }),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (_total > 0)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text('$_total Übungen', style: LyftaTheme.label),
            ),
          const SizedBox(height: 8),
          Expanded(child: _body(options)),
        ],
      ),
    );
  }

  Widget _body(List<GymMuscleOption> options) {
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!, style: LyftaTheme.subtitle),
            const SizedBox(height: 12),
            TextButton(
              onPressed: () => _search(reset: true),
              child: const Text('Erneut versuchen'),
            ),
          ],
        ),
      );
    }
    if (_results.isEmpty && _loading) {
      return const Center(child: CircularProgressIndicator(color: LyftaTheme.primary));
    }
    if (_results.isEmpty) {
      return Center(child: Text('Keine Treffer', style: LyftaTheme.subtitle));
    }

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 100),
      // Eine Zeile mehr als Treffer: solange nachgeladen wird der Spinner, am Ende der
      // Liste der Rechtehinweis zu den Übungsbildern.
      itemCount: _results.length + 1,
      itemBuilder: (context, index) {
        if (index >= _results.length) {
          if (_last) return const GymVisualAttribution();
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 20),
            child: Center(child: CircularProgressIndicator(color: LyftaTheme.primary)),
          );
        }
        final exercise = _results[index];
        return ExerciseListTile(
          exercise: exercise,
          options: options,
          trailing: const Icon(
            Icons.chevron_right_rounded,
            color: LyftaTheme.textTertiary,
          ),
          onTap: () => ExerciseDetailSheet.show(context, exercise),
        );
      },
    );
  }

  Widget _chip(String label, bool selected, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: LyftaTheme.surfaceElevated,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: selected ? LyftaTheme.primary : LyftaTheme.divider,
            ),
          ),
          child: Text(
            label,
            style: LyftaTheme.caption.copyWith(
              color: selected ? LyftaTheme.primary : LyftaTheme.textSecondary,
            ),
          ),
        ),
      ),
    );
  }
}
