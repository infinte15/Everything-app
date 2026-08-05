import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:provider/provider.dart';

import 'package:everything_app/config/routes.dart' as routes;
import 'package:everything_app/main.dart' as app;
import 'package:everything_app/providers/auth_provider.dart';
import 'package:everything_app/providers/calendar_provider.dart';
import 'package:everything_app/providers/study_provider.dart';
import 'package:everything_app/screens/notes/notes_screen.dart';

/// Laeuft gegen die ECHTE App und das ECHTE Backend auf :8080 — kein Fake-Service, keine
/// Attrappe. Zweck ist nicht, Zusicherungen zu haeufen, sondern den Study Space einmal wirklich
/// zu rendern und zu protokollieren, was dabei auf dem Schirm steht.
///
/// Voraussetzung: ./mvnw spring-boot:run laeuft auf :8080 und scripts/seed_study_data.sh
/// wurde einmal ausgefuehrt — die Erwartungen hier beziehen sich auf genau diese Fixture.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  /// Wartet, bis [check] wahr ist, statt pumpAndSettle: die App laedt ueber echtes HTTP, und
  /// dabei ist der Baum nie laenger als einen Frame ruhig.
  Future<bool> pumpUntil(
    WidgetTester tester,
    bool Function() check, {
    Duration timeout = const Duration(seconds: 20),
  }) async {
    final deadline = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(deadline)) {
      await tester.pump(const Duration(milliseconds: 200));
      if (check()) return true;
    }
    return false;
  }

  Future<void> settle(WidgetTester tester, {int seconds = 3}) =>
      pumpUntil(tester, () => false, timeout: Duration(seconds: seconds));

  /// Alle sichtbaren Texte — damit im Protokoll steht, was tatsaechlich gerendert wurde,
  /// und nicht nur, dass irgendetwas gerendert wurde.
  ///
  /// Text-Widgets allein reichen nicht: der Inhalt eines TextField steckt in einem
  /// EditableText, waehrend dessen Platzhalter als ganz normales Text-Widget im Baum haengt.
  /// Ohne die zweite Quelle liest sich ein gefuelltes Feld im Protokoll wie ein leeres.
  List<String> visibleTexts(WidgetTester tester) {
    final texts = tester
        .widgetList<Text>(find.byType(Text))
        .map((t) => t.data ?? '')
        .where((s) => s.trim().isNotEmpty)
        .toList();
    final fields = tester
        .widgetList<EditableText>(find.byType(EditableText))
        .map((e) => 'FELD("${e.controller.text}")');
    return [...texts, ...fields];
  }

  Future<void> openTab(WidgetTester tester, String label) async {
    await tester.tap(find.text(label).first, warnIfMissed: false);
    await settle(tester);
  }

  testWidgets('Study Space: alles zum Modul an einem Ort', (tester) async {
    app.main();
    await pumpUntil(tester, () => find.byType(MaterialApp).evaluate().isNotEmpty);

    final context = tester.element(find.byType(MaterialApp).first);
    final auth = Provider.of<AuthProvider>(context, listen: false);
    expect(await auth.devLogin(), isTrue, reason: 'dev-login gegen :8080 muss klappen');
    await settle(tester);

    final study = Provider.of<StudyProvider>(context, listen: false);
    await study.loadData();
    await settle(tester);

    debugPrint('### Daten vom Server: ${study.notes.length} Notizen, '
        '${study.subjects.length} Module, ${study.flashcardDecks.length} Decks, '
        '${study.flashcards.length} Karten, ${study.schedules.length} Stundenplan-Eintraege, '
        '${study.grades.length} Noten, ${study.semesters.length} Semester');
    expect(study.error, isNull, reason: 'Laden darf keinen Fehler setzen: ${study.error}');

    routes.router.go('/study');
    await pumpUntil(tester, () => find.text('STUDIUM').evaluate().isNotEmpty);

    // Der Seitenbaum hat keinen eigenen Reiter mehr.
    expect(find.text('NOTIZEN'), findsNothing,
        reason: 'die Seiten leben im FÄCHER-Tab, nicht in einem eigenen Reiter');

    // ── ÜBERSICHT: Heute-Zeile und Modul-Kacheln ────────────────────────────
    await openTab(tester, 'ÜBERSICHT');
    await settle(tester, seconds: 2);
    debugPrint('### ÜBERSICHT oben: ${visibleTexts(tester)}');
    expect(find.textContaining('fällig'), findsWidgets, reason: 'die Heute-Zeile');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // Die Modul-Kacheln stehen unten; ein Sliver baut sie erst, wenn er sie braucht.
    await tester.drag(find.byType(CustomScrollView).first, const Offset(0, -1400));
    await settle(tester, seconds: 2);
    debugPrint('### ÜBERSICHT unten: ${visibleTexts(tester)}');
    expect(find.text('MEINE FÄCHER'), findsOneWidget);
    expect(find.text('Analysis I'), findsWidgets, reason: 'Modul-Kachel');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // ── FÄCHER: Seiten, Karteikarten und Noten desselben Moduls ─────────────
    await openTab(tester, 'FÄCHER');
    await settle(tester, seconds: 2);
    debugPrint('### FÄCHER: ${visibleTexts(tester)}');
    expect(find.text('SEITEN'), findsWidgets);
    expect(find.text('KARTEIKARTEN'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // Auf ein Modul mit Inhalt wechseln und dessen drei Abschnitte pruefen.
    await tester.tap(find.text('Analysis I').first, warnIfMissed: false);
    await settle(tester, seconds: 2);
    final analysis = visibleTexts(tester);
    debugPrint('### FÄCHER / Analysis I: $analysis');
    expect(find.text('Analysis Skript'), findsWidgets, reason: 'Seitenbaum des Moduls');
    expect(find.text('Analysis Grundlagen'), findsWidgets, reason: 'Deck des Moduls');
    // Der Notenabschnitt steht unter den Karteikarten; bis dahin muss gescrollt werden.
    await tester.drag(find.byType(ListView).last, const Offset(0, -600));
    await settle(tester, seconds: 2);
    debugPrint('### FÄCHER / Analysis I unten: ${visibleTexts(tester)}');
    expect(find.text('NOTEN'), findsOneWidget);
    expect(find.text('Klausur'), findsWidgets, reason: 'Note des Moduls');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // Baum aufklappen — die Unterseiten stehen erst danach da.
    expect(find.text('Kapitel 1: Folgen'), findsNothing, reason: 'Ebenen starten zugeklappt');
    await tester.tap(find.byIcon(Icons.keyboard_arrow_right).first, warnIfMissed: false);
    await settle(tester, seconds: 2);
    expect(find.text('Kapitel 1: Folgen'), findsOneWidget);
    expect(find.text('Kapitel 2: Reihen'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // Seite oeffnen: Markdown-Vorschau und Umschalter
    await tester.tap(find.text('Analysis Skript').first, warnIfMissed: false);
    await settle(tester, seconds: 3);
    debugPrint('### EDITOR: ${visibleTexts(tester)}');
    expect(find.text('VORSCHAU'), findsOneWidget, reason: 'Umschalter statt Rohtext darunter');
    expect(find.text('BEARBEITEN'), findsOneWidget);
    expect(find.text('Kapitelübersicht'), findsOneWidget, reason: '"# " muss Ueberschrift werden');
    expect(find.textContaining('Klausur am 15.03.'), findsOneWidget,
        reason: '"> " muss Callout werden');
    final titleField = tester.widget<EditableText>(find.byType(EditableText).first);
    expect(titleField.controller.text, 'Analysis Skript',
        reason: 'der Titel muss im Feld stehen, nicht nur der Platzhalter');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // pageBack() sucht einen Cupertino-Zurueck-Button; dieser Screen hat eine Material-AppBar.
    await tester.tap(find.byIcon(Icons.arrow_back).first, warnIfMissed: false);
    await settle(tester, seconds: 2);

    // ── STUNDENPLAN ─────────────────────────────────────────────────────────
    await openTab(tester, 'STUNDENPLAN');
    await settle(tester, seconds: 2);
    debugPrint('### STUNDENPLAN: ${visibleTexts(tester)}');
    expect(find.text('Analysis I'), findsWidgets, reason: 'die Vorlesung muss im Raster stehen');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // ── FLASHCARDS: alle Decks ueber alle Module ────────────────────────────
    await openTab(tester, 'FLASHCARDS');
    await settle(tester, seconds: 2);
    debugPrint('### FLASHCARDS: ${visibleTexts(tester)}');
    expect(find.text('Analysis Grundlagen'), findsWidgets);
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // ── NOTENRECHNER: der ECTS-gewichtete Schnitt ───────────────────────────
    await openTab(tester, 'NOTENRECHNER');
    await settle(tester, seconds: 2);
    debugPrint('### NOTENRECHNER: ${visibleTexts(tester)}');
    expect(find.text('GESAMTSCHNITT'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // ── LERNPLAN: Sprint-Board und faellige Wiederholungen ──────────────────
    await openTab(tester, 'LERNPLAN');
    await settle(tester, seconds: 2);
    debugPrint('### LERNPLAN: ${visibleTexts(tester)}');
    expect(find.text('ACTIVE SPRINT'), findsOneWidget);
    expect(find.text('WIEDERHOLEN'), findsOneWidget,
        reason: 'gezaehlt wird "zu lernen" (faellig + neu) wie ueberall sonst — nur auf '
            '"faellig" zu filtern liesse den Abschnitt leer, waehrend die Uebersicht '
            'gleichzeitig "N Karten faellig" meldet');
    expect(find.textContaining('zu lernen'), findsWidgets);
    // Seiten ohne status:-Tag ruecken automatisch nach; vorher stand das Board leer da.
    expect(find.text('Karte hierher ziehen'), findsAtLeast(1),
        reason: 'die leeren Spalten laden zum Ablegen ein');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    // ── KALENDER: die Vorlesungen aus dem Stundenplan ───────────────────────
    //
    // Der eigentliche Beweis fuer Phase 1: der Stundenplan sperrte bisher nur Zeit fuer den
    // Solver und war im Kalender unsichtbar. Die Neuplanung laeuft entprellt, deshalb wird
    // hier gewartet statt einmal geschaut.
    final calendar = Provider.of<CalendarProvider>(context, listen: false);
    await calendar.generateSchedule(DateTime.now());
    await settle(tester, seconds: 3);

    final gotLectures = await pumpUntil(tester, () {
      calendar.loadEventsForMonth(DateTime.now());
      return calendar.events.any((e) => e.isClass);
    }, timeout: const Duration(seconds: 30));

    final lectures = calendar.events.where((e) => e.isClass).toList();
    debugPrint('### KALENDER: ${lectures.length} Vorlesungstermine, '
        'z.B. ${lectures.isEmpty ? "-" : "${lectures.first.title} "
            "${lectures.first.startTime} Farbe ${lectures.first.color}"}');
    expect(gotLectures, isTrue, reason: 'der Stundenplan muss im Kalender auftauchen');
    expect(lectures.every((e) => !e.isFixed), isTrue,
        reason: 'abgeleitet, nicht gepinnt — sonst ueberlebt der Termin das Aufraeumen '
            'und verdoppelt sich bei jedem Lauf');
    expect(lectures.every((e) => e.isLocked), isTrue,
        reason: 'im Kalender nicht verschiebbar');

    // Ein zweiter Lauf darf die Zahl nicht veraendern.
    final before = lectures.length;
    await calendar.generateSchedule(DateTime.now());
    await settle(tester, seconds: 5);
    await calendar.loadEventsForMonth(DateTime.now());
    await settle(tester, seconds: 2);
    final after = calendar.events.where((e) => e.isClass).length;
    debugPrint('### KALENDER nach zweitem Lauf: $after (vorher $before)');
    expect(after, before, reason: 'die Neuplanung darf keine Duplikate erzeugen');

    // ── NOTIZEN-SPACE: freie Notizen leben getrennt von den Modulseiten ─────
    //
    // Der Space war eine Zeit lang geloescht, weil die Regel „jede Seite gehoert zu einem
    // Modul" auf ALLE Notizen angewandt wurde statt nur auf die Seiten des Study Space.
    // Eindeutig je Lauf: bricht der Durchlauf vor dem Aufraeumen ab, bleibt eine Notiz in der
    // Entwicklungsdatenbank liegen — ein fester Titel liesse den naechsten Lauf daran scheitern.
    final titel = 'Durchlauf-Notiz ${DateTime.now().millisecondsSinceEpoch}';
    final freie = await study.addNote(
        title: titel, content: '- [ ] abhaken', category: 'Personal');
    expect(freie, isNotNull, reason: 'eine Notiz ohne Modul muss sich anlegen lassen');

    routes.router.go('/notes');
    await pumpUntil(tester, () => find.text(titel).evaluate().isNotEmpty);
    debugPrint('### NOTIZEN: ${visibleTexts(tester)}');

    // Eingegrenzt auf den Notizen-Screen: der Study-Screen haengt waehrend des Uebergangs
    // noch im Baum, eine ungebundene Suche faende dessen Seitenbaum mit.
    Finder imNotizenSpace(String text) =>
        find.descendant(of: find.byType(NotesScreen), matching: find.text(text));

    expect(imNotizenSpace(titel), findsOneWidget);
    expect(imNotizenSpace('Analysis Skript'), findsNothing,
        reason: 'Modulseiten gehoeren in den FAECHER-Tab, nicht in den Notizen-Space');
    expect(tester.takeException(), isNull);
    await settle(tester, seconds: 2);

    await study.deleteNote(freie!.id!);
    await settle(tester, seconds: 2);

    debugPrint('### Durchlauf beendet');
  });
}
