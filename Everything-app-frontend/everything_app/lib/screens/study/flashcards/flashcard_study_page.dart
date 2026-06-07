import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/flashcard_deck.dart';
import '../../../providers/study_provider.dart';
import '../../../utils/anki_scheduler.dart';
import '../widgets/study_kinetic_card.dart';

/// Anki-style review: one card, tap to flip, rate with Again/Hard/Good/Easy.
class FlashcardStudyPage extends StatefulWidget {
  final String deckId;
  final String deckTitle;

  const FlashcardStudyPage({
    super.key,
    required this.deckId,
    required this.deckTitle,
  });

  @override
  State<FlashcardStudyPage> createState() => _FlashcardStudyPageState();
}

class _FlashcardStudyPageState extends State<FlashcardStudyPage> {
  late List<Flashcard> _queue;
  int _index = 0;
  bool _showAnswer = false;

  @override
  void initState() {
    super.initState();
    _queue = context.read<StudyProvider>().studyQueueForDeck(widget.deckId);
  }

  Flashcard? get _current =>
      _index < _queue.length ? _queue[_index] : null;

  void _rate(ReviewRating rating) {
    final card = _current;
    if (card == null) return;

    context.read<StudyProvider>().reviewFlashcardWithRating(card.id, rating);

    setState(() {
      _showAnswer = false;
      _index++;
    });

    if (_index >= _queue.length && mounted) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Deck „${widget.deckTitle}“ abgeschlossen'),
          backgroundColor: const Color(0xFF252626),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final card = _current;

    if (card == null) {
      return Scaffold(
        backgroundColor: const Color(0xFF0E0E0E),
        appBar: AppBar(
          backgroundColor: const Color(0xFF131313),
          title: Text(widget.deckTitle),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.check_circle_outline,
                  size: 64, color: theme.colorScheme.primary),
              const SizedBox(height: 16),
              Text('Keine Karten zum Lernen', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                'Alle fälligen Karten sind erledigt.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      );
    }

    final previews = AnkiScheduler.previews(card);
    final progress = (_index + 1) / _queue.length;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.deckTitle, style: const TextStyle(fontSize: 16)),
            Text(
              '${_index + 1} / ${_queue.length}',
              style: TextStyle(
                fontSize: 12,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          LinearProgressIndicator(
            value: progress,
            minHeight: 3,
            backgroundColor: theme.colorScheme.surfaceContainerHighest,
            color: theme.colorScheme.primary,
          ),
          Expanded(
            child: GestureDetector(
              onTap: () {
                if (!_showAnswer) setState(() => _showAnswer = true);
              },
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Center(
                  child: StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerLow,
                    padding: const EdgeInsets.all(28),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          _showAnswer ? 'ANTWORT' : 'FRAGE',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.primary,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 2,
                          ),
                        ),
                        const SizedBox(height: 24),
                        Text(
                          _showAnswer ? card.answer : card.question,
                          textAlign: TextAlign.center,
                          style: theme.textTheme.headlineSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                            height: 1.35,
                          ),
                        ),
                        if (!_showAnswer) ...[
                          const SizedBox(height: 32),
                          Text(
                            'Tippen zum Aufdecken',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (_showAnswer)
            _RatingBar(previews: previews, onRate: _rate)
          else
            Padding(
              padding: const EdgeInsets.all(20),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => setState(() => _showAnswer = true),
                  child: const Text('ANTWORT ZEIGEN'),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _RatingBar extends StatelessWidget {
  final List<ReviewPreview> previews;
  final void Function(ReviewRating) onRate;

  const _RatingBar({required this.previews, required this.onRate});

  @override
  Widget build(BuildContext context) {
    final colors = [
      const Color(0xFFFF453A),
      const Color(0xFFFF9F0A),
      const Color(0xFF30D158),
      const Color(0xFF64D2FF),
    ];

    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      decoration: BoxDecoration(
        color: const Color(0xFF131313),
        border: Border(
          top: BorderSide(
            color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.2),
          ),
        ),
      ),
      child: Row(
        children: List.generate(4, (i) {
          final p = previews[i];
          return Expanded(
            child: Padding(
              padding: EdgeInsets.only(left: i == 0 ? 0 : 4),
              child: Material(
                color: const Color(0xFF252626),
                child: InkWell(
                  onTap: () => onRate(p.rating),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    child: Column(
                      children: [
                        Text(
                          p.rating.labelDe.toUpperCase(),
                          style: TextStyle(
                            color: colors[i],
                            fontWeight: FontWeight.w800,
                            fontSize: 11,
                            letterSpacing: 0.5,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          p.intervalLabel,
                          style: const TextStyle(
                            color: Color(0xFFACABAA),
                            fontSize: 10,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
        }),
      ),
    );
  }
}
