import '../utils/anki_scheduler.dart';

/// Eine protokollierte Bewertung.
///
/// Die Karte selbst kennt nur ihren aktuellen Zustand. Erst dieses Protokoll beantwortet
/// „wie viel habe ich heute gelernt" und zeigt über [intervalDaysBefore]/[intervalDaysAfter],
/// ob eine Karte gewachsen oder zurückgefallen ist.
class FlashcardReview {
  final String id;
  final String flashcardId;
  final String deckId;
  final ReviewRating rating;
  final DateTime reviewedAt;
  final double intervalDaysBefore;
  final double intervalDaysAfter;

  const FlashcardReview({
    required this.id,
    required this.flashcardId,
    required this.deckId,
    required this.rating,
    required this.reviewedAt,
    this.intervalDaysBefore = 0,
    this.intervalDaysAfter = 0,
  });

  factory FlashcardReview.fromJson(Map<String, dynamic> json) => FlashcardReview(
        id: json['id'].toString(),
        flashcardId: json['flashcardId']?.toString() ?? '',
        deckId: json['deckId']?.toString() ?? '',
        rating: _ratingFrom(json['rating']),
        reviewedAt: json['reviewedAt'] != null
            ? DateTime.parse(json['reviewedAt'])
            : DateTime.now(),
        intervalDaysBefore: (json['intervalDaysBefore'] as num?)?.toDouble() ?? 0,
        intervalDaysAfter: (json['intervalDaysAfter'] as num?)?.toDouble() ?? 0,
      );

  /// Das Backend schreibt AGAIN/HARD/GOOD/EASY, das Dart-Enum heißt again/hard/good/easy.
  static ReviewRating _ratingFrom(dynamic raw) {
    final name = raw?.toString().toLowerCase();
    return ReviewRating.values.firstWhere(
      (r) => r.name == name,
      orElse: () => ReviewRating.good,
    );
  }

  /// Die Karte ist bei dieser Bewertung zurückgefallen.
  bool get isLapse => rating == ReviewRating.again;
}
