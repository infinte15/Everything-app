import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/gym/gym_models.dart';
import '../../../providers/sports_provider.dart';
import '../../../theme/lyfta_theme.dart';
import 'exercise_muscle_figure.dart';
import 'routine_card.dart' show muscleLabel;

/// Auswahl aus dem Übungskatalog.
///
/// Der Katalog hat mehrere hundert Einträge, deshalb wird serverseitig gesucht und
/// seitenweise nachgeladen statt alles auf einmal zu holen.
class ExercisePickerSheet extends StatefulWidget {
  const ExercisePickerSheet({super.key});

  static Future<List<GymExercise>?> show(BuildContext context) {
    return showModalBottomSheet<List<GymExercise>>(
      context: context,
      isScrollControlled: true,
      backgroundColor: LyftaTheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const ExercisePickerSheet(),
    );
  }

  @override
  State<ExercisePickerSheet> createState() => _ExercisePickerSheetState();
}

class _ExercisePickerSheetState extends State<ExercisePickerSheet> {
  final TextEditingController _searchController = TextEditingController();
  final List<GymExercise> _results = [];
  final List<GymExercise> _selected = [];

  Timer? _debounce;
  String? _muscle;
  int _page = 0;
  bool _loading = false;
  bool _last = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _search(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  /// Tippen löst nicht sofort eine Anfrage aus - sonst je Tastendruck eine.
  void _onQueryChanged(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () => _search(reset: true));
  }

  Future<void> _search({bool reset = false}) async {
    if (_loading) return;
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

  void _toggle(GymExercise exercise) {
    setState(() {
      final index = _selected.indexWhere((e) => e.id == exercise.id);
      if (index >= 0) {
        _selected.removeAt(index);
      } else {
        _selected.add(exercise);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final options = context.watch<SportsProvider>().muscleOptions;

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        builder: (context, sheetController) {
          return Column(
            children: [
              const SizedBox(height: 10),
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: LyftaTheme.surfaceHighlight,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 12, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Übung wählen',
                        style: LyftaTheme.headline.copyWith(fontSize: 22),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
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
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  children: [
                    _filterChip('Alle', _muscle == null, () {
                      setState(() => _muscle = null);
                      _search(reset: true);
                    }),
                    ...options.map(
                      (option) => _filterChip(option.label, _muscle == option.slug, () {
                        setState(() => _muscle = option.slug);
                        _search(reset: true);
                      }),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Expanded(child: _list(options, sheetController)),
              if (_selected.isNotEmpty)
                SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
                    child: FilledButton(
                      onPressed: () => Navigator.pop(context, _selected),
                      child: Text(
                        _selected.length == 1
                            ? '1 Übung übernehmen'
                            : '${_selected.length} Übungen übernehmen',
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Widget _list(List<GymMuscleOption> options, ScrollController sheetController) {
    if (_error != null) {
      return Center(child: Text(_error!, style: LyftaTheme.subtitle));
    }
    if (_results.isEmpty && _loading) {
      return const Center(child: CircularProgressIndicator(color: LyftaTheme.primary));
    }
    if (_results.isEmpty) {
      return Center(child: Text('Keine Treffer', style: LyftaTheme.subtitle));
    }

    // Der Sheet-Controller treibt die Geste, der eigene Controller das Nachladen.
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.pixels >= notification.metrics.maxScrollExtent - 300) {
          _search();
        }
        return false;
      },
      child: ListView.builder(
        controller: sheetController,
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        itemCount: _results.length + (_last ? 0 : 1),
        itemBuilder: (context, index) {
          if (index >= _results.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(
                child: CircularProgressIndicator(color: LyftaTheme.primary),
              ),
            );
          }

          final exercise = _results[index];
          final selected = _selected.any((e) => e.id == exercise.id);

          return ExerciseListTile(
            exercise: exercise,
            options: options,
            trailing: Icon(
              selected ? Icons.check_circle_rounded : Icons.add_circle_outline_rounded,
              color: selected ? LyftaTheme.primary : LyftaTheme.textTertiary,
            ),
            onTap: () => _toggle(exercise),
          );
        },
      ),
    );
  }

  Widget _filterChip(String label, bool selected, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: selected
                ? LyftaTheme.surfaceHighlight
                : LyftaTheme.surfaceElevated,
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

/// Einheitliche Zeile für Übungslisten.
class ExerciseListTile extends StatelessWidget {
  final GymExercise exercise;
  final List<GymMuscleOption> options;
  final Widget? trailing;
  final VoidCallback onTap;

  const ExerciseListTile({
    super.key,
    required this.exercise,
    required this.options,
    required this.onTap,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    final muscles = exercise.primaryMuscles
        .take(2)
        .map((m) => muscleLabel(m, options))
        .join(', ');

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: LyftaTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Row(
              children: [
                ExerciseMuscleFigure(
                  primaryMuscles: exercise.primaryMuscles,
                  secondaryMuscles: exercise.secondaryMuscles,
                  size: 48,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        exercise.name,
                        style: LyftaTheme.title.copyWith(fontSize: 15),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        [
                          if (muscles.isNotEmpty) muscles,
                          if (exercise.equipment.isNotEmpty) exercise.equipment,
                        ].join(' · '),
                        style: LyftaTheme.caption,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
          ),
        ),
      ),
    );
  }
}
