package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.LoginResponse;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.security.JwtUtil;
import com.Finn.everything_app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-Login fuer die lokale Entwicklung: gibt ohne Anmeldung ein gueltiges JWT fuer
 * {@code dev_tester} aus und legt den Nutzer bei Bedarf an.
 *
 * <p><strong>Existiert als Bean nur bei {@code app.dev-login.enabled=true}.</strong> In Produktion
 * gibt es den Pfad damit schlicht nicht (404), statt ungeschuetzt-aber-hoffentlich-nicht-gefunden.
 * Bewusst dieselbe Eigenschaft, die auch {@code SecurityConfig} freischaltet - mit zwei Schaltern
 * (Profil hier, Property dort) haette man den Zustand "Bean da, Pfad zu" und umgekehrt.
 */
@ConditionalOnProperty(name = "app.dev-login.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/dev-login")
    public ResponseEntity<LoginResponse> devLogin() {
        User user;
        try {
            user = userService.findByUsername("dev_tester");
        } catch (RuntimeException e) {
            user = userService.registerUser("dev_tester", "dev@tester.com", "devpassword123");
        }

        userService.updateLastLogin(user.getId());
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        return ResponseEntity.ok(
                new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail()));
    }
}
