import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../providers/study_provider.dart';

class AddCardSheet extends StatefulWidget {
  final String deckId;

  const AddCardSheet({super.key, required this.deckId});

  static Future<void> show(BuildContext context, {required String deckId}) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF252626),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      builder: (_) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
        child: AddCardSheet(deckId: deckId),
      ),
    );
  }

  @override
  State<AddCardSheet> createState() => _AddCardSheetState();
}

class _AddCardSheetState extends State<AddCardSheet> {
  final _frontCtrl = TextEditingController();
  final _backCtrl = TextEditingController();

  @override
  void dispose() {
    _frontCtrl.dispose();
    _backCtrl.dispose();
    super.dispose();
  }

  void _save() {
    if (_frontCtrl.text.trim().isEmpty || _backCtrl.text.trim().isEmpty) return;
    context.read<StudyProvider>().addFlashcardToDeck(
          deckId: widget.deckId,
          question: _frontCtrl.text.trim(),
          answer: _backCtrl.text.trim(),
        );
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'NEUE KARTE',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                  ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _frontCtrl,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'Vorderseite (Frage)',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _backCtrl,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'Rückseite (Antwort)',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 20),
            FilledButton(onPressed: _save, child: const Text('KARTE HINZUFÜGEN')),
          ],
        ),
      ),
    );
  }
}
