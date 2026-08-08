package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.CourseSchedule;
import com.Finn.everything_app.model.Flashcard;
import com.Finn.everything_app.model.FlashcardDeck;
import com.Finn.everything_app.model.Grade;
import com.Finn.everything_app.model.Semester;
import com.Finn.everything_app.model.StudyGoal;
import com.Finn.everything_app.model.StudyNote;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.CourseScheduleRepository;
import com.Finn.everything_app.repository.FlashcardDeckRepository;
import com.Finn.everything_app.repository.FlashcardRepository;
import com.Finn.everything_app.repository.GradeRepository;
import com.Finn.everything_app.repository.SemesterRepository;
import com.Finn.everything_app.repository.StudyGoalRepository;
import com.Finn.everything_app.repository.StudyNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Demo-Bestand des Lern-Space: zwei Semester, sieben Kurse, ein vollständiger Stundenplan,
 * Noten, Wochenziele, Karteikarten mit gemischtem Lernstand und verschachtelte Notizen.
 *
 * <p>Der Stundenplan ist überschneidungsfrei und lässt bewusst Lücken: er wird vom Scheduler
 * als feste Blockade gelesen, und eine randvolle Woche hätte nichts mehr zu verteilen.
 */
@Component
@RequiredArgsConstructor
public class DemoStudyData {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final GradeRepository gradeRepository;
    private final StudyGoalRepository studyGoalRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;
    private final StudyNoteRepository studyNoteRepository;

    /** Kurse des laufenden Semesters, für den Planer-Space nachnutzbar. */
    private final Map<String, Course> coursesByCode = new HashMap<>();

    public Course course(String code) {
        return coursesByCode.get(code);
    }

    @Transactional
    public void seed(User user, LocalDate today) {
        coursesByCode.clear();

        Semester previous = semester(user, "WS 2025/26", today.minusWeeks(38), today.minusWeeks(14), 0, false);
        Semester current = semester(user, "SS 2026", today.minusWeeks(8), today.plusWeeks(10), 1, true);

        Course analysis = course(user, current, "Analysis II", "MATH-202", "Prof. Dr. Weber", 9, "#5B8DEF",
                "Mehrdimensionale Differential- und Integralrechnung, gewöhnliche Differentialgleichungen.");
        Course dbs = course(user, current, "Datenbanksysteme", "INF-304", "Prof. Dr. Hoffmann", 6, "#2EC4B6",
                "Relationales Modell, SQL, Normalformen, Transaktionen und Anfrageoptimierung.");
        Course se = course(user, current, "Software Engineering", "INF-311", "Prof. Dr. Lehmann", 6, "#FF9F1C",
                "Anforderungen, Architektur, Entwurfsmuster, Testen — mit begleitendem Gruppenprojekt.");
        Course theo = course(user, current, "Theoretische Informatik", "INF-208", "Prof. Dr. Baumann", 8, "#E71D36",
                "Automaten, formale Sprachen, Berechenbarkeit und Komplexitätsklassen.");
        Course stat = course(user, current, "Statistik für Informatiker", "MATH-150", "Dr. Sanders", 5, "#9B5DE5",
                "Wahrscheinlichkeitsrechnung, Schätzer, Hypothesentests, lineare Regression.");

        Course linalg = course(user, previous, "Lineare Algebra I", "MATH-101", "Prof. Dr. Weber", 9, "#4361EE",
                "Vektorräume, lineare Abbildungen, Determinanten, Eigenwerte.");
        Course prog2 = course(user, previous, "Programmierung II", "INF-102", "Prof. Dr. Ritter", 8, "#06D6A0",
                "Objektorientierung, Datenstrukturen, Nebenläufigkeit in Java.");

        // Ein schlanker Stundenplan: jede Zeile hier wird ueber syncClassEvents zu einem festen
        // Kalenderblock in JEDER Woche des Horizonts und ist damit der groesste einzelne Posten
        // im Demo-Kalender. Sechs Termine reichen, um Mo-Fr belegt zu zeigen.
        schedule(analysis, DayOfWeek.MONDAY, 8, 15, 9, 45, "HS 1 - Hörsaalzentrum");
        schedule(theo, DayOfWeek.MONDAY, 14, 15, 15, 45, "HS 4 - Informatikgebäude");
        schedule(dbs, DayOfWeek.TUESDAY, 10, 15, 11, 45, "HS 3 - Informatikgebäude");
        schedule(se, DayOfWeek.WEDNESDAY, 12, 15, 13, 45, "HS 2 - Hörsaalzentrum");
        schedule(analysis, DayOfWeek.THURSDAY, 10, 15, 11, 45, "HS 1 - Hörsaalzentrum");
        schedule(stat, DayOfWeek.FRIDAY, 10, 15, 11, 45, "SR 101 - Mathegebäude");

        grade(user, linalg, "Klausur", 2.3, 100, today.minusWeeks(16), "Klausur", true, null);
        grade(user, prog2, "Klausur", 1.7, 70, today.minusWeeks(15), "Klausur", true, "Aufgabe 4 komplett verhauen.");
        grade(user, prog2, "Praktikum", 1.0, 30, today.minusWeeks(18), "Praktikum", true, null);
        grade(user, analysis, "Übungsblätter-Testat", 1.7, 20, today.minusWeeks(2), "Testat", true, null);
        grade(user, dbs, "Zwischenklausur", 2.0, 30, today.minusWeeks(3), "Klausur", true,
                "Normalformen saßen, bei der Optimierung Punkte liegen lassen.");
        grade(user, se, "Sprint-1-Abgabe", 1.3, 25, today.minusWeeks(4), "Projekt", true, null);
        grade(user, stat, "Testat 1", 2.7, 15, today.minusWeeks(1), "Testat", true, null);
        grade(user, theo, "Probeklausur", 3.0, 0, today.minusDays(9), "Probeklausur", false,
                "Zählt nicht, war aber ein deutlicher Warnschuss.");

        LocalDate weekStart = DemoDates.monday(today);
        goal(user, analysis, "📐", 6.0, 3.5, weekStart);
        goal(user, dbs, "🗄️", 4.0, 2.0, weekStart);
        goal(user, se, "🛠️", 3.0, 3.0, weekStart);
        goal(user, theo, "🧮", 5.0, 1.5, weekStart);
        goal(user, stat, "📊", 3.0, 0.5, weekStart);

        seedDecks(user, today, analysis, dbs, se, theo, stat);
        seedNotes(user, today, analysis, dbs, se, theo);
    }

