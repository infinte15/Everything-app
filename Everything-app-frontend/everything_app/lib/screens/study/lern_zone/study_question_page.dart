import 'package:flutter/material.dart';
import '../../../models/flashcard_deck.dart';
import 'study_answer_page.dart';

class StudyQuestionPage extends StatelessWidget {
  final Flashcard card;
  final List<Flashcard> deckCards;
  final int currentIndex;

  const StudyQuestionPage({
    super.key,
    required this.card,
    required this.deckCards,
    required this.currentIndex,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final progress = deckCards.isNotEmpty ? (currentIndex + 1) / deckCards.length : 0.0;

    return Scaffold(
      backgroundColor: const Color(0xFF0E0E0E),
      appBar: AppBar(
        title: const Text('FRAGE'),
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
                      // Top Section: Progress and Question Card
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Progress Indicator
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'FLASHCARDS',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                  fontWeight: FontWeight.bold,
                                  letterSpacing: 1.5,
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
                          const SizedBox(height: 8),
                          LinearProgressIndicator(
                            value: progress,
                            backgroundColor: theme.colorScheme.surfaceContainerHighest,
                            valueColor: AlwaysStoppedAnimation(theme.colorScheme.primary),
                            minHeight: 2,
                          ),
                          const SizedBox(height: 32),

                          // Question Card
                          Container(
                            color: theme.colorScheme.surfaceContainerLow,
                            constraints: const BoxConstraints(
                              minHeight: 280,
                            ),
                            child: Stack(
                              children: [
                                // Top right style indicator
                                Positioned(
                                  top: 0,
                                  right: 0,
                                  child: Container(
                                    width: 48,
                                    height: 48,
                                    color: theme.colorScheme.surfaceContainerHighest,
                                    child: Icon(
                                      Icons.style,
                                      color: theme.colorScheme.primary,
                                      size: 16,
                                    ),
                                  ),
                                ),
                                // Card Content
                                Padding(
                                  padding: const EdgeInsets.all(32),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.stretch,
                                    children: [
                                      const SizedBox(height: 16),
                                      Text(
                                        'FRAGE',
                                        textAlign: TextAlign.center,
                                        style: theme.textTheme.labelSmall?.copyWith(
                                          color: theme.colorScheme.onSurfaceVariant,
                                          fontWeight: FontWeight.bold,
                                          letterSpacing: 2.0,
                                        ),
                                      ),
                                      const SizedBox(height: 32),
                                      Text(
                                        card.question,
                                        style: theme.textTheme.headlineSmall?.copyWith(
                                          fontWeight: FontWeight.w900,
                                          color: theme.colorScheme.onSurface,
                                          height: 1.3,
                                        ),
                                        textAlign: TextAlign.center,
                                      ),
                                      const SizedBox(height: 32),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 32),

                      // Bottom Section: Action Button
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          InkWell(
                            onTap: () {
                              Navigator.pushReplacement(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => StudyAnswerPage(
                                    card: card,
                                    deckCards: deckCards,
                                    currentIndex: currentIndex,
                                  ),
                                ),
                              );
                            },
                            child: Container(
                              height: 60,
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  colors: [
                                    theme.colorScheme.primary,
                                    theme.colorScheme.primaryContainer,
                                  ],
                                ),
                              ),
                              child: const Center(
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Text(
                                      'ANTWORT ANZEIGEN',
                                      style: TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.bold,
                                        letterSpacing: 2.0,
                                        fontSize: 13,
                                      ),
                                    ),
                                    SizedBox(width: 8),
                                    Icon(Icons.visibility, color: Colors.white, size: 16),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 8),
                        ],
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
}
