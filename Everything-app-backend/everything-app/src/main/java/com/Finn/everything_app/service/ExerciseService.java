package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final EquipmentProfileService equipmentProfileService;

    public List<Exercise> getAllExercises() {
        return exerciseRepository.findAll();
    }

    /**
     * Paginierte Katalog-Suche. Alle Filter sind optional; {@code null} heisst "nicht filtern".
     */
    @Transactional(readOnly = true)
    public Page<Exercise> searchExercises(Long userId,
                                          String search,
                                          String muscleSlug,
                                          String equipment,
                                          String category,
                                          String difficulty,
                                          int page,
                                          int size) {
        MuscleGroup muscle = MuscleGroup.fromSlugOrNull(muscleSlug);
        if (muscleSlug != null && !muscleSlug.isBlank() && muscle == null) {
            throw new BadRequestException("Unbekannte Muskelgruppe: " + muscleSlug);
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        // Ein aktives Ausruestungsprofil schraenkt die Bibliothek auf das ein, was damit
        // ueberhaupt machbar ist. Ein ausdruecklich gewaehltes Geraet schlaegt es nicht -
        // beide Bedingungen gelten, sonst koennte der Filter etwas anzeigen, das im Profil
        // gar nicht vorkommt.
        Set<String> profile = equipmentProfileService.activeEquipment(userId);
        boolean byProfile = !profile.isEmpty();

        return exerciseRepository.search(
                blankToNull(search),
                muscle,
                blankToNull(equipment),
                blankToNull(category),
                blankToNull(difficulty),
                byProfile,
                byProfile ? profile : Set.of(""),
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "name"))
        );
    }

    public Exercise getExerciseById(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Übung nicht gefunden"));
    }

    public List<String> getEquipmentValues() {
        return exerciseRepository.findDistinctEquipment();
    }

    public List<String> getCategoryValues() {
        return exerciseRepository.findDistinctCategories();
    }

    public List<String> getDifficultyValues() {
        return exerciseRepository.findDistinctDifficulties();
    }

    @Transactional
    public Exercise createExercise(Long userId, Exercise exercise) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        exercise.setCreatedBy(user); // User-eigene Übung
        exercise.setIsSystem(false);
        syncMuscleGroupMirror(exercise);

        return exerciseRepository.save(exercise);
    }

    @Transactional
    public Exercise updateExercise(Long userId, Long id, Exercise updatedExercise) {
        Exercise exercise = getExerciseById(id);
        assertOwnedBy(userId, exercise);

        if (updatedExercise.getName() != null) {
            exercise.setName(updatedExercise.getName());
        }
        if (updatedExercise.getDescription() != null) {
            exercise.setDescription(updatedExercise.getDescription());
        }
        if (updatedExercise.getInstructions() != null) {
            exercise.setInstructions(updatedExercise.getInstructions());
        }
        if (updatedExercise.getMuscleGroup() != null) {
            exercise.setMuscleGroup(updatedExercise.getMuscleGroup());
        }
        if (updatedExercise.getPrimaryMuscles() != null && !updatedExercise.getPrimaryMuscles().isEmpty()) {
            exercise.setPrimaryMuscles(updatedExercise.getPrimaryMuscles());
        }
        if (updatedExercise.getSecondaryMuscles() != null) {
            exercise.setSecondaryMuscles(updatedExercise.getSecondaryMuscles());
        }
        if (updatedExercise.getEquipment() != null) {
            exercise.setEquipment(updatedExercise.getEquipment());
        }
        if (updatedExercise.getDifficulty() != null) {
            exercise.setDifficulty(updatedExercise.getDifficulty());
        }
        if (updatedExercise.getCategory() != null) {
            exercise.setCategory(updatedExercise.getCategory());
        }
        if (updatedExercise.getForce() != null) {
            exercise.setForce(updatedExercise.getForce());
        }
        if (updatedExercise.getMechanic() != null) {
            exercise.setMechanic(updatedExercise.getMechanic());
        }
        if (updatedExercise.getVideoUrl() != null) {
            exercise.setVideoUrl(updatedExercise.getVideoUrl());
        }
        if (updatedExercise.getImageUrl() != null) {
            exercise.setImageUrl(updatedExercise.getImageUrl());
        }
        if (updatedExercise.getAnimationUrl() != null) {
            exercise.setAnimationUrl(updatedExercise.getAnimationUrl());
        }
        if (updatedExercise.getImageUrlEnd() != null) {
            exercise.setImageUrlEnd(updatedExercise.getImageUrlEnd());
        }
        if (updatedExercise.getDefaultRestSeconds() != null) {
            exercise.setDefaultRestSeconds(updatedExercise.getDefaultRestSeconds());
        }
        syncMuscleGroupMirror(exercise);

        return exerciseRepository.save(exercise);
    }

    @Transactional
    public void deleteExercise(Long userId, Long id) {
        Exercise exercise = getExerciseById(id);
        assertOwnedBy(userId, exercise);

        exerciseRepository.delete(exercise);
    }

    /**
     * Der Katalog gehoert keinem User und wird von allen geteilt - er ist deshalb
     * schreibgeschuetzt. Fremde User-Uebungen sind fuer den Aufrufer schlicht nicht sichtbar.
     */
    private void assertOwnedBy(Long userId, Exercise exercise) {
        if (exercise.getCreatedBy() == null) {
            throw new BadRequestException("System-Übungen können nicht geändert oder gelöscht werden");
        }
        if (!exercise.getCreatedBy().getId().equals(userId)) {
            throw new ResourceNotFoundException("Übung nicht gefunden");
        }
    }

    /**
     * Haelt die alte {@code muscle_group}-Spalte mit der primaeren Muskelgruppe im Gleichklang.
     * Die Spalte ist NOT NULL, darf also unter keinen Umstaenden leer bleiben.
     */
    private void syncMuscleGroupMirror(Exercise exercise) {
        if (exercise.getPrimaryMuscles() != null && !exercise.getPrimaryMuscles().isEmpty()) {
            exercise.setMuscleGroup(exercise.getPrimaryMuscles().iterator().next().getSlug());
            return;
        }
        String existing = exercise.getMuscleGroup();
        if (existing == null || existing.isBlank()) {
            throw new BadRequestException("Muskelgruppe erforderlich");
        }
        // Umgekehrter Weg: der Client hat nur die alte Einzel-Spalte geschickt.
        MuscleGroup derived = MuscleGroup.fromSlugOrNull(existing);
        if (derived != null) {
            exercise.getPrimaryMuscles().add(derived);
            exercise.setMuscleGroup(derived.getSlug());
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
