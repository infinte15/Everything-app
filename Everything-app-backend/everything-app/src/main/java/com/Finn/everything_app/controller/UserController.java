package com.Finn.everything_app.controller;

import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.dto.UserPreferencesDTO;
import com.Finn.everything_app.mapper.UserPreferencesMapper;
import com.Finn.everything_app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserPreferencesMapper preferencesMapper;

    /**
     * Bewusst getOrCreatePreferences: Nutzer, die vor der Einführung der Preferences angelegt
     * wurden, haben keine Zeile und würden sonst einen Fehler statt der Defaults bekommen.
     */
    @GetMapping("/preferences")
    public ResponseEntity<UserPreferencesDTO> getPreferences(@CurrentUser Long userId) {
        return ResponseEntity.ok(preferencesMapper.toDTO(userService.getOrCreatePreferences(userId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserPreferencesDTO> updatePreferences(
            @CurrentUser Long userId,
            @Valid @RequestBody UserPreferencesDTO dto) {
        return ResponseEntity.ok(preferencesMapper.toDTO(
                userService.updatePreferences(userId, preferencesMapper.toEntity(dto))));
    }
}