    // ------------------------------------------------------------------ Kurse

    private Semester semester(User user, String label, LocalDate start, LocalDate end,
                              int orderIndex, boolean isCurrent) {
        Semester semester = new Semester();
        semester.setUser(user);
        semester.setLabel(label);
        semester.setStartDate(start);
        semester.setEndDate(end);
        semester.setOrderIndex(orderIndex);
        semester.setIsCurrent(isCurrent);
        return semesterRepository.save(semester);
    }

    private Course course(User user, Semester semester, String name, String code, String instructor,
                          int ects, String color, String description) {
        Course course = new Course();
        course.setUser(user);
        course.setName(name);
        course.setCode(code);
        course.setInstructor(instructor);
        course.setSemesterRef(semester);
        course.setSemester(semester.getLabel());
        course.setDescription(description);
        course.setStartDate(semester.getStartDate());
        course.setEndDate(semester.getEndDate());
        course.setColor(color);
        course.setEctsCredits(ects);
        Course saved = courseRepository.save(course);
        coursesByCode.put(code, saved);
        return saved;
    }

    private void schedule(Course course, DayOfWeek day, int fromH, int fromM, int toH, int toM, String location) {
        CourseSchedule schedule = new CourseSchedule();
        schedule.setCourse(course);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(LocalTime.of(fromH, fromM));
        schedule.setEndTime(LocalTime.of(toH, toM));
        schedule.setLocation(location);
        courseScheduleRepository.save(schedule);
    }

    private void grade(User user, Course course, String examName, double value, int weight,
                       LocalDate date, String type, boolean counts, String notes) {
        Grade grade = new Grade();
        grade.setUser(user);
        grade.setCourse(course);
        grade.setExamName(examName);
        grade.setGrade(value);
        grade.setWeight(weight);
        grade.setExamDate(date);
        grade.setExamType(type);
        grade.setCountsTowardGrade(counts);
        grade.setNotes(notes);
        gradeRepository.save(grade);
    }

    private void goal(User user, Course course, String emoji, double weeklyHours,
                      double loggedHours, LocalDate weekStart) {
        StudyGoal goal = new StudyGoal();
        goal.setUser(user);
        goal.setCourse(course);
        goal.setEmoji(emoji);
        goal.setWeeklyGoalHours(weeklyHours);
        goal.setLoggedHours(loggedHours);
        goal.setLoggedWeekStart(weekStart);
        studyGoalRepository.save(goal);
    }

