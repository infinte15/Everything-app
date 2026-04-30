class FlashcardDeck {
  final String id;
  final String title;
  final String subjectId;
  final int totalCards;
  final int toReviewCount;
  final int masteryPercentage;
  final DateTime? lastStudied;

  FlashcardDeck({
    required this.id,
    required this.title,
    required this.subjectId,
    this.totalCards = 0,
    this.toReviewCount = 0,
    this.masteryPercentage = 0,
    this.lastStudied,
  });
}

class Flashcard {
  final String id;
  final String deckId;
  final String question;
  final String answer;
  final int srsLevel;
  final DateTime nextReview;

  Flashcard({
    required this.id,
    required this.deckId,
    required this.question,
    required this.answer,
    this.srsLevel = 0,
    required this.nextReview,
  });
}
