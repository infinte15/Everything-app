package com.Finn.everything_app.controller;

import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.dto.UserPreferencesDTO;
import com.Finn.everything_app.mapper.UserPreferencesMapper;
import com.Finn.everything_app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Die Einstellungen des angemeldeten Nutzers.
 *
 * <p>Der gemeinsame Pfad steht vollstaendig am {@code @RequestMapping} der Klasse, die Methoden
 * tragen nur noch ihr Verb. Kommt hier einmal ein Endpunkt dazu, der NICHT unter
 * {@code /preferences} liegt, muss der Pfad wieder aufgeteilt werden — die URLs bleiben davon
 * unberuehrt, gemeint ist in beiden Formen {@code /api/user/preferences}.
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserPreferencesMapper preferencesMapper;

    /**
     * Bewusst getOrCreatePreferences: Nutzer, die vor der Einführung der Preferences angelegt
     * wurden, haben keine Zeile und würden sonst einen Fehler statt der Defaults bekommen.
     */
    @GetMapping
    public ResponseEntity<UserPreferencesDTO> getPreferences(@CurrentUser Long userId) {
        return ResponseEntity.ok(preferencesMapper.toDTO(userService.getOrCreatePreferences(userId)));
    }

    @PutMapping
    public ResponseEntity<UserPreferencesDTO> updatePreferences(
            @CurrentUser Long userId,
            @Valid @RequestBody UserPreferencesDTO dto) {
        return ResponseEntity.ok(preferencesMapper.toDTO(
                userService.updatePreferences(userId, preferencesMapper.toEntity(dto))));
    }
}