    // ------------------------------------------------------------ Karteikarten

    private void seedDecks(User user, LocalDate today, Course analysis, Course dbs,
                           Course se, Course theo, Course stat) {
        deck(user, analysis, "Analysis II — Sätze & Definitionen", today.minusWeeks(7),
                "Alles, was in der Klausur als Definition abgefragt werden kann.", new String[][]{
                        {"Wann heißt eine Funktion f: R^n -> R total differenzierbar in x0?",
                                "Wenn es eine lineare Abbildung L gibt mit f(x0+h) = f(x0) + L(h) + o(|h|) für h -> 0."},
                        {"Satz von Schwarz — Aussage?",
                                "Sind die zweiten partiellen Ableitungen stetig, so ist die Reihenfolge der Ableitung egal: ∂²f/∂x∂y = ∂²f/∂y∂x."},
                        {"Definition: Gradient",
                                "Der Vektor aller partiellen Ableitungen erster Ordnung, ∇f = (∂f/∂x1, …, ∂f/∂xn)."},
                        {"Wann ist ein Vektorfeld konservativ?",
                                "Wenn es ein Potential besitzt. Auf einfach zusammenhängenden Gebieten äquivalent zu rot F = 0."},
                        {"Satz von Fubini — wofür?",
                                "Erlaubt, ein Mehrfachintegral als iteriertes Integral zu berechnen, sofern der Integrand absolut integrierbar ist."},
                        {"Was besagt der Satz über implizite Funktionen?",
                                "Ist F(x,y)=0 und ∂F/∂y invertierbar, lässt sich y lokal als stetig differenzierbare Funktion von x auflösen."},
                        {"Lagrange-Multiplikatoren — Grundidee",
                                "Im Extremum unter Nebenbedingung g=0 ist ∇f parallel zu ∇g, also ∇f = λ∇g."},
                        {"Definition: Jacobi-Matrix",
                                "Die Matrix aller partiellen Ableitungen erster Ordnung einer Abbildung f: R^n -> R^m, Format m×n."},
                        {"Wann ist eine DGL y' = f(x,y) eindeutig lösbar?",
                                "Picard-Lindelöf: wenn f stetig und lokal Lipschitz-stetig in y ist."},
                        {"Was ist die Wronski-Determinante gut für?",
                                "Sie prüft die lineare Unabhängigkeit von Lösungen einer linearen DGL — ist sie ungleich 0, sind sie unabhängig."},
                        {"Definition: Richtungsableitung",
                                "Die Ableitung von f entlang eines Einheitsvektors v: D_v f(x) = lim_{t->0} (f(x+tv) - f(x))/t."},
                        {"Transformationssatz — wofür braucht man ihn?",
                                "Für den Variablenwechsel im Mehrfachintegral; der Betrag der Jacobi-Determinante ist der Korrekturfaktor."},
                });

        deck(user, dbs, "SQL & Normalformen", today.minusDays(2),
                "Alles, was die Zwischenklausur abgefragt hat — und was sie hätte abfragen sollen.", new String[][]{
                        {"Was besagt die 3. Normalform?",
                                "2NF und zusätzlich: kein Nichtschlüsselattribut hängt transitiv vom Primärschlüssel ab."},
                        {"Unterschied zwischen WHERE und HAVING",
                                "WHERE filtert Zeilen vor der Gruppierung, HAVING filtert Gruppen danach."},
                        {"Was macht ein LEFT OUTER JOIN?",
                                "Liefert alle Zeilen der linken Tabelle; fehlt rechts ein Partner, stehen dort NULL-Werte."},
                        {"ACID — wofür stehen die vier Buchstaben?",
                                "Atomarität, Konsistenz, Isolation, Dauerhaftigkeit."},
                        {"Was ist ein Dirty Read?",
                                "Eine Transaktion liest Daten, die eine andere geschrieben, aber noch nicht committet hat."},
                        {"Wann bringt ein Index nichts?",
                                "Bei geringer Selektivität, bei Funktionen auf der indizierten Spalte und wenn ohnehin fast alle Zeilen gelesen werden."},
                        {"Unterschied zwischen Primär- und Sekundärindex",
                                "Der Primärindex bestimmt die physische Sortierung der Datensätze, ein Sekundärindex nicht."},
                        {"Was ist BCNF?",
                                "Für jede funktionale Abhängigkeit X -> Y muss X ein Superschlüssel sein. Strenger als 3NF."},
                        {"Was ist ein Deadlock und wie erkennt ihn ein DBMS?",
                                "Gegenseitiges Warten auf Sperren; erkannt über Zyklen im Wartegraph, aufgelöst durch Abbruch einer Transaktion."},
                        {"Wofür steht das Isolationslevel READ COMMITTED?",
                                "Es verhindert Dirty Reads, erlaubt aber Non-Repeatable Reads und Phantome."},
                        {"Was leistet ein Query-Optimizer?",
                                "Er wählt anhand von Statistiken den kostengünstigsten Ausführungsplan aus mehreren äquivalenten."},
                        {"Wann ist Denormalisierung sinnvoll?",
                                "Wenn die Lesekosten der Joins die Kosten der Redundanz übersteigen — typisch bei Auswertungssystemen."},
                });

        deck(user, se, "Design Patterns", today.minusDays(5),
                "Die Muster aus der Vorlesung, jeweils auf den Auslöser reduziert.", new String[][]{
                        {"Strategy — welches Problem löst es?",
                                "Austauschbare Algorithmen hinter einer gemeinsamen Schnittstelle, damit die if-Kette im Aufrufer verschwindet."},
                        {"Observer — welches Problem löst es?",
                                "Mehrere Objekte über eine Zustandsänderung informieren, ohne dass der Sender sie kennen muss."},
                        {"Was ist der Unterschied zwischen Factory Method und Abstract Factory?",
                                "Factory Method erzeugt ein Produkt über eine überschriebene Methode, Abstract Factory eine ganze Familie zusammengehöriger Produkte."},
                        {"Wann ist ein Singleton die falsche Wahl?",
                                "Fast immer, wenn er globalen Zustand einführt — er macht Tests unabhängig voneinander unmöglich."},
                        {"Decorator — Grundidee",
                                "Verhalten zur Laufzeit umhüllen statt per Vererbung festzuschreiben."},
                        {"Adapter vs. Facade",
                                "Adapter passt eine bestehende Schnittstelle an eine erwartete an, Facade vereinfacht eine komplexe Menge von Schnittstellen."},
                        {"Was besagt das Liskov-Substitutionsprinzip?",
                                "Ein Untertyp muss überall dort einsetzbar sein, wo der Basistyp erwartet wird, ohne die Zusicherungen zu verletzen."},
                        {"Dependency Inversion — Kernaussage",
                                "Module hoher Ebene sollen nicht von Modulen niedriger Ebene abhängen, sondern beide von Abstraktionen."},
                        {"Wofür steht das Command-Pattern?",
                                "Eine Aktion als Objekt fassen — damit werden Rückgängig, Warteschlangen und Protokollierung möglich."},
                        {"Was ist ein Anti-Pattern und nenne eins",
                                "Eine wiederkehrende, scheinbar naheliegende Lösung mit schlechtem Ausgang — z.B. der God Object / die Gott-Klasse."},
                });

        deck(user, theo, "Automaten & Berechenbarkeit", today.minusDays(9),
                "Der Stoff, bei dem die Probeklausur wehgetan hat.", new String[][]{
                        {"Was ist der Unterschied zwischen DFA und NFA in der Ausdrucksstärke?",
                                "Keiner — beide erkennen genau die regulären Sprachen. Der NFA ist nur kompakter."},
                        {"Pumping-Lemma für reguläre Sprachen — wozu?",
                                "Als Nachweis, dass eine Sprache NICHT regulär ist. Als Beweis für Regularität taugt es nicht."},
                        {"Chomsky-Hierarchie: die vier Stufen",
                                "Typ 3 regulär, Typ 2 kontextfrei, Typ 1 kontextsensitiv, Typ 0 rekursiv aufzählbar."},
                        {"Was ist das Halteproblem?",
                                "Die Frage, ob eine Turingmaschine bei gegebener Eingabe hält — nachweislich unentscheidbar."},
                        {"Definition: NP",
                                "Die Klasse der Probleme, deren Lösung sich in polynomieller Zeit verifizieren lässt."},
                        {"Was heißt NP-vollständig?",
                                "In NP und mindestens so schwer wie jedes andere Problem in NP (per polynomieller Reduktion)."},
                        {"Satz von Rice — Aussage",
                                "Jede nicht-triviale semantische Eigenschaft berechenbarer Funktionen ist unentscheidbar."},
                        {"Welcher Automat gehört zu kontextfreien Sprachen?",
                                "Der Kellerautomat (Pushdown-Automat)."},
                        {"Sind kontextfreie Sprachen unter Schnitt abgeschlossen?",
                                "Nein. Unter Vereinigung, Konkatenation und Stern schon, unter Schnitt und Komplement nicht."},
                        {"Was ist eine polynomielle Reduktion A ≤p B?",
                                "Eine in Polynomzeit berechenbare Abbildung, die jede A-Instanz in eine äquivalente B-Instanz überführt."},
                });

        deck(user, stat, "Statistik-Grundbegriffe", today.minusDays(1),
                "Begriffe, die in jeder Aufgabe vorkommen.", new String[][]{
                        {"Was ist ein p-Wert?",
                                "Die Wahrscheinlichkeit, unter der Nullhypothese ein mindestens so extremes Ergebnis zu beobachten."},
                        {"Fehler 1. und 2. Art",
                                "1. Art: wahre Nullhypothese verworfen. 2. Art: falsche Nullhypothese beibehalten."},
                        {"Was besagt der zentrale Grenzwertsatz?",
                                "Die Summe vieler unabhängiger, gleichverteilter Zufallsgrößen ist annähernd normalverteilt."},
                        {"Erwartungstreue eines Schätzers",
                                "Sein Erwartungswert stimmt mit dem geschätzten Parameter überein: E[θ̂] = θ."},
                        {"Was ist ein Konfidenzintervall zum Niveau 95%?",
                                "Ein aus Daten berechnetes Intervall, das bei Wiederholung des Experiments in 95% der Fälle den wahren Parameter enthält."},
                        {"Unterschied Korrelation und Kausalität",
                                "Korrelation misst nur den linearen Zusammenhang zweier Größen; über die Ursache sagt sie nichts."},
                        {"Was ist die Methode der kleinsten Quadrate?",
                                "Sie wählt die Parameter so, dass die Summe der quadrierten Residuen minimal wird."},
                        {"Wann nimmt man einen t-Test statt eines z-Tests?",
                                "Wenn die Varianz unbekannt ist und aus der Stichprobe geschätzt werden muss."},
                });
    }

