package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyNoteService {

    private final StudyNoteRepository studyNoteRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;

    @Transactional
    public StudyNote createNote(Long userId, StudyNote note, Long courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        note.setUser(user);
        note.setIsFavorite(note.getIsFavorite() != null ? note.getIsFavorite() : false);

        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Kurs nicht gefunden"));
            note.setCourse(course);

            // Update Course Statistics
            courseService.incrementNoteCount(courseId);
        }

        return studyNoteRepository.save(note);
    }

    public List<StudyNote> getUserNotes(Long userId) {
        return studyNoteRepository.findByUserId(userId);
    }

    public StudyNote getNoteById(Long id) {
        return studyNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notiz nicht gefunden"));
    }

    public List<StudyNote> getNotesByCourse(Long userId, Long courseId) {
        return studyNoteRepository.findByUserIdAndCourseId(userId, courseId);
    }

    public List<StudyNote> searchNotes(Long userId, String query) {
        return studyNoteRepository.findByUserIdAndTitleContainingOrContentContaining(
                userId, query,query
        );
    }

    public List<StudyNote> getFavoriteNotes(Long userId) {
        return studyNoteRepository.findByUserIdAndIsFavoriteTrue(userId);
    }

    @Transactional
    public StudyNote updateNote(Long id, StudyNote updatedNote) {
        StudyNote note = getNoteById(id);

        if (updatedNote.getTitle() != null) {
            note.setTitle(updatedNote.getTitle());
        }
        if (updatedNote.getContent() != null) {
            note.setContent(updatedNote.getContent());
        }
        if (updatedNote.getCategory() != null) {
            note.setCategory(updatedNote.getCategory());
        }
        if (updatedNote.getTags() != null) {
            note.setTags(updatedNote.getTags());
        }
        if (updatedNote.getIsFavorite() != null) {
            note.setIsFavorite(updatedNote.getIsFavorite());
        }

        return studyNoteRepository.save(note);
    }

    @Transactional
    public StudyNote markAsReviewed(Long id) {
        StudyNote note = getNoteById(id);
        note.setLastReviewedAt(LocalDateTime.now());
        return studyNoteRepository.save(note);
    }

    @Transactional
    public void deleteNote(Long id) {
        StudyNote note = getNoteById(id);

        // Update Course Statistics
        if (note.getCourse() != null) {
            courseService.decrementNoteCount(note.getCourse().getId());
        }

        studyNoteRepository.delete(note);
    }
}