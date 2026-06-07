import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../models/flashcard_deck.dart';
import '../../../../providers/study_provider.dart';
import '../flashcard_deck_page.dart';

class AddDeckSheet extends StatefulWidget {
  const AddDeckSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF252626),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (_) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
        child: const AddDeckSheet(),
      ),
    );
  }

  @override
  State<AddDeckSheet> createState() => _AddDeckSheetState();
}

class _AddDeckSheetState extends State<AddDeckSheet> {
  final _titleCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  String? _subjectId;
  int _newPerDay = 20;

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  void _create() {
    final provider = context.read<StudyProvider>();
    if (_titleCtrl.text.trim().isEmpty) return;

    final id = 'd${DateTime.now().millisecondsSinceEpoch}';
    provider.addFlashcardDeck(
      FlashcardDeck(
        id: id,
        title: _titleCtrl.text.trim(),
        subjectId: _subjectId ?? (provider.subjects.isNotEmpty ? provider.subjects.first.id : ''),
        description: _descCtrl.text.trim().isEmpty ? null : _descCtrl.text.trim(),
        newCardsPerDay: _newPerDay,
      ),
    );

    Navigator.pop(context);
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => FlashcardDeckPage(deckId: id)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<StudyProvider>();
    _subjectId ??= provider.subjects.isNotEmpty ? provider.subjects.first.id : null;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'NEUES DECK',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                  ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _titleCtrl,
              decoration: const InputDecoration(labelText: 'Deck-Name'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descCtrl,
              decoration: const InputDecoration(labelText: 'Beschreibung (optional)'),
            ),
            if (provider.subjects.isNotEmpty) ...[
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _subjectId,
                decoration: const InputDecoration(labelText: 'Fach'),
                items: provider.subjects
                    .map((s) => DropdownMenuItem(value: s.id, child: Text(s.name)))
                    .toList(),
                onChanged: (v) => setState(() => _subjectId = v),
              ),
            ],
            const SizedBox(height: 16),
            Text(
              'Neue Karten pro Tag: $_newPerDay',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            Slider(
              value: _newPerDay.toDouble(),
              min: 5,
              max: 50,
              divisions: 9,
              label: '$_newPerDay',
              onChanged: (v) => setState(() => _newPerDay = v.round()),
            ),
            const SizedBox(height: 12),
            FilledButton(onPressed: _create, child: const Text('DECK ERSTELLEN')),
          ],
        ),
      ),
    );
  }
}
