package com.Finn.everything_app.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Stehende Notiz zu einer Uebung.
 *
 * @param text leer oder nur Leerzeichen bedeutet "Notiz entfernen" - eine leere Notiz
 *             anzuzeigen waere dasselbe wie keine, nur mit einem leeren Kasten drumherum.
 */
public record ExerciseNoteDTO(
        Long exerciseId,
        @Size(max = 1000, message = "Notiz darf maximal 1000 Zeichen lang sein") String text,
        LocalDateTime updatedAt) {
}
