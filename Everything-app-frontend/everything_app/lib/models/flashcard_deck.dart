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

  /// Das Backend nennt den Titel `name`. Vorher wurde `title` gelesen, weshalb jeder
  /// Deck-Titel in der UI leer blieb.
  factory FlashcardDeck.fromJson(Map<String, dynamic> json) => FlashcardDeck(
        id: json['id'].toString(),
        title: json['name'] ?? '',
        subjectId: json['courseId']?.toString() ?? '',
        description: json['description'] ?? '',
      );

  Map<String, dynamic> toJson() => {
        'name': title,
        'description': description,
        'courseId': int.tryParse(subjectId),
      };

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

  /// Das Backend nennt die Felder `question`/`answer`, den Zähler `repetitionCount` und den
  /// Termin `nextReviewDate`. Gelesen wurde vorher `front`/`back`/`repetitions`/`nextReview` —
  /// jede Karte rendete deshalb leer und galt als neu und sofort fällig.
  factory Flashcard.fromJson(Map<String, dynamic> json) => Flashcard(
        id: json['id'].toString(),
        deckId: json['deckId'].toString(),
        question: json['question'] ?? '',
        answer: json['answer'] ?? '',
        ease: (json['easeFactor'] as num?)?.toDouble() ?? 2.5,
        repetitions: (json['repetitionCount'] as num?)?.toInt() ?? 0,
        intervalDays: (json['intervalDays'] as num?)?.toDouble() ?? 0,
        learningStep: (json['learningStep'] as num?)?.toInt() ?? 0,
        nextReview: json['nextReviewDate'] != null
            ? DateTime.parse(json['nextReviewDate'])
            : DateTime.now(),
        lastReviewed: json['lastReviewedAt'] != null
            ? DateTime.parse(json['lastReviewedAt'])
            : null,
      );

  /// Nur die inhaltlichen Felder. Der Wiederholungszustand gehört dem Server.
  Map<String, dynamic> toJson() => {
        'deckId': int.tryParse(deckId),
        'question': question,
        'answer': answer,
      };

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

/// Die Kennzahlen eines Decks. Kommen vom Server (`GET /study/decks/{id}/stats`), lassen sich
/// aber auch aus den bereits geladenen Karten ableiten — beide Wege benutzen dieselbe
/// Einteilung aus [AnkiScheduler].
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

  factory FlashcardDeckStats.fromJson(Map<String, dynamic> json) => FlashcardDeckStats(
        total: (json['total'] as num?)?.toInt() ?? 0,
        due: (json['due'] as num?)?.toInt() ?? 0,
        newCards: (json['newCards'] as num?)?.toInt() ?? 0,
        learning: (json['learning'] as num?)?.toInt() ?? 0,
        mature: (json['mature'] as num?)?.toInt() ?? 0,
      );

  int get masteryPercent =>
      total == 0 ? 0 : ((mature / total) * 100).round();

  /// Was in einer Lerneinheit drankommt: fällige plus neue Karten.
  int get studyCount => due + newCards;
}
