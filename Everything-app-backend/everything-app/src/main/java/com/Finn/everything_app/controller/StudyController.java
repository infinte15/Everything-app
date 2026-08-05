package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StudyController {

    /** Zeitraum von GET /flashcards/reviews ohne since-Parameter. */
    private static final int REVIEW_LOG_DEFAULT_DAYS = 30;

    private final StudyNoteService studyNoteService;
    private final FlashcardService flashcardService;
    private final FlashcardDeckService deckService;
    private final CourseService courseService;
    private final GradeService gradeService;
    private final SemesterService semesterService;
    private final CourseScheduleService courseScheduleService;
    private final StudyGoalService studyGoalService;

    private final StudyNoteMapper noteMapper;
    private final FlashcardMapper flashcardMapper;
    private final FlashcardDeckMapper deckMapper;
    private final FlashcardReviewMapper reviewMapper;
    private final CourseMapper courseMapper;
    private final GradeMapper gradeMapper;
    private final SemesterMapper semesterMapper;
    private final CourseScheduleMapper scheduleMapper;
    private final StudyGoalMapper goalMapper;

    // ==================== NOTES ====================


    @GetMapping("/notes")
    public ResponseEntity<List<StudyNoteDTO>> getAllNotes(@CurrentUser Long userId) {
        List<StudyNote> notes = studyNoteService.getUserNotes(userId);
        return ResponseEntity.ok(
                notes.stream().map(noteMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/notes/{id}")
    public ResponseEntity<StudyNoteDTO> getNoteById(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        StudyNote note = studyNoteService.getNote(userId, id);
        return ResponseEntity.ok(noteMapper.toDTO(note));
    }


    @GetMapping("/notes/course/{courseId}")
    public ResponseEntity<List<StudyNoteDTO>> getNotesByCourse(
            @CurrentUser Long userId,
            @PathVariable Long courseId) {

        List<StudyNote> notes = studyNoteService.getNotesByCourse(userId, courseId);
        return ResponseEntity.ok(
                notes.stream().map(noteMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/notes/search")
    public ResponseEntity<List<StudyNoteDTO>> searchNotes(
            @CurrentUser Long userId,
            @RequestParam String query) {

        List<StudyNote> notes = studyNoteService.searchNotes(userId, query);
        return ResponseEntity.ok(
                notes.stream().map(noteMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/notes")
    public ResponseEntity<StudyNoteDTO> createNote(
            @CurrentUser Long userId,
            @Valid @RequestBody StudyNoteDTO noteDTO) {

        StudyNote note = noteMapper.toEntity(noteDTO);
        StudyNote created = studyNoteService.createNote(
                userId, note, noteDTO.getCourseId(), noteDTO.getParentId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                noteMapper.toDTO(created)
        );
    }


    // Seitenbaum. Beide Pfade stehen VOR /notes/{id}, damit "reorder" nicht als ID gelesen wird.

    @PutMapping("/notes/reorder")
    public ResponseEntity<Void> reorderNotes(
            @CurrentUser Long userId,
            @Valid @RequestBody NoteReorderRequest request) {

        studyNoteService.reorderNotes(userId, request.noteIds());
        return ResponseEntity.noContent().build();
    }


    /** Ordnet eine Seite samt Teilbaum einem Modul zu — auch für Bestandsseiten ohne Modul. */
    @PutMapping("/notes/{id}/course")
    public ResponseEntity<StudyNoteDTO> assignNoteCourse(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody AssignCourseRequest request) {

        StudyNote note = studyNoteService.assignCourse(userId, id, request.courseId());
        return ResponseEntity.ok(noteMapper.toDTO(note));
    }


    @PutMapping("/notes/{id}/move")
    public ResponseEntity<StudyNoteDTO> moveNote(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @RequestBody MoveNoteRequest request) {

        StudyNote moved = studyNoteService.moveNote(userId, id, request.parentId(), request.position());
        return ResponseEntity.ok(noteMapper.toDTO(moved));
    }


    @PutMapping("/notes/{id}")
    public ResponseEntity<StudyNoteDTO> updateNote(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody StudyNoteDTO noteDTO) {

        StudyNote note = noteMapper.toEntity(noteDTO);
        StudyNote updated = studyNoteService.updateNote(userId, id, note);

        return ResponseEntity.ok(noteMapper.toDTO(updated));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> deleteNote(@CurrentUser Long userId, @PathVariable Long id) {
        studyNoteService.deleteNote(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== FLASHCARDS ====================

    // Alle Karten des Nutzers auf einmal. Der Provider holte vorher pro Deck einzeln, also
    // 1 + N Requests beim Start des Study Space.
    @GetMapping("/flashcards")
    public ResponseEntity<List<FlashcardDTO>> getAllFlashcards(@CurrentUser Long userId) {
        List<Flashcard> cards = flashcardService.getAllCards(userId);
        return ResponseEntity.ok(
                cards.stream().map(flashcardMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/flashcards/deck/{deckId}")
    public ResponseEntity<List<FlashcardDTO>> getFlashcardsByDeck(
            @CurrentUser Long userId,
            @PathVariable Long deckId) {
        List<Flashcard> cards = flashcardService.getCardsByDeck(userId, deckId);
        return ResponseEntity.ok(
                cards.stream().map(flashcardMapper::toDTO).collect(Collectors.toList())
        );
    }


    /**
     * Das Review-Protokoll ab einem Zeitpunkt. Ohne {@code since} die letzten 30 Tage — ein
     * unbegrenztes Protokoll wäre auf Dauer der teuerste Endpunkt der App.
     */
    @GetMapping("/flashcards/reviews")
    public ResponseEntity<List<FlashcardReviewDTO>> getFlashcardReviews(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        LocalDateTime from = since != null ? since : LocalDateTime.now().minusDays(REVIEW_LOG_DEFAULT_DAYS);
        List<FlashcardReview> reviews = flashcardService.getReviewsSince(userId, from);
        return ResponseEntity.ok(
                reviews.stream().map(reviewMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/flashcards/due")
    public ResponseEntity<List<FlashcardDTO>> getDueFlashcards(@CurrentUser Long userId) {
        List<Flashcard> cards = flashcardService.getDueCards(userId);
        return ResponseEntity.ok(
                cards.stream().map(flashcardMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/flashcards")
    public ResponseEntity<FlashcardDTO> createFlashcard(
            @CurrentUser Long userId,
            @Valid @RequestBody FlashcardDTO cardDTO) {

        Flashcard card = flashcardMapper.toEntity(cardDTO);
        Flashcard created = flashcardService.createCard(userId, card, cardDTO.getDeckId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                flashcardMapper.toDTO(created)
        );
    }


    @PutMapping("/flashcards/{id}")
    public ResponseEntity<FlashcardDTO> updateFlashcard(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody FlashcardDTO cardDTO) {

        Flashcard card = flashcardMapper.toEntity(cardDTO);
        Flashcard updated = flashcardService.updateCard(userId, id, card);

        return ResponseEntity.ok(flashcardMapper.toDTO(updated));
    }


    // Bewertung im Body und als Enum: als freier Query-Parameter wurde alles Unbekannte
    // still auf MEDIUM abgebildet. Jetzt liefert ein unbekannter Wert 400.
    @PostMapping("/flashcards/{id}/review")
    public ResponseEntity<FlashcardDTO> reviewFlashcard(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request) {

        Flashcard reviewed = flashcardService.reviewCard(userId, id, request.rating());
        return ResponseEntity.ok(flashcardMapper.toDTO(reviewed));
    }


    @DeleteMapping("/flashcards/{id}")
    public ResponseEntity<Void> deleteFlashcard(@CurrentUser Long userId, @PathVariable Long id) {
        flashcardService.deleteCard(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== DECKS ====================

    @GetMapping("/decks")
    public ResponseEntity<List<FlashcardDeckDTO>> getAllDecks(@CurrentUser Long userId) {
        List<FlashcardDeck> decks = deckService.getUserDecks(userId);
        return ResponseEntity.ok(
                decks.stream().map(deckMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/decks")
    public ResponseEntity<FlashcardDeckDTO> createDeck(
            @CurrentUser Long userId,
            @Valid @RequestBody FlashcardDeckDTO deckDTO) {

        FlashcardDeck deck = deckMapper.toEntity(deckDTO);
        FlashcardDeck created = deckService.createDeck(userId, deck, deckDTO.getCourseId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                deckMapper.toDTO(created)
        );
    }


    // Die Kennzahlen kommen frisch aus der Datenbank statt aus den Zählerspalten des Decks:
    // die werden nur beim Bewerten fortgeschrieben und sind nach dem Anlegen einer Karte veraltet.
    @GetMapping("/decks/{id}/stats")
    public ResponseEntity<DeckStatsDTO> getDeckStats(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(deckService.getDeckStats(userId, id));
    }


    @DeleteMapping("/decks/{id}")
    public ResponseEntity<Void> deleteDeck(@CurrentUser Long userId, @PathVariable Long id) {
        deckService.deleteDeck(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== COURSES ====================

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDTO>> getAllCourses(@CurrentUser Long userId) {
        List<Course> courses = courseService.getUserCourses(userId);
        return ResponseEntity.ok(
                courses.stream().map(courseMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/courses")
    public ResponseEntity<CourseDTO> createCourse(
            @CurrentUser Long userId,
            @Valid @RequestBody CourseDTO courseDTO) {

        Course course = courseMapper.toEntity(courseDTO);
        Course created = courseService.createCourse(userId, course, courseDTO.getSemesterId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                courseMapper.toDTO(created)
        );
    }


    // Eigener Endpunkt, weil updateCourse partiell arbeitet und "kein Semester" dort nicht
    // von "unverändert" zu unterscheiden wäre — wie bei PUT /calendar/events/{id}/pin.
    @PutMapping("/courses/{id}/semester")
    public ResponseEntity<CourseDTO> assignSemester(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @RequestBody AssignSemesterRequest request) {

        Course updated = courseService.assignSemester(userId, id, request.semesterId());
        return ResponseEntity.ok(courseMapper.toDTO(updated));
    }


    @PutMapping("/courses/{id}")
    public ResponseEntity<CourseDTO> updateCourse(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CourseDTO courseDTO) {

        Course course = courseMapper.toEntity(courseDTO);
        Course updated = courseService.updateCourse(userId, id, course);

        return ResponseEntity.ok(courseMapper.toDTO(updated));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@CurrentUser Long userId, @PathVariable Long id) {
        courseService.deleteCourse(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== STUNDENPLAN ====================

    // Der ganze Stundenplan in einem Request. Die Wochenansicht braucht alle Module auf einmal;
    // pro Modul einzeln zu holen wären so viele Roundtrips wie Module.
    @GetMapping("/schedules")
    public ResponseEntity<List<CourseScheduleDTO>> getAllSchedules(@CurrentUser Long userId) {
        List<CourseSchedule> schedules = courseScheduleService.getUserSchedules(userId);
        return ResponseEntity.ok(
                schedules.stream().map(scheduleMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/courses/{courseId}/schedules")
    public ResponseEntity<List<CourseScheduleDTO>> getSchedulesOfCourse(
            @CurrentUser Long userId,
            @PathVariable Long courseId) {

        List<CourseSchedule> schedules = courseScheduleService.getSchedulesOfCourse(userId, courseId);
        return ResponseEntity.ok(
                schedules.stream().map(scheduleMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/courses/{courseId}/schedules")
    public ResponseEntity<CourseScheduleDTO> createSchedule(
            @CurrentUser Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseScheduleDTO scheduleDTO) {

        CourseSchedule schedule = scheduleMapper.toEntity(scheduleDTO);
        CourseSchedule created = courseScheduleService.createSchedule(userId, courseId, schedule);

        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleMapper.toDTO(created));
    }


    @PutMapping("/courses/{courseId}/schedules/{id}")
    public ResponseEntity<CourseScheduleDTO> updateSchedule(
            @CurrentUser Long userId,
            @PathVariable Long courseId,
            @PathVariable Long id,
            @RequestBody CourseScheduleDTO scheduleDTO) {

        CourseSchedule schedule = scheduleMapper.toEntity(scheduleDTO);
        CourseSchedule updated = courseScheduleService.updateSchedule(userId, id, schedule);

        return ResponseEntity.ok(scheduleMapper.toDTO(updated));
    }


    @DeleteMapping("/courses/{courseId}/schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @CurrentUser Long userId,
            @PathVariable Long courseId,
            @PathVariable Long id) {

        courseScheduleService.deleteSchedule(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== SEMESTER ====================

    @GetMapping("/semesters")
    public ResponseEntity<List<SemesterDTO>> getAllSemesters(@CurrentUser Long userId) {
        List<Semester> semesters = semesterService.getSemesters(userId);
        Map<Long, List<Course>> modules = semesterService.modulesBySemester(userId);

        return ResponseEntity.ok(semesters.stream()
                .map(s -> semesterMapper.toDTO(s, modules.getOrDefault(s.getId(), List.of())))
                .collect(Collectors.toList()));
    }


    @PostMapping("/semesters")
    public ResponseEntity<SemesterDTO> createSemester(
            @CurrentUser Long userId,
            @Valid @RequestBody SemesterDTO semesterDTO) {

        Semester created = semesterService.createSemester(userId, semesterMapper.toEntity(semesterDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(semesterMapper.toDTO(created));
    }


    @PutMapping("/semesters/{id}")
    public ResponseEntity<SemesterDTO> updateSemester(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SemesterDTO semesterDTO) {

        Semester updated = semesterService.updateSemester(userId, id, semesterMapper.toEntity(semesterDTO));
        return ResponseEntity.ok(semesterMapper.toDTO(updated));
    }


    @PutMapping("/semesters/{id}/current")
    public ResponseEntity<SemesterDTO> setCurrentSemester(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        return ResponseEntity.ok(semesterMapper.toDTO(semesterService.setCurrent(userId, id)));
    }


    @PutMapping("/semesters/reorder")
    public ResponseEntity<Void> reorderSemesters(
            @CurrentUser Long userId,
            @RequestBody SemesterReorderRequest request) {

        semesterService.reorderSemesters(userId, request.semesterIds());
        return ResponseEntity.noContent().build();
    }


    // Löscht nur das Semester; seine Module bleiben bestehen und sind danach keinem
    // Semester zugeordnet. An ihnen hängen Noten, Notizen und Karteikarten.
    @DeleteMapping("/semesters/{id}")
    public ResponseEntity<Void> deleteSemester(@CurrentUser Long userId, @PathVariable Long id) {
        semesterService.deleteSemester(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== LERNZIELE ====================
    //
    // Ein Lernziel gehört zu genau einem Modul und spiegelt sich in einen Task, damit der
    // SmartScheduler die Reststunden im Kalender platzieren kann (siehe StudyGoalService).

    @GetMapping("/goals")
    public ResponseEntity<List<StudyGoalDTO>> getAllGoals(@CurrentUser Long userId) {
        return ResponseEntity.ok(studyGoalService.getGoals(userId).stream()
                .map(goalMapper::toDTO)
                .collect(Collectors.toList()));
    }


    @PostMapping("/goals")
    public ResponseEntity<StudyGoalDTO> createGoal(
            @CurrentUser Long userId,
            @Valid @RequestBody StudyGoalDTO goalDTO) {

        StudyGoal created = studyGoalService.createGoal(
                userId, goalDTO.getCourseId(), goalMapper.toEntity(goalDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(goalMapper.toDTO(created));
    }


    @PutMapping("/goals/{id}")
    public ResponseEntity<StudyGoalDTO> updateGoal(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody StudyGoalDTO goalDTO) {

        StudyGoal updated = studyGoalService.updateGoal(userId, id, goalMapper.toEntity(goalDTO));
        return ResponseEntity.ok(goalMapper.toDTO(updated));
    }


    @PostMapping("/goals/{id}/log")
    public ResponseEntity<StudyGoalDTO> logGoalHours(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody LogHoursRequest request) {

        return ResponseEntity.ok(goalMapper.toDTO(studyGoalService.logHours(userId, id, request.hours())));
    }


    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(@CurrentUser Long userId, @PathVariable Long id) {
        studyGoalService.deleteGoal(userId, id);
        return ResponseEntity.noContent().build();
    }


    // ==================== GRADES ====================


    @GetMapping("/grades")
    public ResponseEntity<List<GradeDTO>> getAllGrades(@CurrentUser Long userId) {
        List<Grade> grades = gradeService.getUserGrades(userId);
        return ResponseEntity.ok(
                grades.stream().map(gradeMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/grades/course/{courseId}")
    public ResponseEntity<List<GradeDTO>> getGradesByCourse(
            @CurrentUser Long userId,
            @PathVariable Long courseId) {

        List<Grade> grades = gradeService.getGradesByCourse(userId, courseId);
        return ResponseEntity.ok(
                grades.stream().map(gradeMapper::toDTO).collect(Collectors.toList())
        );
    }


    // GET /grades/average ist entfallen: kein Client hat ihn je aufgerufen, und die
    // Notenmathematik lebt in lib/utils/study_grade_calculator.dart.

    @PostMapping("/grades")
    public ResponseEntity<GradeDTO> createGrade(
            @CurrentUser Long userId,
            @Valid @RequestBody GradeDTO gradeDTO) {

        Grade grade = gradeMapper.toEntity(gradeDTO);
        Grade created = gradeService.createGrade(userId, grade, gradeDTO.getCourseId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                gradeMapper.toDTO(created)
        );
    }


    @PutMapping("/grades/{id}")
    public ResponseEntity<GradeDTO> updateGrade(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody GradeDTO gradeDTO) {

        Grade grade = gradeMapper.toEntity(gradeDTO);
        Grade updated = gradeService.updateGrade(userId, id, grade);

        return ResponseEntity.ok(gradeMapper.toDTO(updated));
    }


    @DeleteMapping("/grades/{id}")
    public ResponseEntity<Void> deleteGrade(@CurrentUser Long userId, @PathVariable Long id) {
        gradeService.deleteGrade(userId, id);
        return ResponseEntity.noContent().build();
    }
}