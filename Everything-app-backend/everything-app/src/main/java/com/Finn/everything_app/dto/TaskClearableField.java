package com.Finn.everything_app.dto;

/**
 * Felder einer Aufgabe, die sich ausdrücklich LEEREN lassen.
 *
 * <p><b>Warum es das braucht.</b> {@code TaskService.updateTask} patcht mit der Konvention
 * "{@code null} heißt unverändert" — sonst würde {@code CalendarEventService.creditBlock}, das mit
 * einem nackten {@code new Task()} nur Minuten und Status nachträgt, bei jedem Abhaken die halbe
 * Aufgabe leerräumen. Die Kehrseite: eine gesetzte Deadline ließ sich über diesen Weg nie wieder
 * entfernen. Im Bearbeiten-Sheet ist genau das aber eine alltägliche Handlung.
 *
 * <p><b>Warum eine Liste und nicht je ein Endpunkt.</b> Für die Projektzuordnung gibt es mit
 * {@code PUT /tasks/{id}/project} bereits einen eigenen Endpunkt, aus demselben Grund. Das
 * Prinzip dahinter — wenn {@code null} "leeren" heißen muss, braucht es einen Kanal, in dem
 * {@code null} eindeutig ist — gilt hier genauso, das Rezept aber nicht: das Sheet speichert alles
 * auf einmal, und es sind sechs leerbare Felder. Sechs Endpunkte machten aus einem Speichern
 * sieben Anfragen und sieben Neuplanungs-Meldungen, ohne Atomarität und ohne definierten
 * Fehlerzustand am Speichern-Knopf.
 *
 * <p><b>Warum ein Enum und kein {@code Set<String>}.</b> Jackson weist einen unbekannten Wert von
 * selbst mit 400 ab. Als freier String würde ein Tippfehler im Client stillschweigend nichts tun —
 * und "das Löschen kommt nicht an" ist ein Fehler, den man lange sucht.
 *
 * <p>{@code SPLITTABLE} fehlt bewusst: dafür gibt es im UI nur an und aus, nie "kein Wert".
 */
public enum TaskClearableField {
    DEADLINE,
    NOT_BEFORE,
    MIN_CHUNK_MINUTES,
    MAX_CHUNK_MINUTES,
    MAX_CHUNKS_PER_DAY,
    /** Kein Beifang: eine Notiz ließ sich bisher gar nicht löschen — "" wird im Sheet zu null. */
    DESCRIPTION
}
