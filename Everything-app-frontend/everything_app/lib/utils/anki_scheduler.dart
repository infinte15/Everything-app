import '../models/flashcard_deck.dart';

/// Anki-style review ratings (SM-2 inspired).
enum ReviewRating { again, hard, good, easy }

extension ReviewRatingLabel on ReviewRating {
  String get label {
    switch (this) {
      case ReviewRating.again:
        return 'Again';
      case ReviewRating.hard:
        return 'Hard';
      case ReviewRating.good:
        return 'Good';
      case ReviewRating.easy:
        return 'Easy';
    }
  }

  String get labelDe {
    switch (this) {
      case ReviewRating.again:
        return 'Nochmal';
      case ReviewRating.hard:
        return 'Schwer';
      case ReviewRating.good:
        return 'Gut';
      case ReviewRating.easy:
        return 'Einfach';
    }
  }
}

class ScheduledReview {
  final Flashcard card;
  final Duration nextInterval;
  final String intervalLabel;

  const ScheduledReview({
    required this.card,
    required this.nextInterval,
    required this.intervalLabel,
  });
}

/// Predicts next interval label for each rating (shown on buttons before tap).
class ReviewPreview {
  final ReviewRating rating;
  final String intervalLabel;

  const ReviewPreview({required this.rating, required this.intervalLabel});
}

abstract final class AnkiScheduler {
  static const _minEase = 1.3;

  static String formatInterval(Duration d) {
    if (d.inMinutes < 1) return '<1 Min';
    if (d.inMinutes < 60) return '${d.inMinutes} Min';
    if (d.inHours < 24) return '${d.inHours} Std';
    final days = d.inDays;
    if (days < 30) return '$days Tag${days == 1 ? '' : 'e'}';
    final months = (days / 30).round();
    if (months < 12) return '$months Mon';
    return '${(days / 365).toStringAsFixed(1)} J';
  }

  static List<ReviewPreview> previews(Flashcard card) {
    return ReviewRating.values.map((r) {
      final scheduled = schedule(card, r);
      return ReviewPreview(rating: r, intervalLabel: scheduled.intervalLabel);
    }).toList();
  }

  static ScheduledReview schedule(Flashcard card, ReviewRating rating) {
    final now = DateTime.now();
    double ease = card.ease;
    int reps = card.repetitions;
    double intervalDays = card.intervalDays;
    int learningStep = card.learningStep;

    Duration next;
    int newReps = reps;
    double newInterval = intervalDays;
    int newStep = learningStep;
    double newEase = ease;

    switch (rating) {
      case ReviewRating.again:
        newReps = 0;
        newStep = 0;
        newInterval = 0;
        newEase = (ease - 0.2).clamp(_minEase, 5.0);
        next = const Duration(minutes: 1);
        break;

      case ReviewRating.hard:
        newEase = (ease - 0.15).clamp(_minEase, 5.0);
        if (reps == 0 || learningStep < 2) {
          newStep = learningStep;
          next = const Duration(minutes: 6);
        } else {
          newReps = reps;
          newInterval = (intervalDays * 1.2).clamp(1, 365);
          next = Duration(days: newInterval.round());
        }
        break;

      case ReviewRating.good:
        if (reps == 0 || learningStep < 1) {
          newReps = 1;
          newStep = 2;
          newInterval = 1;
          next = const Duration(days: 1);
        } else {
          newReps = reps + 1;
          newInterval = (intervalDays * ease).clamp(1, 365);
          next = Duration(days: newInterval.round());
          newStep = 2;
        }
        break;

      case ReviewRating.easy:
        newEase = (ease + 0.15).clamp(_minEase, 5.0);
        newReps = reps + 1;
        newStep = 2;
        if (reps == 0) {
          newInterval = 4;
          next = const Duration(days: 4);
        } else {
          newInterval = (intervalDays * ease * 1.3).clamp(1, 365);
          next = Duration(days: newInterval.round());
        }
        break;
    }

    final updated = card.copyWith(
      ease: newEase,
      repetitions: newReps,
      intervalDays: newInterval,
      learningStep: newStep,
      nextReview: now.add(next),
      lastReviewed: now,
    );

    return ScheduledReview(
      card: updated,
      nextInterval: next,
      intervalLabel: formatInterval(next),
    );
  }

  static bool isDue(Flashcard card) {
    return !card.nextReview.isAfter(DateTime.now());
  }

  static bool isNew(Flashcard card) => card.repetitions == 0 && card.learningStep == 0;

  static bool isLearning(Flashcard card) =>
      card.repetitions == 0 || (card.learningStep < 2 && card.intervalDays < 1);
}
