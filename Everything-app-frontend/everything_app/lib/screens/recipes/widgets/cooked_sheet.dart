import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';
import 'rating_stars.dart';
import 'servings_stepper.dart';

/// Was beim Abhaken von "Gekocht" festgehalten wird.
///
/// Sterne und Notiz sind freiwillig - der Zähler ist der Zweck, alles andere
/// ein Angebot. Eine vergebene Bewertung gilt serverseitig auch als Bewertung
/// des Rezepts: wer nach dem Kochen Sterne setzt, meint das Rezept.
class CookedEntry {
  const CookedEntry({required this.servings, this.rating, this.note});

  final int servings;
  final int? rating;
  final String? note;
}

class CookedSheet extends StatefulWidget {
  const CookedSheet({super.key, required this.servings, this.rating});

  final int servings;
  final int? rating;

  static Future<CookedEntry?> show(
    BuildContext context, {
    required int servings,
    int? rating,
  }) {
    return showModalBottomSheet<CookedEntry>(
      context: context,
      backgroundColor: KineticTheme.surface,
      isScrollControlled: true,
      builder: (_) => CookedSheet(servings: servings, rating: rating),
    );
  }

  @override
  State<CookedSheet> createState() => _CookedSheetState();
}

class _CookedSheetState extends State<CookedSheet> {
  late int _servings = widget.servings;
  late int? _rating = widget.rating;
  final _noteController = TextEditingController();

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SafeArea(
        // Scrollbar, damit das Sheet bei aufgeklappter Tastatur nicht platzt.
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Gekocht', style: KineticTheme.title),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(child: Text('Portionen', style: KineticTheme.subtitle)),
                  ServingsStepper(
                    value: _servings,
                    onChanged: (value) => setState(() => _servings = value),
                  ),
                ],
              ),
              const Divider(height: 28, color: KineticTheme.divider),
              Text('BEWERTUNG', style: KineticTheme.label),
              const SizedBox(height: 8),
              RatingStars(
                rating: _rating,
                size: 28,
                onRate: (stars) => setState(
                  // Nochmal auf denselben Stern tippen nimmt die Bewertung
                  // zurück - sonst gibt es keinen Weg zurück zu "unbewertet".
                  () => _rating = _rating == stars ? null : stars,
                ),
              ),
              const Divider(height: 28, color: KineticTheme.divider),
              TextField(
                controller: _noteController,
                maxLines: 3,
                textCapitalization: TextCapitalization.sentences,
                style:
                    KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
                decoration: const InputDecoration(
                  hintText: 'Notiz - was war anders? (optional)',
                ),
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: () => Navigator.pop(
                  context,
                  CookedEntry(
                    servings: _servings,
                    rating: _rating,
                    note: _noteController.text.trim().isEmpty
                        ? null
                        : _noteController.text.trim(),
                  ),
                ),
                child: const Text('Eintragen'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
