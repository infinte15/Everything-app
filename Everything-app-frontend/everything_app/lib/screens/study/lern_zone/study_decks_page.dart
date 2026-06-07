import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../providers/study_provider.dart';
import '../flashcards/flashcard_deck_page.dart';
import '../flashcards/flashcard_study_page.dart';
import '../flashcards/widgets/add_deck_sheet.dart';
import '../widgets/study_kinetic_card.dart';

/// Anki-style flashcard hub: decks, stats, study sessions.
class StudyDecksPage extends StatelessWidget {
  const StudyDecksPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final decks = provider.flashcardDecks;
    final totalDue = provider.dueFlashcards.length;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: RefreshIndicator(
        onRefresh: () => provider.loadData(),
        color: theme.colorScheme.primary,
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'FLASHCARDS',
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Container(width: 96, height: 4, color: theme.colorScheme.primary),
                    const SizedBox(height: 16),
                    Text(
                      'Lerne wie mit Anki: Karten aufdecken, Schwierigkeit wählen, Wiederholungen planen sich automatisch.',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (totalDue > 0)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                  child: StudyKineticCard(
                    backgroundColor: theme.colorScheme.primaryContainer,
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        const Icon(Icons.school, color: Colors.white, size: 32),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '$totalDue Karten fällig',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const Text(
                                'Starte eine Lerneinheit in einem Deck',
                                style: TextStyle(color: Colors.white70, fontSize: 12),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            if (decks.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: StudyKineticCard(
                    backgroundColor: theme.colorScheme.surfaceContainerLow,
                    child: const Text('Erstelle dein erstes Deck, um zu lernen.'),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final deck = decks[index];
                      final stats = provider.deckStats(deck.id);
                      final studyCount = stats.due + stats.newCards;

                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: StudyKineticCard(
                          backgroundColor: theme.colorScheme.surfaceContainerLow,
                          padding: const EdgeInsets.all(18),
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => FlashcardDeckPage(deckId: deck.id),
                            ),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      deck.title,
                                      style: theme.textTheme.titleMedium?.copyWith(
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                  if (studyCount > 0)
                                    IconButton(
                                      icon: Icon(Icons.play_circle_fill,
                                          color: theme.colorScheme.primary, size: 32),
                                      onPressed: () => Navigator.push(
                                        context,
                                        MaterialPageRoute(
                                          builder: (_) => FlashcardStudyPage(
                                            deckId: deck.id,
                                            deckTitle: deck.title,
                                          ),
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              Row(
                                children: [
                                  _pill(context, 'Neu', '${stats.newCards}', const Color(0xFF64D2FF)),
                                  const SizedBox(width: 8),
                                  _pill(context, 'Fällig', '${stats.due}', const Color(0xFFFF9F0A)),
                                  const SizedBox(width: 8),
                                  _pill(context, 'Gesamt', '${stats.total}', theme.colorScheme.primary),
                                  const Spacer(),
                                  Text(
                                    '${stats.masteryPercent} % gereift',
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                    childCount: decks.length,
                  ),
                ),
              ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                child: OutlinedButton.icon(
                  onPressed: () => AddDeckSheet.show(context),
                  icon: const Icon(Icons.add),
                  label: const Text('NEUES DECK'),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 18),
                    side: BorderSide(color: theme.colorScheme.primary),
                    foregroundColor: theme.colorScheme.primary,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _pill(BuildContext context, String label, String value, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(fontSize: 9)),
        Text(value, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 16)),
      ],
    );
  }
}
