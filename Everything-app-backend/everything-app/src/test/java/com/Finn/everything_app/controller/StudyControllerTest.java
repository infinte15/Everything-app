package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Der Study Space hatte auf JEDEM Detail-Endpunkt eine IDOR-Luecke: getNoteById, updateNote,
 * deleteNote, getFlashcardsByDeck, updateFlashcard, reviewFlashcard, deleteFlashcard,
 * deleteDeck, updateCourse, deleteCourse, updateGrade und deleteGrade nahmen keine userId
 * entgegen, und die Services machten ein blankes findById(id). Jeder eingeloggte Nutzer konnte
 * damit fremde Notizen, Noten, Karten, Decks und Kurse lesen, aendern und loeschen.
 *
 * Zwei echte Nutzer mit zwei echten JWTs, weil @CurrentUser direkt aus dem Header aufloest -
 * ein Service-Test allein wuerde eine fehlende Annotation am Controller nie bemerken.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired StudyNoteRepository studyNoteRepository;
    @Autowired GradeRepository gradeRepository;
    @Autowired FlashcardRepository flashcardRepository;
    @Autowired FlashcardDeckRepository deckRepository;
    @Autowired FlashcardReviewRepository reviewRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired CourseScheduleRepository courseScheduleRepository;
    @Autowired StudyGoalRepository studyGoalRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired CalendarEventRepository calendarEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private User owner;
    private User intruder;
    private String ownerToken;
    private String intruderToken;

    private Course course;
    private StudyNote note;
    private Grade grade;
    private FlashcardDeck deck;
    private Flashcard card;

    private User ensureUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(username + "@test.local");
            u.setPasswordHash(passwordEncoder.encode("irrelevant"));
            u.setCreatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
    }

    @BeforeEach
    void setUp() {
        owner    = ensureUser("study_owner");
        intruder = ensureUser("study_intruder");
        ownerToken    = jwtUtil.generateToken(owner.getUsername(), owner.getId());
        intruderToken = jwtUtil.generateToken(intruder.getUsername(), intruder.getId());

        course = new Course();
        course.setName("Analysis I");
        course.setUser(owner);
        course.setEctsCredits(6);
        course = courseRepository.save(course);

        note = new StudyNote();
        note.setTitle("Vorlesung 1");
        note.setContent("Inhalt");
        note.setTags("Klausur,wichtig");
        note.setUser(owner);
        note = studyNoteRepository.save(note);

        grade = new Grade();
        grade.setExamName("Klausur");
        grade.setGrade(2.0);
        grade.setWeight(100);
        grade.setUser(owner);
        grade.setCourse(course);
        grade = gradeRepository.save(grade);

        deck = new FlashcardDeck();
        deck.setName("Analysis Deck");
        deck.setUser(owner);
        deck.setTotalCards(0);
        deck.setCardsToReview(0);
        deck.setMasteredCards(0);
        deck = deckRepository.save(deck);

        card = new Flashcard();
        card.setQuestion("Was ist eine Ableitung?");
        card.setAnswer("Steigung");
        card.setDeck(deck);
        card = flashcardRepository.save(card);
    }

    @AfterEach
    void tearDown() {
        // Zuerst das Review-Protokoll: es haengt per Fremdschluessel an den Karten.
        reviewRepository.deleteAll();
        flashcardRepository.deleteAll(flashcardRepository.findAllByUserId(owner.getId()));
        deckRepository.deleteAll(deckRepository.findByUserIdOrderByUpdatedAtDesc(owner.getId()));
        courseScheduleRepository.deleteAll(courseScheduleRepository.findByUserId(owner.getId()));
        // Lernziele vor den Kursen (Fremdschluessel course_id) und vor den Tasks: sie zeigen
        // per task_id auf ihren Bruecken-Task, der wiederum an Kalendereintraegen haengt.
        studyGoalRepository.deleteAll(studyGoalRepository.findByUserIdOrderByIdAsc(owner.getId()));
        for (Task t : taskRepository.findByUserId(owner.getId())) {
            calendarEventRepository.deleteAll(calendarEventRepository.findByRelatedTaskId(t.getId()));
        }
        taskRepository.deleteAll(taskRepository.findByUserId(owner.getId()));
        gradeRepository.deleteAll(gradeRepository.findByUserId(owner.getId()));
        // Notizen zeigen seit dem Seitenbaum per parent_id aufeinander - erst die Verweise
        // kappen, dann löschen, sonst greift die Fremdschlüsselbedingung.
        List<StudyNote> ownerNotes = studyNoteRepository.findByUserId(owner.getId());
        if (!ownerNotes.isEmpty()) {
            studyNoteRepository.clearParentOf(ownerNotes.stream().map(StudyNote::getId).toList());
            studyNoteRepository.deleteAllInBatch(ownerNotes);
        }
        courseRepository.deleteAll(courseRepository.findByUserId(owner.getId()));
        semesterRepository.deleteAll(
                semesterRepository.findByUserIdOrderByOrderIndexAscIdAsc(owner.getId()));
        semesterRepository.deleteAll(
                semesterRepository.findByUserIdOrderByOrderIndexAscIdAsc(intruder.getId()));
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ==================== IDOR-Matrix ====================

    @Test
    void aForeignUserCannotReadANote() throws Exception {
        mockMvc.perform(get("/api/study/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());
    }

    @Test
    void aForeignUserCannotUpdateANote() throws Exception {
        mockMvc.perform(put("/api/study/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "Gekapert", "content", "x"))))
               .andExpect(status().isNotFound());

        assertEquals("Vorlesung 1", studyNoteRepository.findById(note.getId()).orElseThrow().getTitle(),
                "die Notiz darf sich nicht geaendert haben");
    }

    @Test
    void aForeignUserCannotDeleteANote() throws Exception {
        mockMvc.perform(delete("/api/study/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(studyNoteRepository.findById(note.getId()).isPresent(),
                "die Notiz muss noch da sein");
    }

    @Test
    void aForeignUserCannotUpdateAGrade() throws Exception {
        mockMvc.perform(put("/api/study/grades/{id}", grade.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("examName", "Gekapert", "courseId", course.getId(), "grade", 1.0))))
               .andExpect(status().isNotFound());

        assertEquals(2.0, gradeRepository.findById(grade.getId()).orElseThrow().getGrade());
    }

    @Test
    void aForeignUserCannotDeleteAGrade() throws Exception {
        mockMvc.perform(delete("/api/study/grades/{id}", grade.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(gradeRepository.findById(grade.getId()).isPresent());
    }

    @Test
    void aForeignUserCannotReadTheCardsOfADeck() throws Exception {
        mockMvc.perform(get("/api/study/flashcards/deck/{id}", deck.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aForeignUserCannotReviewACard() throws Exception {
        mockMvc.perform(post("/api/study/flashcards/{id}/review", card.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("rating", "GOOD"))))
               .andExpect(status().isNotFound());
    }

    @Test
    void aForeignUserCannotDeleteACard() throws Exception {
        mockMvc.perform(delete("/api/study/flashcards/{id}", card.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(flashcardRepository.findById(card.getId()).isPresent());
    }

    @Test
    void aForeignUserCannotDeleteADeck() throws Exception {
        mockMvc.perform(delete("/api/study/decks/{id}", deck.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(deckRepository.findById(deck.getId()).isPresent());
    }

    @Test
    void aForeignUserCannotUpdateACourse() throws Exception {
        mockMvc.perform(put("/api/study/courses/{id}", course.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("name", "Gekapert"))))
               .andExpect(status().isNotFound());

        assertEquals("Analysis I", courseRepository.findById(course.getId()).orElseThrow().getName());
    }

    @Test
    void aForeignUserCannotDeleteACourse() throws Exception {
        mockMvc.perform(delete("/api/study/courses/{id}", course.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(courseRepository.findById(course.getId()).isPresent());
    }

    // ==================== Der Eigentuemer kommt weiterhin durch ====================

    @Test
    void theOwnerCanStillReadTheirNote() throws Exception {
        mockMvc.perform(get("/api/study/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.title").value("Vorlesung 1"))
               // Regression: der Mapper machte hier frueher "[Klausur,wichtig]" bzw. "null".
               .andExpect(jsonPath("$.tags").value("Klausur,wichtig"));
    }

    @Test
    void theOwnerSeesTheirCardsForADeck() throws Exception {
        mockMvc.perform(get("/api/study/flashcards/deck/{id}", deck.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1))
               .andExpect(jsonPath("$[0].question").value("Was ist eine Ableitung?"));
    }

    // Der Kartenzustand muss als Double herauskommen (2.5), nicht als intern gespeicherte 250.
    @Test
    void theEaseFactorIsExposedAsAFactorNotAsTheStoredInteger() throws Exception {
        mockMvc.perform(get("/api/study/flashcards")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].easeFactor").value(2.5));
    }

    @Test
    void ectsCreditsSurviveTheRoundTrip() throws Exception {
        mockMvc.perform(get("/api/study/courses")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].ectsCredits").value(6));
    }

    // ==================== Fehlerformate ====================

    // Vorher warfen die Services ein blankes RuntimeException, das im Catch-All des
    // GlobalExceptionHandler landete - eine unbekannte ID lieferte also 500 statt 404.
    @Test
    void anUnknownIdIsNotFoundAndNotAServerError() throws Exception {
        mockMvc.perform(get("/api/study/notes/{id}", 999_999L)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNotFound());
    }

    // Frueher nahm der Endpunkt einen freien Query-Parameter und bildete alles Unbekannte
    // still auf MEDIUM ab.
    @Test
    void anUnknownRatingIsRejected() throws Exception {
        mockMvc.perform(post("/api/study/flashcards/{id}/review", card.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"VIELLEICHT\"}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void reviewingWithGoodPushesTheCardIntoTheFuture() throws Exception {
        mockMvc.perform(post("/api/study/flashcards/{id}/review", card.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("rating", "GOOD"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.repetitionCount").value(1))
               .andExpect(jsonPath("$.intervalDays").value(1.0));

        Flashcard after = flashcardRepository.findById(card.getId()).orElseThrow();
        assertTrue(after.getNextReviewDate().isAfter(LocalDateTime.now().plusHours(1)),
                "\"Gut\" darf die Karte nicht sofort wieder faellig machen");
    }

    // Der Kern des ersetzten SM-2: das zweite Review muss auf dem GESPEICHERTEN Intervall
    // aufsetzen. Der alte Code rechnete die Kette aus repetitionCount nach und kam beim zweiten
    // Mal starr auf 6 Tage - unabhaengig davon, was die Karte tatsaechlich hinter sich hatte.
    @Test
    void theSecondReviewBuildsOnTheStoredInterval() throws Exception {
        review(card.getId(), "GOOD")
               .andExpect(jsonPath("$.intervalDays").value(1.0));

        review(card.getId(), "GOOD")
               .andExpect(jsonPath("$.repetitionCount").value(2))
               // 1 gespeicherter Tag * Ease 2.5
               .andExpect(jsonPath("$.intervalDays").value(2.5));
    }

    @Test
    void everyReviewLandsInTheLog() throws Exception {
        review(card.getId(), "GOOD");
        review(card.getId(), "AGAIN");

        mockMvc.perform(get("/api/study/flashcards/reviews")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               // Neueste zuerst.
               .andExpect(jsonPath("$[0].rating").value("AGAIN"))
               .andExpect(jsonPath("$[0].intervalDaysBefore").value(1.0))
               .andExpect(jsonPath("$[0].intervalDaysAfter").value(0.0))
               .andExpect(jsonPath("$[0].flashcardId").value(card.getId()))
               .andExpect(jsonPath("$[0].deckId").value(deck.getId()));
    }

    @Test
    void theReviewLogOfAnotherUserStaysInvisible() throws Exception {
        review(card.getId(), "GOOD");

        mockMvc.perform(get("/api/study/flashcards/reviews")
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
    }

    // Die Zaehlerspalten des Decks werden nur beim Bewerten fortgeschrieben; der Stats-Endpunkt
    // zaehlt frisch und sieht deshalb auch eine gerade erst angelegte Karte.
    @Test
    void deckStatsCountAFreshCardAsNew() throws Exception {
        mockMvc.perform(get("/api/study/decks/{id}/stats", deck.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.total").value(1))
               .andExpect(jsonPath("$.newCards").value(1))
               .andExpect(jsonPath("$.due").value(0))
               .andExpect(jsonPath("$.mature").value(0));

        review(card.getId(), "GOOD");

        // Nach der ersten bestandenen Wiederholung ist die Karte aus der Lernphase raus und
        // erst morgen wieder faellig.
        mockMvc.perform(get("/api/study/decks/{id}/stats", deck.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(jsonPath("$.newCards").value(0))
               .andExpect(jsonPath("$.mature").value(1))
               .andExpect(jsonPath("$.due").value(0));
    }

    @Test
    void aForeignUserCannotReadDeckStats() throws Exception {
        mockMvc.perform(get("/api/study/decks/{id}/stats", deck.getId())
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());
    }

    // Uni-Skala: 5,5 war unter der alten @DecimalMax(\"6.0\") erlaubt.
    @Test
    void aGradeAboveFiveIsRejected() throws Exception {
        mockMvc.perform(post("/api/study/grades")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("examName", "Klausur", "courseId", course.getId(),
                                "grade", 5.5, "weight", 100))))
               .andExpect(status().isBadRequest());
    }

    // ==================== Seitenbaum ====================

    @Test
    void aForeignUserCannotMoveANote() throws Exception {
        mockMvc.perform(put("/api/study/notes/{id}/move", note.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"parentId\":null,\"position\":0}"))
               .andExpect(status().isNotFound());
    }

    @Test
    void aSubpageIsCreatedUnderItsParentAndComesBackWithParentId() throws Exception {
        Long childId = createPage("Kapitel 1", note.getId());

        mockMvc.perform(get("/api/study/notes/{id}", childId)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.parentId").value(note.getId()))
               .andExpect(jsonPath("$.orderIndex").value(0));
    }

    // Der Baum wird clientseitig aus GET /notes gebaut - es gibt bewusst keinen Baum-Endpunkt.
    // Dafür müssen parentId, orderIndex und icon in der Liste stehen.
    @Test
    void theNoteListCarriesTheTreeFields() throws Exception {
        createPage("Kapitel 1", note.getId());

        mockMvc.perform(get("/api/study/notes")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[?(@.title=='Kapitel 1')].parentId").value(note.getId().intValue()))
               .andExpect(jsonPath("$[?(@.title=='Kapitel 1')].orderIndex").value(0));
    }

    @Test
    void movingAPageIntoItsOwnSubpageIsABadRequest() throws Exception {
        Long childId = createPage("Kapitel 1", note.getId());

        mockMvc.perform(put("/api/study/notes/{id}/move", note.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("parentId", childId, "position", 0))))
               .andExpect(status().isBadRequest());
    }

    @Test
    void aPageCanBeMovedToTheRootAndBackUnderAParent() throws Exception {
        Long childId = createPage("Kapitel 1", note.getId());

        mockMvc.perform(put("/api/study/notes/{id}/move", childId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"parentId\":null,\"position\":0}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.parentId").doesNotExist());

        assertNull(studyNoteRepository.findById(childId).orElseThrow().getParent());
    }

    @Test
    void reorderingWritesTheListPositionIntoOrderIndex() throws Exception {
        Long first  = createPage("A", null);
        Long second = createPage("B", null);

        mockMvc.perform(put("/api/study/notes/reorder")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("noteIds", List.of(second, first)))))
               .andExpect(status().isNoContent());

        assertEquals(0, studyNoteRepository.findById(second).orElseThrow().getOrderIndex());
        assertEquals(1, studyNoteRepository.findById(first).orElseThrow().getOrderIndex());
    }

    // Notion-Semantik: die Seite nimmt ihren Teilbaum mit.
    @Test
    void deletingAPageDeletesItsSubpages() throws Exception {
        Long childId = createPage("Kapitel 1", note.getId());
        Long grandchildId = createPage("Abschnitt 1.1", childId);

        mockMvc.perform(delete("/api/study/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        assertTrue(studyNoteRepository.findById(childId).isEmpty());
        assertTrue(studyNoteRepository.findById(grandchildId).isEmpty(),
                "auch zwei Ebenen tiefer");
    }

    // Course.notes kaskadiert nicht mehr - ohne den expliziten Aufruf im CourseService liefe
    // das Löschen eines Kurses in eine Fremdschlüsselverletzung.
    @Test
    void deletingACourseTakesItsNotesWithIt() throws Exception {
        note.setCourse(course);
        studyNoteRepository.save(note);
        Long childId = createPage("Kapitel 1", note.getId());

        mockMvc.perform(delete("/api/study/courses/{id}", course.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        assertTrue(studyNoteRepository.findById(note.getId()).isEmpty());
        assertTrue(studyNoteRepository.findById(childId).isEmpty(),
                "die Unterseite darf nicht als Waise zurueckbleiben");
    }

    // Zwei Arten von Notizen: eine freie Notiz traegt eine Kategorie und kein Modul und lebt im
    // Notizen-Space; eine Modulseite traegt ein Modul und lebt im FAECHER-Tab. Eine Zeit lang
    // war das Modul Pflicht, womit sich ueberhaupt keine gewoehnliche Notiz mehr anlegen liess.
    @Test
    void eineNotizOhneModulWirdAngelegtUndBehaeltIhreKategorie() throws Exception {
        mockMvc.perform(post("/api/study/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Einkaufsliste",
                                "content", "",
                                "category", "Personal"))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.courseId").doesNotExist())
               .andExpect(jsonPath("$.category").value("Personal"));
    }

    @Test
    void eineUnterseiteEinerFreienNotizBleibtFrei() throws Exception {
        String parent = mockMvc.perform(post("/api/study/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "Ideen", "content", "", "category", "Personal"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long parentId = objectMapper.readTree(parent).get("id").asLong();

        mockMvc.perform(post("/api/study/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "Unterpunkt", "content", "", "parentId", parentId))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.courseId").doesNotExist());
    }

    /// Wurzelseiten des Seitenbaums bekommen ein Modul; Unterseiten erben es von der Elternseite.
    private Long createPage(String title, Long parentId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("content", "");
        body.put("parentId", parentId);
        if (parentId == null) {
            body.put("courseId", course.getId());
        }

        String response = mockMvc.perform(post("/api/study/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ==================== Stundenplan ====================

    @Test
    void aScheduleCanBeCreatedReadAndDeleted() throws Exception {
        String body = mockMvc.perform(post("/api/study/courses/{id}/schedules", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("dayOfWeek", "MONDAY", "startTime", "08:00:00",
                                "endTime", "10:00:00", "location", "HS 1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(course.getId().intValue()))
                .andExpect(jsonPath("$.courseName").value("Analysis I"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        // Der ganze Stundenplan in einem Request - die Wochenansicht braucht alle Module.
        mockMvc.perform(get("/api/study/schedules")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1))
               .andExpect(jsonPath("$[0].location").value("HS 1"));

        mockMvc.perform(delete("/api/study/courses/{c}/schedules/{id}", course.getId(), id)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/study/schedules")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aForeignUserSeesNoSchedulesAndCannotAddOne() throws Exception {
        createSchedule("MONDAY", "08:00:00", "10:00:00");

        mockMvc.perform(get("/api/study/schedules")
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/study/courses/{id}/schedules", course.getId())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("dayOfWeek", "TUESDAY", "startTime", "08:00:00",
                                "endTime", "10:00:00"))))
               .andExpect(status().isNotFound());
    }

    @Test
    void aForeignUserCannotChangeOrDeleteASchedule() throws Exception {
        Long id = createSchedule("MONDAY", "08:00:00", "10:00:00");

        mockMvc.perform(put("/api/study/courses/{c}/schedules/{id}", course.getId(), id)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("location", "Gekapert"))))
               .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/study/courses/{c}/schedules/{id}", course.getId(), id)
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(courseScheduleRepository.findById(id).isPresent());
    }

    @Test
    void aScheduleEndingBeforeItStartsIsRejected() throws Exception {
        mockMvc.perform(post("/api/study/courses/{id}/schedules", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("dayOfWeek", "MONDAY", "startTime", "12:00:00",
                                "endTime", "10:00:00"))))
               .andExpect(status().isBadRequest());
    }

    @Test
    void aScheduleWithoutADayIsRejected() throws Exception {
        mockMvc.perform(post("/api/study/courses/{id}/schedules", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("startTime", "08:00:00", "endTime", "10:00:00"))))
               .andExpect(status().isBadRequest());
    }

    // Das Semester steht am Termin mit drin: sonst ist im Frontend nicht zu sehen, warum eine
    // Vorlesung im Kalender auftaucht oder eben nicht mehr.
    @Test
    void aScheduleCarriesTheSemesterOfItsCourse() throws Exception {
        Long semesterId = createSemester("WS 2025/26");
        assignSemester(course.getId(), semesterId);
        createSchedule("WEDNESDAY", "14:00:00", "16:00:00");

        mockMvc.perform(get("/api/study/schedules")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].semesterLabel").value("WS 2025/26"));
    }

    // Der Stundenplan haengt per Fremdschluessel am Kurs, und Course hat bewusst keine
    // schedules-Collection. Ohne explizites Loeschen scheitert das Modul-Loeschen mit 500 -
    // was erst auffiel, als es die Endpunkte gab, um ueberhaupt einen Stundenplan anzulegen.
    @Test
    void deletingACourseTakesItsSchedulesWithIt() throws Exception {
        Long scheduleId = createSchedule("MONDAY", "08:00:00", "10:00:00");

        mockMvc.perform(delete("/api/study/courses/{id}", course.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        assertTrue(courseScheduleRepository.findById(scheduleId).isEmpty());
    }

    private Long createSchedule(String day, String start, String end) throws Exception {
        String body = mockMvc.perform(post("/api/study/courses/{id}/schedules", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("dayOfWeek", day, "startTime", start, "endTime", end))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // ==================== Semester ====================

    // Der Backfill haengt am GET: Bestandsnutzer haben ihre Semester nur als Freitext an den
    // Modulen, und ohne diesen Schritt waere die Semesterliste bei ihnen dauerhaft leer.
    @Test
    void theFirstSemesterCallBackfillsFromTheFreeTextOnTheModules() throws Exception {
        Course second = new Course();
        second.setName("Lineare Algebra");
        second.setUser(owner);
        second.setSemester("WS 2025/26");
        second.setEctsCredits(9);
        courseRepository.save(second);

        course.setSemester("WS 2025/26");
        courseRepository.save(course);

        mockMvc.perform(get("/api/study/semesters")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(1))
               .andExpect(jsonPath("$[0].label").value("WS 2025/26"))
               .andExpect(jsonPath("$[0].moduleCount").value(2))
               .andExpect(jsonPath("$[0].totalEcts").value(15));
    }

    @Test
    void theBackfillDoesNotRunTwice() throws Exception {
        course.setSemester("WS 2025/26");
        courseRepository.save(course);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/study/semesters")
                            .header("Authorization", "Bearer " + ownerToken))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    void aSemesterCanBeCreatedAndRead() throws Exception {
        mockMvc.perform(post("/api/study/semesters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("label", "SS 2026"))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.label").value("SS 2026"));

        mockMvc.perform(get("/api/study/semesters")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(jsonPath("$[0].label").value("SS 2026"));
    }

    @Test
    void aForeignUserCannotDeleteASemester() throws Exception {
        Long id = createSemester("SS 2026");

        mockMvc.perform(delete("/api/study/semesters/{id}", id)
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        assertTrue(semesterRepository.findById(id).isPresent());
    }

    // An einem Modul haengen Noten, Notizen und Karteikarten - ein Semester zu loeschen darf
    // die nicht mitreissen.
    @Test
    void deletingASemesterKeepsItsModules() throws Exception {
        Long semesterId = createSemester("SS 2026");
        assignSemester(course.getId(), semesterId);

        mockMvc.perform(delete("/api/study/semesters/{id}", semesterId)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertNull(after.getSemesterRef(), "Zuordnung aufgehoben");
        assertEquals("Analysis I", after.getName(), "das Modul selbst bleibt");
        assertTrue(gradeRepository.findById(grade.getId()).isPresent(),
                "und mit ihm seine Noten");
    }

    @Test
    void assigningAndClearingTheSemesterOfAModule() throws Exception {
        Long semesterId = createSemester("SS 2026");

        mockMvc.perform(put("/api/study/courses/{id}/semester", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("semesterId", semesterId))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.semesterId").value(semesterId))
               .andExpect(jsonPath("$.semesterLabel").value("SS 2026"))
               // Der Freitext wird mitgefuehrt, damit die alten Filter weiter funktionieren.
               .andExpect(jsonPath("$.semester").value("SS 2026"));

        mockMvc.perform(put("/api/study/courses/{id}/semester", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"semesterId\":null}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.semesterId").doesNotExist());
    }

    // Sonst liesse sich das eigene Modul in ein fremdes Semester haengen.
    @Test
    void aModuleCannotBeAssignedToAForeignSemester() throws Exception {
        Semester foreign = new Semester();
        foreign.setLabel("Fremd");
        foreign.setUser(intruder);
        foreign = semesterRepository.save(foreign);

        mockMvc.perform(put("/api/study/courses/{id}/semester", course.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("semesterId", foreign.getId()))))
               .andExpect(status().isNotFound());
    }

    // Course.semester und Semester.label muessen zusammenbleiben.
    @Test
    void renamingASemesterUpdatesTheFreeTextOnItsModules() throws Exception {
        Long semesterId = createSemester("SS 2026");
        assignSemester(course.getId(), semesterId);

        mockMvc.perform(put("/api/study/semesters/{id}", semesterId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("label", "Sommersemester 2026"))))
               .andExpect(status().isOk());

        assertEquals("Sommersemester 2026",
                courseRepository.findById(course.getId()).orElseThrow().getSemester());
    }

    @Test
    void onlyOneSemesterIsMarkedAsCurrent() throws Exception {
        Long first = createSemester("WS 2025/26");
        Long second = createSemester("SS 2026");

        mockMvc.perform(put("/api/study/semesters/{id}/current", first)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk());
        mockMvc.perform(put("/api/study/semesters/{id}/current", second)
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isOk());

        assertFalse(semesterRepository.findById(first).orElseThrow().getIsCurrent(),
                "das vorherige aktuelle Semester muss zurueckgesetzt werden");
        assertTrue(semesterRepository.findById(second).orElseThrow().getIsCurrent());
    }

    // ------------------------------------------------------------------
    // Lernziele
    // ------------------------------------------------------------------

    @Test
    void einLernzielEntstehtMitBrueckenTask() throws Exception {
        mockMvc.perform(post("/api/study/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("courseId", course.getId(), "weeklyGoalHours", 5.0))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.courseName").value(course.getName()))
               .andExpect(jsonPath("$.weeklyGoalHours").value(5.0))
               .andExpect(jsonPath("$.remainingHours").value(5.0))
               // Ohne den Task landete das Ziel nie im Kalender - das ist der ganze Zweck.
               .andExpect(jsonPath("$.taskId").isNumber());
    }

    @Test
    void einLernzielOhneModulWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/study/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("weeklyGoalHours", 5.0))))
               .andExpect(status().isBadRequest());
    }

    @Test
    void erfassteStundenLandenAmZiel() throws Exception {
        Long id = createGoal(5.0);

        mockMvc.perform(post("/api/study/goals/{id}/log", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("hours", 2.0))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.loggedHours").value(2.0))
               .andExpect(jsonPath("$.remainingHours").value(3.0));
    }

    @Test
    void nullStundenZuErfassenIstEinFehler() throws Exception {
        Long id = createGoal(5.0);

        mockMvc.perform(post("/api/study/goals/{id}/log", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("hours", 0.0))))
               .andExpect(status().isBadRequest());
    }

    @Test
    void einFremderNutzerKommtNichtAnEinLernziel() throws Exception {
        Long id = createGoal(5.0);

        mockMvc.perform(delete("/api/study/goals/{id}", id)
                        .header("Authorization", "Bearer " + intruderToken))
               .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/study/goals")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void einGeloeschtesModulNimmtSeinLernzielMit() throws Exception {
        createGoal(5.0);

        // study_goals.course_id ist ein Fremdschluessel: ohne das Aufraeumen im CourseService
        // scheiterte das Loeschen des Moduls hier mit 500.
        mockMvc.perform(delete("/api/study/courses/{id}", course.getId())
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/study/goals")
                        .header("Authorization", "Bearer " + ownerToken))
               .andExpect(jsonPath("$.length()").value(0));
    }

    private Long createGoal(double hours) throws Exception {
        String body = mockMvc.perform(post("/api/study/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("courseId", course.getId(), "weeklyGoalHours", hours))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions review(Long cardId, String rating) throws Exception {
        return mockMvc.perform(post("/api/study/flashcards/{id}/review", cardId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("rating", rating))))
                .andExpect(status().isOk());
    }

    private Long createSemester(String label) throws Exception {
        String body = mockMvc.perform(post("/api/study/semesters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("label", label))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void assignSemester(Long courseId, Long semesterId) throws Exception {
        mockMvc.perform(put("/api/study/courses/{id}/semester", courseId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("semesterId", semesterId))))
               .andExpect(status().isOk());
    }
}
