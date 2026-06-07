import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../models/flashcard_deck.dart';
import '../../../providers/study_provider.dart';
import '../../../utils/anki_scheduler.dart';
import '../widgets/study_kinetic_card.dart';
import 'flashcard_study_page.dart';
import 'widgets/add_card_sheet.dart';

class FlashcardDeckPage extends StatelessWidget {
  final String deckId;

  const FlashcardDeckPage({super.key, required this.deckId});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final deck = provider.deckById(deckId);
    if (deck == null) {
      return Scaffold(
        backgroundColor: const Color(0xFF0E0E0E),
        appBar: AppBar(backgroundColor: const Color(0xFF131313)),
        body: const Center(child: Text('Deck nicht gefunden')),
      );
    }

    final stats = provider.deckStats(deckId);
    final cards = provider.cardsForDeck(deckId);
    final subject = provider.subjects
        .where((s) => s.id == deck.subjectId)
        .map((s) => s.name)
        .firstOrNull;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        backgroundColor: const Color(0xFF131313),
        title: Text(deck.title),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: () => _confirmDeleteDeck(context, provider, deck),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
        children: [
          if (subject != null)
            Text(
              subject,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
          if (deck.description != null && deck.description!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(deck.description!, style: theme.textTheme.bodyMedium),
          ],
          const SizedBox(height: 20),
          Row(
            children: [
              _StatChip(label: 'Neu', value: '${stats.newCards}', color: const Color(0xFF64D2FF)),
              const SizedBox(width: 8),
              _StatChip(label: 'Fällig', value: '${stats.due}', color: const Color(0xFFFF9F0A)),
              const SizedBox(width: 8),
              _StatChip(label: 'Gesamt', value: '${stats.total}', color: theme.colorScheme.primary),
            ],
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: stats.due + stats.newCards > 0
                  ? () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => FlashcardStudyPage(
                            deckId: deckId,
                            deckTitle: deck.title,
                          ),
                        ),
                      )
                  : null,
              icon: const Icon(Icons.play_arrow),
              label: Text(
                stats.due + stats.newCards > 0
                    ? 'LERNEN (${stats.due + stats.newCards})'
                    : 'NICHTS FÄLLIG',
              ),
            ),
          ),
          const SizedBox(height: 28),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'KARTEN',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  letterSpacing: 0.5,
                ),
              ),
              TextButton.icon(
                onPressed: () => AddCardSheet.show(context, deckId: deckId),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('Neu'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (cards.isEmpty)
            StudyKineticCard(
              backgroundColor: theme.colorScheme.surfaceContainerLow,
              child: Text(
                'Noch keine Karten. Füge die erste Karte hinzu.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            )
          else
            ...cards.map((c) => _CardTile(card: c)),
        ],
      ),
    );
  }

  void _confirmDeleteDeck(
    BuildContext context,
    StudyProvider provider,
    FlashcardDeck deck,
  ) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF252626),
        title: const Text('Deck löschen?'),
        content: Text('„${deck.title}“ und alle Karten werden entfernt.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () {
              provider.deleteFlashcardDeck(deck.id);
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            child: const Text('Löschen'),
          ),
        ],
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _StatChip({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
        color: const Color(0xFF252626),
        child: Column(
          children: [
            Text(label, style: const TextStyle(color: Color(0xFFACABAA), fontSize: 10)),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 18),
            ),
          ],
        ),
      ),
    );
  }
}

class _CardTile extends StatelessWidget {
  final Flashcard card;

  const _CardTile({required this.card});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    String status;
    Color statusColor;
    if (AnkiScheduler.isNew(card)) {
      status = 'Neu';
      statusColor = const Color(0xFF64D2FF);
    } else if (AnkiScheduler.isDue(card)) {
      status = 'Fällig';
      statusColor = const Color(0xFFFF9F0A);
    } else {
      status = AnkiScheduler.formatInterval(
        card.nextReview.difference(DateTime.now()),
      );
      statusColor = const Color(0xFF30D158);
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: StudyKineticCard(
        backgroundColor: theme.colorScheme.surfaceContainerLow,
        padding: const EdgeInsets.all(14),
        onTap: () => _showCardEditor(context, card),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    card.question,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    card.answer,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              color: statusColor.withValues(alpha: 0.15),
              child: Text(
                status,
                style: TextStyle(color: statusColor, fontSize: 11, fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showCardEditor(BuildContext context, Flashcard card) {
    final qCtrl = TextEditingController(text: card.question);
    final aCtrl = TextEditingController(text: card.answer);

    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF252626),
        title: const Text('Karte bearbeiten'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: qCtrl,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Vorderseite'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: aCtrl,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Rückseite'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              context.read<StudyProvider>().deleteFlashcard(card.id);
              Navigator.pop(ctx);
            },
            child: const Text('Löschen', style: TextStyle(color: Colors.redAccent)),
          ),
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
            onPressed: () {
              context.read<StudyProvider>().updateFlashcard(
                    card.copyWith(
                      question: qCtrl.text.trim(),
                      answer: aCtrl.text.trim(),
                    ),
                  );
              Navigator.pop(ctx);
            },
            child: const Text('Speichern'),
          ),
        ],
      ),
    );
  }
}
