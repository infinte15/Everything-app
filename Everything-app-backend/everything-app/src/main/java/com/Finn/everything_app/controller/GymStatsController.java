package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.MuscleRecoveryDTO;
import com.Finn.everything_app.dto.MuscleVolumeDTO;
import com.Finn.everything_app.dto.WeeklyStatsDTO;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.WorkoutStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Auswertungen fuer den Gym-Bereich: Wochenbilanz und Muskel-Belastung. */
@RestController
@RequestMapping("/api/sports/stats")
@RequiredArgsConstructor
public class GymStatsController {

    private final WorkoutStatsService statsService;

    @GetMapping("/week")
    public ResponseEntity<WeeklyStatsDTO> getWeeklyStats(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        return ResponseEntity.ok(statsService.getWeeklyStats(userId, weekStart));
    }

    /** Grundlage der Koerper-Grafik. Liefert immer alle Muskelgruppen, auch untrainierte. */
    @GetMapping("/muscles")
    public ResponseEntity<List<MuscleVolumeDTO>> getMuscleVolume(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(statsService.getMuscleVolume(userId, startDate, endDate));
    }

    /**
     * Erholungsstand je Muskelgruppe. Kein Zeitraum-Parameter: "wie erholt bin ich" ist
     * immer eine Frage von jetzt.
     */
    @GetMapping("/recovery")
    public ResponseEntity<List<MuscleRecoveryDTO>> getRecovery(@CurrentUser Long userId) {
        return ResponseEntity.ok(statsService.getRecovery(userId));
    }
}
