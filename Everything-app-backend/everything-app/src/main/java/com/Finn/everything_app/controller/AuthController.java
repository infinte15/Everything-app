package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.service.UserService;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    /**
     * Selbstregistrierung. In Produktion aus: die App hat genau einen Nutzer, und ein offener
     * /register ist der zweite Weg an ein gueltiges Token vorbei am Passwort des Kontos.
     *
     * <p>Beim ersten Deploy einmal auf {@code true} starten, den eigenen Account anlegen, Flag
     * zurueck auf {@code false}, Container neu starten.
     */
    @Value("${app.registration.enabled:false}")
    private boolean registrationEnabled;

    //POST /api/auth/register  --> Registriere User
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserDTO userDTO) {
        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    new ErrorResponse("Registrierung ist deaktiviert.")
            );
        }

        // Vergebener Name oder vergebene Adresse kommen als BadRequestException heraus und werden
        // vom GlobalExceptionHandler in die übliche ErrorResponse-Form gebracht — hier nichts zu
        // fangen. Ein eigener catch-Block lieferte einen Körper ohne status/error/path.
        User user = userService.registerUser(
                userDTO.getUsername(),
                userDTO.getEmail(),
                userDTO.getPassword()
        );

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getTokenVersion());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail())
        );
    }

    //POST /api/auth/login --> Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        // BadCredentialsException läuft absichtlich durch: GlobalExceptionHandler beantwortet sie
        // bereits mit 401 und der vollständigen ErrorResponse. Der frühere catch-Block hier gab
        // stattdessen einen Körper ohne status/error/path zurück — ausgerechnet /api/auth/login
        // wich damit als einziger Endpunkt von der gemeinsamen Fehlerform ab.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userService.findByUsername(userDetails.getUsername());

        userService.updateLastLogin(user.getId());

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getTokenVersion());

        return ResponseEntity.ok(
                new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail())
        );
    }

    /**
     * Meldet alle Geraete ab: zaehlt {@code tokenVersion} am Nutzer hoch, womit jedes vorher
     * ausgegebene Token ungueltig wird - das eigene eingeschlossen, und das von Nero ebenso.
     *
     * <p>Das ist der Gegenwert zur Laufzeit von 30 Tagen ({@code jwt.expiration}): bei einem
     * verlorenen Geraet genuegt ein Aufruf. Ohne diesen Weg bliebe nur, {@code jwt.secret} zu
     * tauschen und das Backend neu zu starten.
     *
     * <p>Erfordert ein App-Token: {@code SecurityConfig} gibt {@code /api/auth/**} nur fuer
     * {@code ROLE_APP} frei, Nero kann sich also nicht selbst aussperren.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@CurrentUser Long userId) {
        userService.revokeAllTokens(userId);
        return ResponseEntity.noContent().build();
    }
}
