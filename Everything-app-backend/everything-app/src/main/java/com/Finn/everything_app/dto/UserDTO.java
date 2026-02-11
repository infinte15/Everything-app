package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class UserDTO {
    private Long id;

    @NotBlank(message = "Username darf nicht leer sein")
    @Size(min = 3, max = 50, message = "Username muss zwischen 3 und 50 Zeichen lang sein")
    private String username;

    @NotBlank(message = "Email darf nicht leer sein")
    @Email(message = "Ungültige Email-Adresse")
    private String email;

    // Passwort nur beim Registrieren/Ändern
    @Size(min = 6, message = "Passwort muss mindestens 6 Zeichen lang sein")
    private String password;
}