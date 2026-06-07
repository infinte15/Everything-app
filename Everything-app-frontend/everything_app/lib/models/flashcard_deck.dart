class FlashcardDeck {
  final String id;
  final String title;
  final String subjectId;
  final String? description;
  final int newCardsPerDay;

  FlashcardDeck({
    required this.id,
    required this.title,
    required this.subjectId,
    this.description,
    this.newCardsPerDay = 20,
  });

  FlashcardDeck copyWith({
    String? id,
    String? title,
    String? subjectId,
    String? description,
    int? newCardsPerDay,
  }) {
    return FlashcardDeck(
      id: id ?? this.id,
      title: title ?? this.title,
      subjectId: subjectId ?? this.subjectId,
      description: description ?? this.description,
      newCardsPerDay: newCardsPerDay ?? this.newCardsPerDay,
    );
  }
}

class Flashcard {
  final String id;
  final String deckId;
  final String question;
  final String answer;
  final double ease;
  final int repetitions;
  final double intervalDays;
  final int learningStep;
  final DateTime nextReview;
  final DateTime? lastReviewed;
  final DateTime createdAt;

  Flashcard({
    required this.id,
    required this.deckId,
    required this.question,
    required this.answer,
    this.ease = 2.5,
    this.repetitions = 0,
    this.intervalDays = 0,
    this.learningStep = 0,
    required this.nextReview,
    this.lastReviewed,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();

  Flashcard copyWith({
    String? id,
    String? deckId,
    String? question,
    String? answer,
    double? ease,
    int? repetitions,
    double? intervalDays,
    int? learningStep,
    DateTime? nextReview,
    DateTime? lastReviewed,
    DateTime? createdAt,
  }) {
    return Flashcard(
      id: id ?? this.id,
      deckId: deckId ?? this.deckId,
      question: question ?? this.question,
      answer: answer ?? this.answer,
      ease: ease ?? this.ease,
      repetitions: repetitions ?? this.repetitions,
      intervalDays: intervalDays ?? this.intervalDays,
      learningStep: learningStep ?? this.learningStep,
      nextReview: nextReview ?? this.nextReview,
      lastReviewed: lastReviewed ?? this.lastReviewed,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  /// Legacy alias for older code paths.
  int get srsLevel => repetitions;
}

/// Aggregated counts for deck list UI (computed from cards).
class FlashcardDeckStats {
  final int total;
  final int due;
  final int newCards;
  final int learning;
  final int mature;

  const FlashcardDeckStats({
    required this.total,
    required this.due,
    required this.newCards,
    required this.learning,
    required this.mature,
  });

  int get masteryPercent =>
      total == 0 ? 0 : ((mature / total) * 100).round();
}
