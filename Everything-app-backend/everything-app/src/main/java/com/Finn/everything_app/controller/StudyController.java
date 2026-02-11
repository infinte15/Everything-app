package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StudyController {

    private final StudyNoteService studyNoteService;
    private final FlashcardService flashcardService;
    private final FlashcardDeckService deckService;
    private final CourseService courseService;
    private final GradeService gradeService;

    private final StudyNoteMapper noteMapper;
    private final FlashcardMapper flashcardMapper;
    private final FlashcardDeckMapper deckMapper;
    private final CourseMapper courseMapper;
    private final GradeMapper gradeMapper;

    // ==================== NOTES ====================


    @GetMapping("/notes")
    public ResponseEntity<List<StudyNoteDTO>> getAllNotes(@CurrentUser Long userId) {
        List<StudyNote> notes = studyNoteService.getUserNotes(userId);
        return ResponseEntity.ok(
                notes.stream().map(noteMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/notes/{id}")
    public ResponseEntity<StudyNoteDTO> getNoteById(@PathVariable Long id) {
        StudyNote note = studyNoteService.getNoteById(id);
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
        StudyNote created = studyNoteService.createNote(userId, note, noteDTO.getCourseId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                noteMapper.toDTO(created)
        );
    }


    @PutMapping("/notes/{id}")
    public ResponseEntity<StudyNoteDTO> updateNote(
            @PathVariable Long id,
            @Valid @RequestBody StudyNoteDTO noteDTO) {

        StudyNote note = noteMapper.toEntity(noteDTO);
        StudyNote updated = studyNoteService.updateNote(id, note);

        return ResponseEntity.ok(noteMapper.toDTO(updated));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id) {
        studyNoteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== FLASHCARDS ====================


    @GetMapping("/flashcards/deck/{deckId}")
    public ResponseEntity<List<FlashcardDTO>> getFlashcardsByDeck(@PathVariable Long deckId) {
        List<Flashcard> cards = flashcardService.getCardsByDeck(deckId);
        return ResponseEntity.ok(
                cards.stream().map(flashcardMapper::toDTO).collect(Collectors.toList())
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
            @PathVariable Long id,
            @Valid @RequestBody FlashcardDTO cardDTO) {

        Flashcard card = flashcardMapper.toEntity(cardDTO);
        Flashcard updated = flashcardService.updateCard(id, card);

        return ResponseEntity.ok(flashcardMapper.toDTO(updated));
    }


    @PostMapping("/flashcards/{id}/review")
    public ResponseEntity<FlashcardDTO> reviewFlashcard(
            @PathVariable Long id,
            @RequestParam String quality) { // EASY, MEDIUM, HARD, AGAIN

        Flashcard reviewed = flashcardService.reviewCard(id, quality);
        return ResponseEntity.ok(flashcardMapper.toDTO(reviewed));
    }


    @DeleteMapping("/flashcards/{id}")
    public ResponseEntity<Void> deleteFlashcard(@PathVariable Long id) {
        flashcardService.deleteCard(id);
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


    @DeleteMapping("/decks/{id}")
    public ResponseEntity<Void> deleteDeck(@PathVariable Long id) {
        deckService.deleteDeck(id);
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
        Course created = courseService.createCourse(userId, course);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                courseMapper.toDTO(created)
        );
    }


    @PutMapping("/courses/{id}")
    public ResponseEntity<CourseDTO> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseDTO courseDTO) {

        Course course = courseMapper.toEntity(courseDTO);
        Course updated = courseService.updateCourse(id, course);

        return ResponseEntity.ok(courseMapper.toDTO(updated));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
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


    @GetMapping("/grades/average")
    public ResponseEntity<Double> getAverageGrade(@CurrentUser Long userId) {
        Double average = gradeService.calculateAverageGrade(userId);
        return ResponseEntity.ok(average);
    }

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
            @PathVariable Long id,
            @Valid @RequestBody GradeDTO gradeDTO) {

        Grade grade = gradeMapper.toEntity(gradeDTO);
        Grade updated = gradeService.updateGrade(id, grade);

        return ResponseEntity.ok(gradeMapper.toDTO(updated));
    }


    @DeleteMapping("/grades/{id}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long id) {
        gradeService.deleteGrade(id);
        return ResponseEntity.noContent().build();
    }
}