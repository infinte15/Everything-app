package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body von PUT /api/study/notes/{id}/course.
 *
 * Ordnet eine Seite samt Teilbaum einem Modul zu. Anders als bei {@link AssignSemesterRequest}
 * ist {@code null} hier nicht erlaubt: jede Seite gehört zu einem Modul, sonst taucht sie in
 * keiner Modulansicht auf und ist über die Oberfläche nicht mehr erreichbar.
 */
public record AssignCourseRequest(
        @NotNull(message = "Modul erforderlich") Long courseId) {
}
