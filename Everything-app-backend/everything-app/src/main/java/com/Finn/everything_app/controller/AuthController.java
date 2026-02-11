package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.service.UserService;
import com.Finn.everything_app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
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
@CrossOrigin(origins = "*")  //Frontend-Zugriff
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    //POST /api/auth/register  --> Registriere User
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserDTO userDTO) {
        try {
            User user = userService.registerUser(
                    userDTO.getUsername(),
                    userDTO.getEmail(),
                    userDTO.getPassword()
            );

            String token = jwtUtil.generateToken(user.getUsername(),user.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail())
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(e.getMessage())
            );
        }
    }

    //POST /api/auth/login --> Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername());

            userService.updateLastLogin(user.getId());

            String token = jwtUtil.generateToken(user.getUsername(),user.getId());

            return ResponseEntity.ok(
                    new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail())
            );

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new ErrorResponse("Ungültige Anmeldedaten")
            );
        }
    }
}