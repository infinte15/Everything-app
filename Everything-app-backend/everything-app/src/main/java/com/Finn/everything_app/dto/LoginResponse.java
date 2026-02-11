package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;  // JWT Token
    private Long userId;
    private String username;
    private String email;
}