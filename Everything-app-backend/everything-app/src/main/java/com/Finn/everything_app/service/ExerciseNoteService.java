package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ExerciseNoteDTO;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.ExerciseNote;
import com.Finn.everything_app.repository.ExerciseNoteRepository;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Stehende Notizen zu Uebungen - eine je Nutzer und Uebung. */
@Service
@RequiredArgsConstructor
public class ExerciseNoteService {

    private final ExerciseNoteRepository noteRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ExerciseNoteDTO get(Long userId, Long exerciseId) {
        return noteRepository.findByUserIdAndExerciseId(userId, exerciseId)
                .map(n -> new ExerciseNoteDTO(exerciseId, n.getText(), n.getUpdatedAt()))
                .orElseGet(() -> new ExerciseNoteDTO(exerciseId, null, null));
    }

    /** Texte je Uebung - fuer den Trainingsstart, der sie alle auf einmal braucht. */
    @Transactional(readOnly = true)
    public Map<Long, String> getAll(Long userId, Collection<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        for (ExerciseNote note : noteRepository.findByUserIdAndExerciseIdIn(userId, exerciseIds)) {
            out.put(note.getExercise().getId(), note.getText());
        }
        return out;
    }

    /** Leerer Text loescht die Notiz - ein leerer Kasten ist keine Information. */
    @Transactional
    public ExerciseNoteDTO save(Long userId, Long exerciseId, String text) {
        if (text == null || text.isBlank()) {
            noteRepository.deleteByUserIdAndExerciseId(userId, exerciseId);
            return new ExerciseNoteDTO(exerciseId, null, null);
        }

        ExerciseNote note = noteRepository.findByUserIdAndExerciseId(userId, exerciseId)
                .orElseGet(() -> {
                    ExerciseNote created = new ExerciseNote();
                    created.setUser(userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Nutzer nicht gefunden")));
                    created.setExercise(exerciseRepository.findById(exerciseId)
                            .orElseThrow(() -> new ResourceNotFoundException("Übung nicht gefunden")));
                    return created;
                });
        note.setText(text.strip());
        ExerciseNote saved = noteRepository.save(note);
        return new ExerciseNoteDTO(exerciseId, saved.getText(), saved.getUpdatedAt());
    }
}
