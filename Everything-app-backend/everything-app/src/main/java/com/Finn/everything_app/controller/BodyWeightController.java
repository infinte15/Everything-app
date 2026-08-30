package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.BodyWeightEntryDTO;
import com.Finn.everything_app.dto.BodyWeightSeriesDTO;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.BodyWeightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Koerpergewicht: Verlauf, Eintraege und Zielgewicht. */
@RestController
@RequestMapping("/api/sports/bodyweight")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BodyWeightController {

    private final BodyWeightService bodyWeightService;

    /**
     * Verlauf ab {@code from} (ohne Angabe: alles) plus die letzten beiden Werte und das Ziel.
     */
    @GetMapping
    public ResponseEntity<BodyWeightSeriesDTO> getSeries(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {

        return ResponseEntity.ok(bodyWeightService.getSeries(userId, from));
    }

    /** Legt den Eintrag des Tages an - oder ueberschreibt ihn, wenn es schon einen gibt. */
    @PostMapping
    public ResponseEntity<BodyWeightEntryDTO> log(
            @CurrentUser Long userId,
            @Valid @RequestBody BodyWeightEntryDTO request) {

        return ResponseEntity.ok(bodyWeightService.log(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        bodyWeightService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Setzt das Zielgewicht. {@code {"targetWeightKg": null}} entfernt es wieder - deshalb eine
     * Map und kein DTO mit Validierung: ein fehlendes Feld und ein bewusstes null muessen hier
     * dasselbe bedeuten duerfen, naemlich "kein Ziel".
     */
    @PutMapping("/target")
    public ResponseEntity<Map<String, Double>> setTarget(
            @CurrentUser Long userId,
            @RequestBody Map<String, Double> body) {

        Double target = bodyWeightService.setTarget(userId, body.get("targetWeightKg"));
        return ResponseEntity.ok(java.util.Collections.singletonMap("targetWeightKg", target));
    }
}
