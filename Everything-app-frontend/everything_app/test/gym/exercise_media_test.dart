import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:everything_app/models/gym/gym_models.dart';
import 'package:everything_app/screens/gym/widgets/exercise_media.dart';
import 'package:everything_app/screens/gym/widgets/exercise_muscle_figure.dart';

/// Die Übungsmedien liegen auf einem CDN. In Tests gibt es kein Netz, also wird hier nicht
/// geprüft, ob ein Bild ankommt, sondern das, was auch ohne Netz gelten muss: dass eine
/// Übung ohne Bild-URL auf die gezeichnete Figur zurückfällt statt eine leere Kachel zu
/// zeigen, und dass die Modelle die URLs überhaupt durchreichen.
void main() {
  Widget wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

  group('ExerciseThumb', () {
    testWidgets('zeigt ohne Bild-URL die gezeichnete Muskelfigur', (tester) async {
      await tester.pumpWidget(wrap(const ExerciseThumb(
        imageUrl: null,
        primaryMuscles: ['chest'],
      )));

      expect(find.byType(ExerciseMuscleFigure), findsOneWidget);
      expect(find.byType(CachedNetworkImage), findsNothing);
    });

    testWidgets('behandelt einen leeren String wie kein Bild', (tester) async {
      await tester.pumpWidget(wrap(const ExerciseThumb(
        imageUrl: '',
        primaryMuscles: ['chest'],
      )));

      expect(find.byType(ExerciseMuscleFigure), findsOneWidget);
    });

    testWidgets('lädt mit Bild-URL das Bild statt der Figur', (tester) async {
      await tester.pumpWidget(wrap(const ExerciseThumb(
        imageUrl: 'https://example.invalid/0001.jpg',
        primaryMuscles: ['chest'],
      )));

      expect(find.byType(CachedNetworkImage), findsOneWidget);
    });
  });

  group('ExerciseAnimation', () {
    testWidgets('fällt ohne jede URL auf das Figuren-Banner zurück', (tester) async {
      await tester.pumpWidget(wrap(const ExerciseAnimation(
        animationUrl: null,
        imageUrl: null,
        primaryMuscles: ['lats'],
      )));

      expect(find.byType(ExerciseMuscleFigureBanner), findsOneWidget);
    });

    testWidgets('zeigt ohne Standbild keinen Pausen-Umschalter', (tester) async {
      // Umschalten hieße hier: von der Animation auf nichts. Der Hinweis wäre eine Lüge.
      await tester.pumpWidget(wrap(const ExerciseAnimation(
        animationUrl: 'https://example.invalid/0001.gif',
        imageUrl: null,
        primaryMuscles: ['lats'],
      )));

      expect(find.textContaining('Tippen'), findsNothing);
    });

    testWidgets('mit beidem lässt sich zwischen Animation und Standbild wechseln',
        (tester) async {
      await tester.pumpWidget(wrap(const ExerciseAnimation(
        animationUrl: 'https://example.invalid/0001.gif',
        imageUrl: 'https://example.invalid/0001.jpg',
        primaryMuscles: ['lats'],
      )));

      CachedNetworkImage image() =>
          tester.widget<CachedNetworkImage>(find.byType(CachedNetworkImage));

      expect(image().imageUrl, endsWith('.gif'), reason: 'startet mit der Animation');
      expect(find.text('Tippen pausiert'), findsOneWidget);

      await tester.tap(find.byType(ExerciseAnimation));
      await tester.pump();

      expect(image().imageUrl, endsWith('.jpg'), reason: 'pausiert auf dem Standbild');
      expect(find.text('Tippen startet'), findsOneWidget);
    });

    /// Der Größen-Umschalter ist entfallen: die Animation läuft nur noch im Übungsblatt,
    /// und dort ist Platz. Im Training gab es sie zum Wegklappen - jetzt gibt es sie dort
    /// gar nicht mehr.
    testWidgets('es gibt keinen Größen-Umschalter mehr', (tester) async {
      await tester.pumpWidget(wrap(const ExerciseAnimation(
        animationUrl: 'https://example.invalid/0001.gif',
        imageUrl: 'https://example.invalid/0001.jpg',
      )));
      await tester.pump();

      expect(find.text('Kleiner'), findsNothing);
      expect(find.text('Größer'), findsNothing);
    });
  });

  group('Modelle reichen die Medien durch', () {
    test('GymExercise liest animationUrl', () {
      final exercise = GymExercise.fromMap(const {
        'id': 1,
        'name': 'barbell bench press',
        'imageUrl': 'https://example.invalid/0025.jpg',
        'animationUrl': 'https://example.invalid/0025.gif',
      });

      expect(exercise.animationUrl, 'https://example.invalid/0025.gif');
      expect(exercise.imageUrl, 'https://example.invalid/0025.jpg');
    });

    test('ein Katalog ohne das Feld bleibt lesbar', () {
      // Eigene Übungen und ältere Antworten haben kein animationUrl - das darf kein Fehler
      // sein, sondern muss beim Fallback landen.
      final exercise = GymExercise.fromMap(const {'id': 1, 'name': 'eigene Übung'});

      expect(exercise.animationUrl, isNull);
      expect(exercise.imageUrl, isNull);
    });

    test('GymWorkoutExercise nimmt die Animation aus der Übung mit', () {
      final exercise = GymExercise.fromMap(const {
        'id': 1,
        'name': 'pull-up',
        'imageUrl': 'https://example.invalid/0652.jpg',
        'animationUrl': 'https://example.invalid/0652.gif',
      });

      final block = GymWorkoutExercise.fromExercise(exercise);

      expect(block.animationUrl, 'https://example.invalid/0652.gif');
      // Der Zwischenspeicher des laufenden Trainings muss sie auch überstehen, sonst ist die
      // Animation nach einem Absturz weg.
      expect(GymWorkoutExercise.fromCache(block.toCache()).animationUrl,
          'https://example.invalid/0652.gif');
    });

    test('GymRoutineExercise behält die Animation beim copyWith', () {
      final planned = GymRoutineExercise.fromMap(const {
        'exerciseId': 1,
        'exerciseName': 'barbell curl',
        'animationUrl': 'https://example.invalid/0031.gif',
      });

      expect(planned.copyWith(targetSets: 5).animationUrl,
          'https://example.invalid/0031.gif');
    });
  });
}
