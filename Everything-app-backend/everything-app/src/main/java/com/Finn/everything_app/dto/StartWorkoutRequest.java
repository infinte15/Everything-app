package com.Finn.everything_app.dto;

import lombok.Data;

/**
 * Startet ein Training. Genau einer der Wege wird gewaehlt:
 * <ul>
 *   <li>{@code sessionId} - eine bereits eingeplante Einheit wird jetzt trainiert,</li>
 *   <li>{@code routineId} - eine Routine wird frisch gestartet,</li>
 *   <li>keins von beidem - leeres Training mit optionalem {@code name}.</li>
 * </ul>
 */
@Data
public class StartWorkoutRequest {
    private Long sessionId;
    private Long routineId;
    private String name;
}