    /**
     * Legt ein Deck samt Karten an und setzt die Zähler so, wie
     * {@code FlashcardDeckService.updateDeckStatistics} sie berechnen würde — sonst zeigten die
     * Deck-Kacheln bis zur ersten Lernsitzung "0 fällig".
     */
    private void deck(User user, Course course, String name, LocalDate lastStudied,
                      String description, String[][] cards) {
        FlashcardDeck deck = new FlashcardDeck();
        deck.setUser(user);
        deck.setCourse(course);
        deck.setName(name);
        deck.setDescription(description);
        deck.setLastStudiedAt(lastStudied.atTime(19, 30));
        deck = deckRepository.save(deck);

        LocalDateTime now = LocalDateTime.now();
        int due = 0;
        int mastered = 0;

        for (int i = 0; i < cards.length; i++) {
            Flashcard card = new Flashcard();
            card.setDeck(deck);
            card.setQuestion(cards[i][0]);
            card.setAnswer(cards[i][1]);
            card.setCategory(course.getName());

            // Vier wiederkehrende Lernstände, damit jede Ansicht der App etwas zu zeigen hat:
            // ungelernt, im Lernen, fällig und gemeistert.
            switch (i % 4) {
                case 0 -> { // frisch, nie gesehen
                    card.setDifficulty("mittel");
                    card.setRepetitionCount(0);
                    card.setIntervalDays(0.0);
                    card.setLearningStep(0);
                    card.setNextReviewDate(now);
                    due++;
                }
                case 1 -> { // mitten im Lernen, heute wieder dran
                    card.setDifficulty("schwer");
                    card.setRepetitionCount(2);
                    card.setEasinessFactor(215);
                    card.setIntervalDays(1.0);
                    card.setLearningStep(1);
                    card.setLapses(1);
                    card.setLastReviewedAt(now.minusDays(1));
                    card.setNextReviewDate(now.minusHours(3));
                    due++;
                }
                case 2 -> { // sitzt, kommt erst in ein paar Tagen wieder
                    card.setDifficulty("mittel");
                    card.setRepetitionCount(4);
                    card.setEasinessFactor(255);
                    card.setIntervalDays(6.0);
                    card.setLearningStep(2);
                    card.setLastReviewedAt(now.minusDays(2));
                    card.setNextReviewDate(now.plusDays(4));
                }
                default -> { // gemeistert (Intervall >= 21 Tage)
                    card.setDifficulty("leicht");
                    card.setRepetitionCount(7);
                    card.setEasinessFactor(285);
                    card.setIntervalDays(34.0);
                    card.setLearningStep(2);
                    card.setLastReviewedAt(now.minusDays(11));
                    card.setNextReviewDate(now.plusDays(23));
                    mastered++;
                }
            }
            flashcardRepository.save(card);
        }

        deck.setTotalCards(cards.length);
        deck.setCardsToReview(due);
        deck.setMasteredCards(mastered);
        deckRepository.save(deck);

        course.setTotalFlashcards(course.getTotalFlashcards() + cards.length);
        courseRepository.save(course);
    }

