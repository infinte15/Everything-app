import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/flashcard_deck.dart';
import '../widgets/study_kinetic_card.dart';
import 'study_question_page.dart';

class StudyDecksPage extends StatelessWidget {
  const StudyDecksPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final provider = context.watch<StudyProvider>();
    final decks = provider.flashcardDecks;

    final isWide = MediaQuery.of(context).size.width > 700;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header Section
            Text(
              'DECK OVERVIEW',
              style: theme.textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w900,
                color: theme.colorScheme.onSurface,
                letterSpacing: -1.0,
              ),
            ),
            const SizedBox(height: 6),
            Container(
              width: 96,
              height: 4,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(height: 24),
            Text(
              'Willkommen zurück. Lerne deine Decks täglich, um dein Langzeitgedächtnis zu trainieren.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 40),

            // Bento Grid for Decks
            if (decks.isEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 24),
                child: StudyKineticCard(
                  backgroundColor: theme.colorScheme.surfaceContainerLow,
                  child: const Center(child: Text('Keine Flashcard Decks vorhanden.')),
                ),
              )
            else if (!isWide)
              Column(
                children: List.generate(decks.length, (index) {
                  final deck = decks[index];
                  final isMainFocus = index == 0;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: StudyKineticCard(
                      backgroundColor: isMainFocus
                          ? theme.colorScheme.surfaceContainerHighest
                          : theme.colorScheme.surfaceContainerLow,
                      padding: const EdgeInsets.all(24),
                      onTap: () => _startReviewSession(context, deck),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                isMainFocus ? 'ACTIVE DECK' : 'DECK',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.primary,
                                  fontWeight: FontWeight.bold,
                                  letterSpacing: 1.5,
                                ),
                              ),
                              Icon(
                                  Icons.play_circle_outline,
                                  color: theme.colorScheme.primary,
                                ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Text(
                            deck.title,
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onSurface,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${deck.totalCards} Karten insgesamt',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 20),
                          Row(
                            children: [
                              _buildStatItem(context, 'FÄLLIG', '${deck.toReviewCount}'),
                              const SizedBox(width: 24),
                              _buildStatItem(context, 'FORTSCHRITT', '${deck.masteryPercentage}%'),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              )
            else
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: isWide ? 2 : 1,
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                  childAspectRatio: isWide ? 1.5 : 1.8,
                ),
                itemCount: decks.length,
                itemBuilder: (context, index) {
                  final deck = decks[index];
                  final isMainFocus = index == 0;

                  return StudyKineticCard(
                    backgroundColor: isMainFocus
                        ? theme.colorScheme.surfaceContainerHighest
                        : theme.colorScheme.surfaceContainerLow,
                    padding: const EdgeInsets.all(24),
                    onTap: () => _startReviewSession(context, deck),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  isMainFocus ? 'ACTIVE DECK' : 'DECK',
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.primary,
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 1.5,
                                  ),
                                ),
                                Icon(
                                  Icons.play_circle_outline,
                                  color: theme.colorScheme.primary,
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            Text(
                              deck.title,
                              style: theme.textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                                color: theme.colorScheme.onSurface,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${deck.totalCards} Karten insgesamt',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                        Row(
                          children: [
                            _buildStatItem(context, 'FÄLLIG', '${deck.toReviewCount}'),
                            const SizedBox(width: 24),
                            _buildStatItem(context, 'FORTSCHRITT', '${deck.masteryPercentage}%'),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),

            const SizedBox(height: 16),

            // Streak & Stats Card
            StudyKineticCard(
              backgroundColor: theme.colorScheme.primaryContainer,
              padding: const EdgeInsets.all(24),
              child: Row(
                children: [
                  const Icon(Icons.bolt, size: 36, color: Colors.white),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'DEIN STREAK: 14 TAGE',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w900,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          'Dranbleiben lohnt sich. 85% Genauigkeit diese Woche.',
                          style: TextStyle(color: Colors.white70, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // Add New Deck Dotted Card
            InkWell(
              onTap: () => _showAddDeckDialog(context),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 36),
                decoration: BoxDecoration(
                  border: Border.all(
                    color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3),
                    style: BorderStyle.solid,
                  ),
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.add_circle_outline, size: 32, color: theme.colorScheme.onSurfaceVariant),
                    const SizedBox(height: 12),
                    Text(
                      'NEUES DECK ERSTELLEN',
                      style: theme.textTheme.labelSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onSurfaceVariant,
                        letterSpacing: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 100),
          ],
        ),
      ),
    );
  }

  Widget _buildStatItem(BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: theme.textTheme.labelSmall?.copyWith(
            fontSize: 9,
            color: theme.colorScheme.onSurfaceVariant,
            letterSpacing: 1.0,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.bold,
            color: theme.colorScheme.onSurface,
          ),
        ),
      ],
    );
  }

  void _startReviewSession(BuildContext context, FlashcardDeck deck) {
    final provider = context.read<StudyProvider>();
    final dueCards = provider.flashcards.where((f) => f.deckId == deck.id).toList();

    if (dueCards.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Keine fälligen Karten in diesem Deck.')),
      );
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => StudyQuestionPage(
          card: dueCards.first,
          deckCards: dueCards,
          currentIndex: 0,
        ),
      ),
    );
  }

  void _showAddDeckDialog(BuildContext context) {
    final titleCtrl = TextEditingController();
    final provider = context.read<StudyProvider>();
    String? selectedSubId = provider.subjects.isNotEmpty ? provider.subjects.first.id : null;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) {
          return AlertDialog(
            title: const Text('Neues Lern-Deck'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Titel')),
                if (provider.subjects.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: selectedSubId,
                    decoration: const InputDecoration(labelText: 'Fach'),
                    items: provider.subjects.map((s) {
                      return DropdownMenuItem(value: s.id, child: Text(s.name));
                    }).toList(),
                    onChanged: (val) => setSt(() => selectedSubId = val),
                  ),
                ],
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
              FilledButton(
                onPressed: () {
                  if (titleCtrl.text.trim().isNotEmpty) {
                    provider.addFlashcardDeck(FlashcardDeck(
                      id: 'd${DateTime.now().millisecondsSinceEpoch}',
                      title: titleCtrl.text.trim(),
                      subjectId: selectedSubId ?? '',
                      totalCards: 0,
                      toReviewCount: 0,
                      masteryPercentage: 0,
                    ));
                    Navigator.pop(ctx);
                  }
                },
                child: const Text('Erstellen'),
              ),
            ],
          );
        },
      ),
    );
  }
}
