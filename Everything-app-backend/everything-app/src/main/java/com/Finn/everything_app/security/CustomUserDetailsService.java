package com.Finn.everything_app.security;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // AppUserDetails statt Spring-User: der JwtAuthenticationFilter vergleicht die
        // tokenVersion aus dem Token mit dem Stand am Nutzer und braucht ihn deshalb hier -
        // sonst waere pro Anfrage eine zweite Abfrage noetig.
        return new AppUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                user.getId(),
                user.getTokenVersion(),
                new ArrayList<>() // Für Rollen
        );
    }
}