    // ------------------------------------------------------------------ Notizen

    private void seedNotes(User user, LocalDate today, Course analysis, Course dbs,
                           Course se, Course theo) {
        StudyNote dbsRoot = note(user, dbs, null, "📗", "Datenbanksysteme — Mitschrift", 0, true,
                "sql,klausur", today.minusWeeks(7), """
                        # Datenbanksysteme

                        Sammelnotiz für die Vorlesung. Die einzelnen Kapitel hängen als Unterseiten dran.

                        **Klausur:** 90 Minuten, keine Hilfsmittel außer einem beidseitig beschriebenen Blatt.

                        ## Woran ich hängen bleibe
                        - Anfrageoptimierung: Kostenmodell noch nicht verstanden
                        - Mehrbenutzerbetrieb: Unterschied 2PL / strenges 2PL
                        """);

        note(user, dbs, dbsRoot, "🔑", "Kapitel 3 — Normalformen", 0, false, "sql,normalformen",
                today.minusWeeks(6), """
                        ## Normalformen in einer Zeile

                        | NF | Bedingung |
                        |----|-----------|
                        | 1NF | Alle Attribute atomar |
                        | 2NF | 1NF + keine partielle Abhängigkeit vom Schlüssel |
                        | 3NF | 2NF + keine transitive Abhängigkeit |
                        | BCNF | Jede Determinante ist Superschlüssel |

                        Merksatz aus der Übung: *"the key, the whole key, and nothing but the key"*.

                        ### Typische Klausuraufgabe
                        Gegeben eine Relation mit FD-Menge, gesucht die Schlüsselkandidaten und die
                        höchste erfüllte Normalform. Vorgehen: Attributhülle bilden, dann rückwärts prüfen.
                        """);

        note(user, dbs, dbsRoot, "🔒", "Kapitel 6 — Transaktionen", 1, false, "acid,sperren",
                today.minusWeeks(3), """
                        ## Isolationslevel und was sie verhindern

                        - **READ UNCOMMITTED** — verhindert nichts
                        - **READ COMMITTED** — keine Dirty Reads
                        - **REPEATABLE READ** — zusätzlich keine Non-Repeatable Reads
                        - **SERIALIZABLE** — zusätzlich keine Phantome

                        2PL: Sperren erst alle anfordern, dann alle freigeben. **Strenges** 2PL gibt
                        Schreibsperren erst beim Commit frei — dadurch keine kaskadierenden Abbrüche.
                        """);

        StudyNote analysisRoot = note(user, analysis, null, "📐", "Analysis II — Formelsammlung", 1, true,
                "formeln,klausur", today.minusWeeks(5), """
                        # Analysis II — was aufs Blatt muss

                        Das hier wird am Ende die eine erlaubte Seite für die Klausur.

                        - Kettenregel mehrdimensional: D(f∘g)(x) = Df(g(x)) · Dg(x)
                        - Taylor 2. Ordnung: f(x0+h) ≈ f(x0) + ∇f·h + ½ hᵀ H h
                        - Divergenz, Rotation, Gradient — Zusammenhänge über den Nabla-Operator
                        """);

        note(user, analysis, analysisRoot, "🧩", "Übungsblatt 7 — Lösungsweg", 0, false, "übung",
                today.minusWeeks(1), """
                        ## Aufgabe 3 (Lagrange)

                        Extrema von f(x,y) = x²y unter x² + y² = 3.

                        1. L(x,y,λ) = x²y − λ(x² + y² − 3)
                        2. ∂L/∂x = 2xy − 2λx = 0 → x(y − λ) = 0
                        3. ∂L/∂y = x² − 2λy = 0
                        4. Fallunterscheidung x = 0 bzw. y = λ

                        Ergebnis: Maximum bei (±√2, 1), Minimum bei (±√2, −1).

                        > Fehler beim ersten Versuch: den Fall x = 0 unterschlagen.
                        """);

        StudyNote seRoot = note(user, se, null, "🛠️", "Software Engineering — Projekt", 2, false,
                "projekt,team", today.minusWeeks(6), """
                        # Gruppenprojekt "Mensa-Planer"

                        Team: ich, Lena, Jonas, Miriam. Wöchentliches Standup dienstags 18 Uhr.

                        ## Sprint 2 — offen
                        - [ ] REST-Schnittstelle für die Speiseplan-Abfrage
                        - [ ] Persistenz mit JPA aufsetzen
                        - [x] Architekturentscheidung dokumentiert (ADR-001)
                        - [ ] Testabdeckung über 70% bringen
                        """);

        note(user, se, seRoot, "📋", "ADR-001 — Schichtenarchitektur", 0, false, "architektur",
                today.minusWeeks(4), """
                        ## Entscheidung
                        Klassische Schichtung Controller → Service → Repository.

                        ## Begründung
                        Alle vier kennen das Muster aus Programmierung II. Eine hexagonale
                        Architektur wäre sauberer, kostet im Sechs-Wochen-Projekt aber mehr
                        Einarbeitung, als sie an Flexibilität zurückgibt.

                        ## Konsequenz
                        Fachlogik gehört ausschließlich in den Service. Kein Repository-Aufruf
                        direkt aus dem Controller — das ist der Punkt, an dem so etwas kippt.
                        """);

        note(user, theo, null, "🧮", "Theoretische Informatik — Beweismuster", 3, false,
                "beweise,klausur", today.minusDays(6), """
                        # Wiederkehrende Beweismuster

                        ## Sprache ist nicht regulär
                        Pumping-Lemma, Widerspruch. Gegner wählt n, ich wähle das Wort,
                        Gegner zerlegt, ich wähle i. **Reihenfolge nie vertauschen.**

                        ## Problem ist NP-vollständig
                        1. Zugehörigkeit zu NP zeigen (Zertifikat angeben)
                        2. Bekanntes NP-vollständiges Problem darauf reduzieren
                        3. Reduktion läuft in Polynomzeit — kurz begründen

                        ## Unentscheidbarkeit
                        Reduktion vom Halteproblem, oder direkt Satz von Rice anwenden.
                        """);

        note(user, null, null, "🗒️", "Semesterplanung SS 2026", 4, true, "planung",
                today.minusWeeks(8), """
                        # Semesterplanung

                        **28 ECTS** belegt — das ist ein Modul mehr als der Regelplan vorsieht.

                        | Kurs | ECTS | Prüfungsform |
                        |------|------|--------------|
                        | Analysis II | 9 | Klausur |
                        | Theoretische Informatik | 8 | Klausur |
                        | Datenbanksysteme | 6 | Klausur |
                        | Software Engineering | 6 | Projekt + Präsentation |
                        | Statistik | 5 | Klausur |

                        Wenn es eng wird, fliegt Statistik raus — die lässt sich im WS nachholen.
                        """);
    }

    private StudyNote note(User user, Course course, StudyNote parent, String icon, String title,
                           int orderIndex, boolean favorite, String tags, LocalDate created, String content) {
        StudyNote note = new StudyNote();
        note.setUser(user);
        note.setCourse(course);
        note.setParent(parent);
        note.setIcon(icon);
        note.setTitle(title);
        note.setContent(content);
        note.setCategory(course != null ? course.getName() : "Allgemein");
        note.setTags(tags);
        note.setIsFavorite(favorite);
        note.setOrderIndex(orderIndex);
        note.setLastReviewedAt(created.plusDays(3).atTime(20, 15));
        StudyNote saved = studyNoteRepository.save(note);

        if (course != null) {
            course.setTotalNotes(course.getTotalNotes() + 1);
            courseRepository.save(course);
        }
        return saved;
    }
}
