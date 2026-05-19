import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/study_provider.dart';
import '../../../models/flashcard_deck.dart';
import 'study_question_page.dart';

class StudyAnswerPage extends StatelessWidget {
  final Flashcard card;
  final List<Flashcard> deckCards;
  final int currentIndex;

  const StudyAnswerPage({
    super.key,
    required this.card,
    required this.deckCards,
    required this.currentIndex,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        title: const Text('ANTWORT'),
        backgroundColor: const Color(0xFF0E0E0E),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: constraints.maxHeight,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Top Section: Header & Content Cards
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Context Header
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Text(
                                  'AKTIVES DECK',
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 1.5,
                                  ),
                                ),
                              ),
                              Text(
                                '${currentIndex + 1}/${deckCards.length}',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: theme.colorScheme.primary,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 24),

                          // Question Plate
                          Container(
                            width: double.infinity,
                            color: theme.colorScheme.surfaceContainerLow,
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'FRAGE',
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 1.5,
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  card.question,
                                  style: theme.textTheme.titleLarge?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 1), // 1px spacing

                          // Answer Plate
                          Container(
                            width: double.infinity,
                            color: theme.colorScheme.surfaceContainerHighest,
                            padding: const EdgeInsets.all(24),
                            child: Stack(
                              children: [
                                Positioned(
                                  left: 0,
                                  top: 0,
                                  bottom: 0,
                                  width: 3,
                                  child: Container(color: theme.colorScheme.primary),
                                ),
                                Padding(
                                  padding: const EdgeInsets.only(left: 16),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'ANTWORT',
                                        style: theme.textTheme.labelSmall?.copyWith(
                                          color: theme.colorScheme.primary,
                                          fontWeight: FontWeight.bold,
                                          letterSpacing: 1.5,
                                        ),
                                      ),
                                      const SizedBox(height: 16),
                                      Text(
                                        card.answer,
                                        style: theme.textTheme.headlineSmall?.copyWith(
                                          fontWeight: FontWeight.w900,
                                          color: theme.colorScheme.onSurface,
                                          height: 1.3,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 32),

                      // Bottom Section: Rating Grid (4 Options)
                      Container(
                        color: theme.colorScheme.outlineVariant.withValues(alpha: 0.15),
                        child: GridView.count(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          crossAxisCount: 2,
                          crossAxisSpacing: 1,
                          mainAxisSpacing: 1,
                          childAspectRatio: 2.2,
                          children: [
                            _buildRatingButton(context, 'Nochmal', '<1 Min', theme.colorScheme.error, false),
                            _buildRatingButton(context, 'Schwer', '2 Tage', theme.colorScheme.onSurface, true),
                            _buildRatingButton(context, 'Gut', '4 Tage', theme.colorScheme.primary, true),
                            _buildRatingButton(context, 'Einfach', '7 Tage', theme.colorScheme.secondary, true),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildRatingButton(BuildContext context, String label, String interval, Color color, bool correct) {
    final theme = Theme.of(context);
    final provider = context.read<StudyProvider>();

    return Material(
      color: theme.colorScheme.surfaceContainerHigh,
      child: InkWell(
        onTap: () {
          provider.reviewFlashcard(card.id, correct);

          if (currentIndex + 1 < deckCards.length) {
            Navigator.pushReplacement(
              context,
              MaterialPageRoute(
                builder: (_) => StudyQuestionPage(
                  card: deckCards[currentIndex + 1],
                  deckCards: deckCards,
                  currentIndex: currentIndex + 1,
                ),
              ),
            );
          } else {
            Navigator.pop(context);
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Session abgeschlossen! 🎉')),
            );
          }
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              label.toUpperCase(),
              style: TextStyle(
                color: color,
                fontWeight: FontWeight.w900,
                fontSize: 12,
                letterSpacing: 1.0,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              interval,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
                fontSize: 9,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
