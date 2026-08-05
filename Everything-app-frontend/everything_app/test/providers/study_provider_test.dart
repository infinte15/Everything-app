import 'dart:ui';

import 'package:everything_app/models/flashcard_deck.dart';
import 'package:everything_app/models/study_grade.dart';
import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/utils/anki_scheduler.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_study_service.dart';

/// Der Study Space und das Backend sprachen an mehreren Stellen aneinander vorbei. Die Tests
/// hier halten die JSON-Schlüssel fest, die tatsächlich über die Leitung gehen — ein Umbenennen
/// auf einer der beiden Seiten faellt damit sofort auf.
void main() {
  late FakeStudyService fake;
  late StudyProvider provider;

  setUp(() {
    fake = FakeStudyService();
    provider = StudyProvider(studyService: fake);
  });

  tearDown(() => provider.dispose());

  group('Contract zum Backend', () {
    // Gelesen wurde 'title', geliefert wird 'name' — jeder Deck-Titel blieb dadurch leer.
    test('Deck-Namen kommen aus dem Feld name', () async {
      fake.decks = [
        {'id': 1, 'name': 'Analysis', 'courseId': 3, 'description': 'Kapitel 1'},
      ];

      await provider.loadData();

      expect(provider.flashcardDecks.single.title, 'Analysis',
          reason: 'das Backend nennt den Titel name, nicht title');
      expect(provider.flashcardDecks.single.subjectId, '3');
    });

    // Gelesen wurde 'front'/'back', geliefert wird 'question'/'answer'.
    test('Kartentexte kommen aus question und answer', () async {
      fake.cards = [
        {
          'id': 9,
          'deckId': 1,
          'question': 'Was ist eine Ableitung?',
          'answer': 'Steigung',
          'repetitionCount': 4,
          'intervalDays': 12.0,
          'learningStep': 4,
          'easeFactor': 2.3,
          'nextReviewDate': '2026-09-01T10:00:00',
        },
      ];

      await provider.loadData();

      final card = provider.flashcards.single;
      expect(card.question, 'Was ist eine Ableitung?');
      expect(card.answer, 'Steigung');
      expect(card.repetitions, 4, reason: 'Backend-Feld heisst repetitionCount');
      expect(card.intervalDays, 12.0);
      expect(card.ease, 2.3, reason: 'easeFactor kommt als Faktor 2.3, nicht als 230');
      expect(card.nextReview, DateTime.parse('2026-09-01T10:00:00'));
    });

    // Ohne die richtigen Felder galt jede geladene Karte als neu und sofort faellig.
    test('eine geplante Karte gilt weder als neu noch als faellig', () async {
      fake.cards = [
        {
          'id': 9,
          'deckId': 1,
          'question': 'F',
          'answer': 'A',
          'repetitionCount': 4,
          'intervalDays': 30.0,
          'learningStep': 4,
          'easeFactor': 2.5,
          'nextReviewDate':
              DateTime.now().add(const Duration(days: 30)).toIso8601String(),
        },
      ];

      await provider.loadData();

      final card = provider.flashcards.single;
      expect(AnkiScheduler.isNew(card), isFalse);
      expect(AnkiScheduler.isDue(card), isFalse);
    });

    // creditPoints war immer 0, weil das Backend ectsCredits liefert — deshalb zeigte der
    // GPA-Ring dauerhaft "—".
    test('ECTS kommen aus ectsCredits, Professor aus instructor', () async {
      fake.courses = [
        {
          'id': 3,
          'name': 'Analysis I',
          'instructor': 'Prof. Meier',
          'ectsCredits': 6,
          'semester': 'WS 2025/26',
          'color': '#FF0000',
        },
      ];

      await provider.loadData();

      final subject = provider.subjects.single;
      expect(subject.creditPoints, 6, reason: 'ohne das bleibt totalEcts 0 und der GPA leer');
      expect(subject.professor, 'Prof. Meier');
      expect(subject.colorHex, '#FF0000');
    });

    // addGrade warf frueher einen TypeError auf (created['gradeValue'] as num) == null.
    test('Noten kommen aus grade, weight und examDate', () async {
      fake.grades = [
        {
          'id': 5,
          'courseId': 3,
          'examName': 'Klausur',
          'examType': 'Klausur',
          'grade': 2.3,
          'weight': 50,
          'countsTowardGrade': true,
          'examDate': '2026-02-14',
        },
      ];

      await provider.loadData();

      final grade = provider.grades.single;
      expect(grade.grade, 2.3);
      expect(grade.weightPercent, 50);
      expect(grade.date, DateTime.parse('2026-02-14'));
      expect(grade.countsTowardGrade, isTrue);
    });

    test('eine neue Note wird mit den Backend-Feldnamen gesendet', () async {
      final ok = await provider.addGrade(StudyGrade(
        id: '',
        subjectId: '3',
        examName: 'Klausur',
        examType: 'Klausur',
        grade: 1.7,
        weightPercent: 60,
        date: DateTime(2026, 2, 14),
      ));

      expect(ok, isTrue);
      expect(fake.lastCreatedGrade, isNotNull);
      expect(fake.lastCreatedGrade!['grade'], 1.7);
      expect(fake.lastCreatedGrade!['weight'], 60);
      expect(fake.lastCreatedGrade!['examDate'], '2026-02-14');
      expect(fake.lastCreatedGrade!.containsKey('gradeValue'), isFalse,
          reason: 'gradeValue kennt das Backend nicht');
      expect(fake.lastCreatedGrade!.containsKey('weighting'), isFalse);
    });

    test('ein neues Modul wird mit ectsCredits und instructor gesendet', () async {
      await provider.addSubject(
        name: 'Lineare Algebra',
        professor: 'Prof. Schmidt',
        creditPoints: 9,
        semesterId: '4',
        colorHex: '#00FF00',
      );

      expect(fake.lastCreatedCourse!['ectsCredits'], 9);
      expect(fake.lastCreatedCourse!['instructor'], 'Prof. Schmidt');
      expect(fake.lastCreatedCourse!['semesterId'], 4,
          reason: 'die Verknuepfung geht als ID mit, die Bezeichnung setzt der Server');
      expect(fake.lastCreatedCourse!.containsKey('creditPoints'), isFalse);
    });
  });

  group('Laden', () {
    // Vorher wurde pro Deck einzeln nachgeladen: 1 + N Requests beim Oeffnen des Space.
    test('Karten werden in einem Request geholt, unabhaengig von der Deck-Anzahl', () async {
      fake.decks = List.generate(
        10,
        (i) => {'id': i, 'name': 'Deck $i', 'courseId': 1, 'description': ''},
      );

      await provider.loadData();

      expect(fake.getAllDecksCallCount, 1);
      expect(fake.getAllFlashcardsCallCount, 1);
      expect(fake.getReviewsCallCount, 1);
      expect(fake.getCardsByDeckCallCount, 0,
          reason: 'bei zehn Decks waeren das zehn zusaetzliche Roundtrips');
      expect(fake.getDeckStatsCallCount, 0,
          reason: 'die Liste rechnet lokal; Server-Stats holt nur die geoeffnete Deck-Seite');
    });
  });

  group('Statistiken', () {
    Map<String, dynamic> card(int id, {required int reps, required int step}) => {
          'id': id,
          'deckId': 1,
          'question': 'F',
          'answer': 'A',
          'repetitionCount': reps,
          'intervalDays': reps == 0 ? 0.0 : 30.0,
          'learningStep': step,
          'easeFactor': 2.5,
          'nextReviewDate': DateTime.now().add(const Duration(days: 30)).toIso8601String(),
        };

    test('ohne Server-Antwort wird aus den geladenen Karten gerechnet', () async {
      fake.decks = [{'id': 1, 'name': 'Analysis', 'courseId': 1, 'description': ''}];
      fake.cards = [card(1, reps: 0, step: 0), card(2, reps: 5, step: 2)];

      await provider.loadData();

      final stats = provider.deckStats('1');
      expect(stats.total, 2);
      expect(stats.newCards, 1);
      expect(stats.mature, 1);
    });

    test('die Server-Zahlen haben Vorrang vor der lokalen Rechnung', () async {
      fake.decks = [{'id': 1, 'name': 'Analysis', 'courseId': 1, 'description': ''}];
      fake.cards = [card(1, reps: 0, step: 0)];
      fake.deckStats = {
        'deckId': 1, 'total': 40, 'due': 7, 'newCards': 12, 'learning': 3, 'mature': 25,
      };
      await provider.loadData();

      await provider.refreshDeckStats('1');

      final stats = provider.deckStats('1');
      expect(stats.total, 40, reason: 'lokal waere hier 1');
      expect(stats.studyCount, 19, reason: '7 faellig + 12 neu');
    });

    // Nach einer Bewertung stimmen die geholten Zahlen nicht mehr - dann lieber die lokale
    // Rechnung als eine veraltete Serverzahl.
    test('eine Bewertung verwirft die geholten Deck-Zahlen', () async {
      fake.decks = [{'id': 1, 'name': 'Analysis', 'courseId': 1, 'description': ''}];
      fake.cards = [card(9, reps: 0, step: 0)];
      fake.deckStats = {
        'deckId': 1, 'total': 40, 'due': 7, 'newCards': 12, 'learning': 3, 'mature': 25,
      };
      await provider.loadData();
      await provider.refreshDeckStats('1');

      await provider.reviewFlashcardWithRating('9', ReviewRating.good);

      expect(provider.deckStats('1').total, 1, reason: 'wieder lokal gerechnet');
    });

    // Der Kartenzustand allein koennte "heute gelernt" nicht beantworten: er kennt nur die
    // letzte Bewertung, nicht ihre Anzahl.
    test('das Review-Protokoll zaehlt die heutigen Bewertungen', () async {
      fake.cards = [card(9, reps: 0, step: 0)];
      fake.reviews = [
        {
          'id': 1, 'flashcardId': 9, 'deckId': 1, 'rating': 'GOOD',
          'reviewedAt': DateTime.now().subtract(const Duration(hours: 2)).toIso8601String(),
          'intervalDaysBefore': 0.0, 'intervalDaysAfter': 1.0,
        },
        {
          'id': 2, 'flashcardId': 9, 'deckId': 1, 'rating': 'AGAIN',
          'reviewedAt': DateTime.now().subtract(const Duration(days: 3)).toIso8601String(),
          'intervalDaysBefore': 5.0, 'intervalDaysAfter': 0.0,
        },
      ];
      await provider.loadData();

      expect(provider.reviews.length, 2);
      expect(provider.reviews.first.rating, ReviewRating.good,
          reason: 'das Backend schreibt GOOD, das Dart-Enum heisst good');
      expect(provider.reviewCountSince(), 1, reason: 'nur die von heute');

      await provider.reviewFlashcardWithRating('9', ReviewRating.easy);

      expect(provider.reviewCountSince(), 2,
          reason: 'die eigene Bewertung wird nachgezogen, ohne das Protokoll neu zu holen');
    });
  });

  group('Fehlerbehandlung', () {
    // Frueher wurde bei einem Fehlschlag eine Notiz mit einer aus millisecondsSinceEpoch
    // gebastelten ID eingefuegt. Die lief bei jedem spaeteren PUT/DELETE in ein 404.
    test('eine fehlgeschlagene Notiz landet nicht mit erfundener ID in der Liste', () async {
      fake.failCreateNote = true;

      final note = await provider.addNote(title: 'Kaputt');

      expect(note, isNull);
      expect(provider.notes, isEmpty, reason: 'keine Geisternotiz in der Liste');
      expect(provider.error, isNotNull);
    });

    test('eine erfolgreiche Notiz landet mit der Server-ID in der Liste', () async {
      final note = await provider.addNote(title: 'Vorlesung 1');

      expect(note, isNotNull);
      expect(note!.id, 101, reason: 'die ID muss vom Server kommen');
      expect(provider.notes.single.title, 'Vorlesung 1');
    });

    test('eine fehlgeschlagene Bewertung meldet das und aendert die Karte nicht', () async {
      fake.cards = [
        {
          'id': 9,
          'deckId': 1,
          'question': 'F',
          'answer': 'A',
          'repetitionCount': 3,
          'intervalDays': 8.0,
          'learningStep': 3,
          'easeFactor': 2.5,
          'nextReviewDate': '2026-09-01T10:00:00',
        },
      ];
      await provider.loadData();
      fake.failReview = true;

      final ok = await provider.reviewFlashcardWithRating('9', ReviewRating.good);

      expect(ok, isFalse, reason: 'der Aufrufer muss den Fehlschlag bemerken koennen');
      expect(provider.flashcards.single.repetitions, 3, reason: 'unveraendert');
      expect(provider.error, isNotNull);
    });
  });

  group('Bewertung', () {
    // Das Frontend sendete GOOD, der Server kannte nur AGAIN/HARD/MEDIUM/EASY und wertete
    // alles Unbekannte als falsch beantwortet - "Gut" setzte die Karte also zurueck.
    test('die Bewertung geht als Grossbuchstaben-Enum an den Server', () async {
      fake.cards = [
        {
          'id': 9,
          'deckId': 1,
          'question': 'F',
          'answer': 'A',
          'repetitionCount': 0,
          'intervalDays': 0.0,
          'learningStep': 0,
          'easeFactor': 2.5,
          'nextReviewDate': '2026-01-01T10:00:00',
        },
      ];
      await provider.loadData();

      await provider.reviewFlashcardWithRating('9', ReviewRating.good);
      expect(fake.lastReviewRating, 'GOOD');

      await provider.reviewFlashcardWithRating('9', ReviewRating.again);
      expect(fake.lastReviewRating, 'AGAIN');
    });

    test('der Kartenzustand wird aus der Server-Antwort uebernommen', () async {
      fake.cards = [
        {
          'id': 9,
          'deckId': 1,
          'question': 'F',
          'answer': 'A',
          'repetitionCount': 0,
          'intervalDays': 0.0,
          'learningStep': 0,
          'easeFactor': 2.5,
          'nextReviewDate': '2026-01-01T10:00:00',
        },
      ];
      await provider.loadData();

      final ok = await provider.reviewFlashcardWithRating('9', ReviewRating.good);

      expect(ok, isTrue);
      expect(provider.flashcards.single.repetitions, 1);
      expect(provider.flashcards.single.intervalDays, 1.0);
    });
  });

  group('Seitenbaum', () {
    Map<String, dynamic> page(int id, String title, {int? parentId, int orderIndex = 0}) => {
          'id': id,
          'title': title,
          'content': '',
          'parentId': parentId,
          'orderIndex': orderIndex,
          'isFavorite': false,
        };

    test('der Baum entsteht aus der flachen Liste, sortiert nach orderIndex', () async {
      fake.notes = [
        page(1, 'Zweite', orderIndex: 1),
        page(2, 'Erste', orderIndex: 0),
        page(3, 'Kapitel', parentId: 2),
      ];

      await provider.loadData();

      expect(provider.noteTree.map((n) => n.title), ['Erste', 'Zweite'],
          reason: 'orderIndex schlaegt die Ladereihenfolge');
      expect(provider.childrenOf(2).single.title, 'Kapitel');
      expect(provider.childrenOf(1), isEmpty);
    });

    test('Brotkrumen laufen von der Wurzel bis zur Seite', () async {
      fake.notes = [
        page(1, 'Analysis'),
        page(2, 'Kapitel 1', parentId: 1),
        page(3, 'Abschnitt 1.1', parentId: 2),
      ];
      await provider.loadData();

      expect(provider.breadcrumbsFor(3).map((n) => n.title),
          ['Analysis', 'Kapitel 1', 'Abschnitt 1.1']);
    });

    // Ein von Hand verbogener Zyklus darf die UI nicht aufhaengen.
    test('Brotkrumen brechen bei einem Zyklus ab', () async {
      fake.notes = [
        page(1, 'A', parentId: 2),
        page(2, 'B', parentId: 1),
      ];
      await provider.loadData();

      expect(provider.breadcrumbsFor(1).length, lessThanOrEqualTo(64));
    });

    // Jede Seite gehoert zu einem Modul: eine Wurzelseite nennt es selbst, eine Unterseite
    // erbt es von der Elternseite. Wuerde der Client hier ein courseId mitschicken, koennte
    // eine Unterseite in einem anderen Modul landen als ihre Elternseite.
    test('eine neue Unterseite wird mit parentId und OHNE Modul gesendet', () async {
      await provider.createChildPage(7, title: 'Kapitel 1');

      expect(fake.lastCreatedNote!['parentId'], 7);
      expect(fake.lastCreatedNote!['title'], 'Kapitel 1');
      expect(fake.lastCreatedNote!['courseId'], isNull,
          reason: 'das Modul kommt von der Elternseite, nicht vom Client');
    });

    test('eine neue Wurzelseite wird mit Modul gesendet', () async {
      await provider.addNote(title: 'Skript', courseId: 5);

      expect(fake.lastCreatedNote!['courseId'], 5);
      expect(fake.lastCreatedNote!['parentId'], isNull);
    });

    test('die Modulzuordnung geht an den Server und laedt die Notizen neu', () async {
      fake.notes = [page(1, 'Freie Seite')];
      await provider.loadData();

      final ok = await provider.assignNoteToCourse(1, 5);

      expect(ok, isTrue);
      expect(provider.freeNotes, isEmpty,
          reason: 'nach dem Neuladen haengt die Seite an ihrem Modul und ist keine freie Notiz mehr');
    });

    // Der Server loescht den Teilbaum mit; wuerde hier nur die eine Seite verschwinden,
    // blieben ihre Unterseiten als Waisen in der Liste stehen.
    test('Loeschen nimmt den ganzen Teilbaum aus der Liste', () async {
      fake.notes = [
        page(1, 'Analysis'),
        page(2, 'Kapitel 1', parentId: 1),
        page(3, 'Abschnitt 1.1', parentId: 2),
        page(4, 'Unbeteiligt'),
      ];
      await provider.loadData();

      await provider.deleteNote(1);

      expect(provider.notes.map((n) => n.id), [4]);
    });

    test('Umsortieren schreibt die Listenposition in orderIndex', () async {
      fake.notes = [page(1, 'A', orderIndex: 0), page(2, 'B', orderIndex: 1)];
      await provider.loadData();

      final ok = await provider.reorderNotes([2, 1]);

      expect(ok, isTrue);
      expect(fake.lastReorder, [2, 1]);
      expect(provider.noteTree.map((n) => n.title), ['B', 'A']);
    });

    test('eine fehlgeschlagene Sortierung wird zurueckgenommen', () async {
      fake.notes = [page(1, 'A', orderIndex: 0), page(2, 'B', orderIndex: 1)];
      await provider.loadData();
      fake.failReorder = true;

      final ok = await provider.reorderNotes([2, 1]);

      expect(ok, isFalse);
      expect(provider.noteTree.map((n) => n.title), ['A', 'B'],
          reason: 'optimistisch gesetzt, also auch wieder zurueckgenommen');
    });

    test('Verschieben schickt parentId und Position an den Server', () async {
      fake.notes = [page(1, 'A'), page(2, 'B')];
      await provider.loadData();

      final ok = await provider.moveNote(2, 1, 0);

      expect(ok, isTrue);
      expect(fake.lastMove, (id: 2, parentId: 1, position: 0));
      expect(provider.childrenOf(1).single.id, 2,
          reason: 'nach dem Verschieben werden die Notizen neu geladen');
    });
  });

  group('Stundenplan', () {
    Map<String, dynamic> slot(int id, String day, String start, String end) => {
          'id': id,
          'courseId': 5,
          'courseName': 'Analysis I',
          'courseColor': '#FF9F0A',
          'dayOfWeek': day,
          'startTime': start,
          'endTime': end,
          'location': 'HS 1',
          'semesterLabel': 'WS 2025/26',
        };

    test('der Stundenplan kommt vom Server statt aus dem Speicher', () async {
      fake.schedules = [slot(1, 'MONDAY', '08:00:00', '09:30:00')];

      await provider.loadData();

      final lesson = provider.schedules.single;
      expect(lesson.courseName, 'Analysis I');
      expect(lesson.dayIndex, 0, reason: 'MONDAY ist die erste Spalte');
      expect(lesson.durationMinutes, 90);
      expect(lesson.startTimeLabel, '08:00');
      expect(lesson.semesterLabel, 'WS 2025/26');
    });

    test('lessonsForDay filtert nach Wochentag und sortiert nach Uhrzeit', () async {
      fake.schedules = [
        slot(1, 'TUESDAY', '14:00:00', '16:00:00'),
        slot(2, 'TUESDAY', '08:00:00', '10:00:00'),
        slot(3, 'MONDAY', '08:00:00', '10:00:00'),
      ];
      await provider.loadData();

      final tuesday = provider.lessonsForDay(1);
      expect(tuesday.map((s) => s.id), ['2', '1']);
    });

    // Die Uebersicht rechnete den Tagesindex mit .clamp(0, 4) aus und zeigte am Wochenende
    // deshalb den Freitag als "heute". Die Indizes 5 und 6 sind echte Tage, keine
    // Ueberlaeufe — das haelt dieser Test fest.
    test('das Wochenende hat eigene Spalten und faellt nicht auf Freitag zurueck', () async {
      fake.schedules = [
        slot(1, 'FRIDAY', '08:00:00', '10:00:00'),
        slot(2, 'SATURDAY', '10:00:00', '12:00:00'),
      ];
      await provider.loadData();

      expect(provider.lessonsForDay(4).map((s) => s.id), ['1']);
      expect(provider.lessonsForDay(5).map((s) => s.id), ['2'],
          reason: 'Samstag ist Spalte 5, nicht der geklemmte Freitag');
      expect(provider.lessonsForDay(6), isEmpty, reason: 'Sonntag ohne Veranstaltung');
    });

    // Der Wochentag geht als ISO-Name an den Server; die Spalte ist nullbasiert.
    test('eine neue Veranstaltung wird mit ISO-Wochentag gesendet', () async {
      await provider.addSchedule(
        courseId: 5,
        weekday: 3,
        startHour: 8,
        startMinute: 0,
        endHour: 9,
        endMinute: 30,
        location: 'HS 1',
      );

      expect(fake.lastCreatedSchedule!['dayOfWeek'], 'WEDNESDAY');
      expect(fake.lastCreatedSchedule!['startTime'], '08:00:00');
      expect(fake.lastCreatedSchedule!['endTime'], '09:30:00');
      expect(fake.lastCreatedSchedule!['courseId'], 5);
      expect(provider.schedules.single.location, 'HS 1');
    });

    test('eine fehlgeschlagene Veranstaltung landet nicht in der Liste', () async {
      fake.failCreateSchedule = true;

      final ok = await provider.addSchedule(
        courseId: 5, weekday: 1, startHour: 8, startMinute: 0, endHour: 10, endMinute: 0,
      );

      expect(ok, isFalse);
      expect(provider.schedules, isEmpty);
      expect(provider.error, isNotNull);
    });

    test('Loeschen geht ueber Modul und Termin', () async {
      fake.schedules = [slot(7, 'MONDAY', '08:00:00', '10:00:00')];
      await provider.loadData();

      final ok = await provider.deleteSchedule(provider.schedules.single);

      expect(ok, isTrue);
      expect(fake.lastDeletedSchedule, (courseId: 5, id: 7));
      expect(provider.schedules, isEmpty);
    });
  });

  // Die Lernziele lagen vorher nur im Arbeitsspeicher: nach jedem Neustart war das Ziel weg,
  // die daraus erzeugte Aufgabe blieb im Backend liegen und wurde ewig weitergeplant. Jetzt
  // kommen sie vom Server, und die Bruecke in den Kalender baut der Server.
  group('Lernziele', () {
    Map<String, dynamic> goalJson({
      int id = 61,
      String name = 'Analysis I',
      double goalHours = 5,
      double logged = 0,
    }) => {
          'id': id,
          'courseId': 5,
          'courseName': name,
          'courseColor': '#3B82F6',
          'emoji': '📐',
          'weeklyGoalHours': goalHours,
          'loggedHours': logged,
          'taskId': 300,
        };

    test('Lernziele kommen mit Modulnamen und -farbe vom Server', () async {
      fake.goals = [goalJson(logged: 2)];

      await provider.loadData();

      final goal = provider.studyPlan.single;
      expect(goal.courseName, 'Analysis I', reason: 'kein Freitext mehr, sondern das Modul');
      expect(goal.color, const Color(0xFF3B82F6));
      expect(goal.remainingHours, 3);
      expect(goal.taskId, 300, reason: 'ohne Bruecken-Task landet das Ziel nie im Kalender');
    });

    test('ein neues Ziel wird mit courseId gesendet, nicht mit einem Fachnamen', () async {
      final ok = await provider.addStudyGoal(courseId: 5, goalHours: 4, emoji: '📐');

      expect(ok, isTrue);
      expect(fake.lastCreatedGoal, {'courseId': 5, 'weeklyGoalHours': 4.0, 'emoji': '📐'});
      expect(provider.studyPlan.single.courseId, 5);
    });

    test('erfasste Stunden uebernehmen den Serverstand statt lokal zu addieren', () async {
      fake.goals = [goalJson(logged: 1)];
      await provider.loadData();

      final ok = await provider.logStudyHours(61, 2.5);

      expect(ok, isTrue);
      expect(fake.lastLoggedHours, 2.5, reason: 'gesendet wird das Delta');
      expect(provider.studyPlan.single.loggedHours, 3.5,
          reason: 'angezeigt wird die Summe, die der Server zurueckmeldet');
    });

    test('ein fehlgeschlagenes Erfassen laesst die Anzeige unveraendert', () async {
      fake.goals = [goalJson(logged: 1)];
      await provider.loadData();
      fake.failLogHours = true;

      final ok = await provider.logStudyHours(61, 2.0);

      expect(ok, isFalse);
      expect(provider.studyPlan.single.loggedHours, 1,
          reason: 'sonst stuende ein Fortschritt da, den es auf dem Server nicht gibt');
    });

    // Der Server laesst nur ein Ziel je Modul zu; der Dialog darf belegte gar nicht anbieten.
    test('Module mit Ziel tauchen nicht mehr in der Auswahl auf', () async {
      fake.courses = [
        {'id': 5, 'name': 'Analysis I'},
        {'id': 6, 'name': 'Lineare Algebra'},
      ];
      fake.goals = [goalJson()];

      await provider.loadData();

      expect(provider.subjectsWithoutGoal.map((s) => s.name), ['Lineare Algebra']);
    });
  });

  // Das Board zeigte bisher NUR Seiten mit status:-Tag — eine neu angelegte Seite tauchte
  // also in keiner Spalte auf. Jetzt ruecken Seiten ohne Status automatisch in TO DO nach.
  group('Sprint-Board', () {
    Map<String, dynamic> note(int id, {String? tags, int courseId = 5, String? updatedAt}) => {
          'id': id,
          'title': 'Seite $id',
          'content': '',
          'courseId': courseId,
          'tags': tags,
          'updatedAt': updatedAt,
        };

    test('eine Seite ohne Status rueckt automatisch in TO DO nach', () async {
      fake.courses = [{'id': 5, 'name': 'Analysis I'}];
      fake.notes = [note(1), note(2, tags: 'status:done')];

      await provider.loadData();

      expect(provider.todoNotes.map((n) => n.id), [1]);
      expect(provider.doneNotes.map((n) => n.id), [2]);
      expect(provider.inProgressNotes, isEmpty);
    });

    // Vorher: contains('status:todo') auf dem zusammengesetzten Tag-String.
    test('ein Tag wie status:todo-later landet nicht in TO DO', () async {
      fake.courses = [{'id': 5, 'name': 'Analysis I'}];
      fake.notes = [note(1, tags: 'status:todo-later')];

      await provider.loadData();

      expect(provider.todoNotes, isEmpty,
          reason: 'status:todo ist nur ein Teilstring davon, kein eigener Status');
    });

    test('explizit einsortierte Seiten stehen vor den nachgerueckten', () async {
      fake.courses = [{'id': 5, 'name': 'Analysis I'}];
      fake.notes = [note(1), note(2, tags: 'status:todo'), note(3)];

      await provider.loadData();

      expect(provider.todoNotes.first.id, 2);
      expect(provider.todoNotes.map((n) => n.id).toSet(), {1, 2, 3});
    });

    test('das Board zeigt nur die Module des aktuellen Semesters', () async {
      fake.semesters = [
        {'id': 1, 'label': 'WS 2025/26', 'isCurrent': true},
        {'id': 2, 'label': 'SS 2025', 'isCurrent': false},
      ];
      fake.courses = [
        {'id': 5, 'name': 'Analysis I', 'semesterId': 1},
        {'id': 6, 'name': 'Statistik', 'semesterId': 2},
      ];
      fake.notes = [note(1, courseId: 5), note(2, courseId: 6)];

      await provider.loadData();

      expect(provider.todoNotes.map((n) => n.id), [1],
          reason: 'sonst stuende jede jemals angelegte Seite in TO DO');
    });

    test('Seiten ohne Modul stehen nicht auf dem Board', () async {
      fake.courses = [{'id': 5, 'name': 'Analysis I'}];
      fake.notes = [
        {'id': 1, 'title': 'Frei', 'content': '', 'courseId': null, 'tags': null},
      ];

      await provider.loadData();

      expect(provider.todoNotes, isEmpty);
    });

    test('anstehende Decks werden je Modul zusammengefasst, das dringendste zuerst', () async {
      final overdue = DateTime.now().subtract(const Duration(days: 1)).toIso8601String();
      final future = DateTime.now().add(const Duration(days: 7)).toIso8601String();
      fake.courses = [
        {'id': 5, 'name': 'Analysis I'},
        {'id': 6, 'name': 'Statistik'},
        {'id': 7, 'name': 'Nichts zu lernen'},
      ];
      fake.decks = [
        {'id': 1, 'name': 'A', 'courseId': 5},
        {'id': 2, 'name': 'S', 'courseId': 6},
        {'id': 3, 'name': 'O', 'courseId': 7},
      ];
      fake.cards = [
        for (var i = 0; i < 3; i++)
          {'id': 100 + i, 'deckId': 1, 'question': 'F', 'answer': 'A',
           'repetitionCount': 2, 'nextReviewDate': overdue},
        // Eine nie gelernte Karte: kein Wiederholungsdatum in der Vergangenheit, aber sie
        // steht sehr wohl zum Lernen an. Nur auf 'due' zu filtern liesse Statistik hier
        // verschwinden, obwohl die Uebersicht sie mitzaehlt.
        {'id': 200, 'deckId': 2, 'question': 'F', 'answer': 'A', 'repetitionCount': 0},
        // Gelernt und erst naechste Woche wieder dran.
        {'id': 300, 'deckId': 3, 'question': 'F', 'answer': 'A',
         'repetitionCount': 4, 'nextReviewDate': future},
      ];

      await provider.loadData();

      final due = provider.coursesWithDueCards;
      expect(due.map((e) => e.subject.name), ['Analysis I', 'Statistik'],
          reason: 'Module ohne anstehende Karten fehlen, Reihenfolge nach Dringlichkeit');
      expect(due.first.stats.studyCount, 3);
      expect(due.last.stats.studyCount, 1, reason: 'die neue Karte zaehlt mit');
    });
  });

  // Es gibt zwei Arten von Notizen. Eine Zeit lang war das Modul Pflicht — damit liess sich
  // ueberhaupt keine gewoehnliche Notiz mehr anlegen, und der Notizen-Space war leer.
  group('Freie Notizen', () {
    test('eine freie Notiz wird mit Kategorie und ohne Modul gesendet', () async {
      final ok = await provider.addNote(title: 'Einkaufsliste', category: 'Personal');

      expect(ok, isNotNull);
      expect(fake.lastCreatedNote!['category'], 'Personal');
      expect(fake.lastCreatedNote!['courseId'], isNull);
      expect(fake.lastCreatedNote!['tags'], 'status:todo',
          reason: 'der Startzustand fuers Sprint-Board ging beim Umbau verloren');
    });

    test('freeNotes enthaelt keine Modulseiten', () async {
      fake.notes = [
        {'id': 1, 'title': 'Einkaufsliste', 'content': '', 'category': 'Personal'},
        {'id': 2, 'title': 'Analysis Skript', 'content': '', 'courseId': 5},
      ];

      await provider.loadData();

      expect(provider.freeNotes.map((n) => n.title), ['Einkaufsliste']);
      expect(provider.notes.length, 2, reason: 'die Modulseite bleibt in der Gesamtliste');
    });

    test('eine Unterseite ist keine freie Notiz, auch ohne Modul', () async {
      fake.notes = [
        {'id': 1, 'title': 'Ideen', 'content': '', 'category': 'Personal'},
        {'id': 2, 'title': 'Unterpunkt', 'content': '', 'parentId': 1},
      ];

      await provider.loadData();

      // Der Notizen-Space listet Wurzeln; die Unterseiten haengen im Baum darunter.
      expect(provider.freeNotes.map((n) => n.title), ['Ideen']);
    });
  });

  group('Decks', () {
    test('ein neues Deck wird mit dem Feld name gesendet', () async {
      final ok = await provider.addFlashcardDeck(FlashcardDeck(
        id: '',
        title: 'Analysis',
        subjectId: '3',
        description: 'Kapitel 1',
      ));

      expect(ok, isTrue);
      expect(provider.flashcardDecks.single.title, 'Analysis');
    });
  });
}
