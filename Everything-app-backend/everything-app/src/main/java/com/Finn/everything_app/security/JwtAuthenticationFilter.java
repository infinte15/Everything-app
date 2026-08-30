package com.Finn.everything_app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Auch beim ASYNC-Dispatch filtern — sonst antwortet jeder Long-Poll mit 403.
     *
     * {@link OncePerRequestFilter} lässt asynchrone Dispatches per Vorgabe aus. Bei einer normalen
     * Anfrage ist das richtig (der Filter lief bereits), bei einer über {@code DeferredResult}
     * geparkten aber nicht: Spring Security filtert ASYNC mit, und mit
     * {@code SessionCreationPolicy.STATELESS} gibt es keinen Speicher, aus dem der SecurityContext
     * beim zweiten Dispatch zurückkäme. Der Nutzer wäre beim Aufwachen unauthentifiziert — und
     * zwar erst NACH der vollen Wartezeit, was den Fehler beim Suchen besonders unangenehm macht.
     *
     * Der Authorization-Header hängt weiterhin am selben Request, der Filter authentifiziert also
     * schlicht erneut. Abgesichert durch
     * {@code CalendarControllerTest#awaitScheduleStatusBleibtNachAsyncDispatchAuthentifiziert}.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Authorization Header vorhanden und valid?
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrahiere Token
        jwt = authHeader.substring(7);

        try {
            // Extrahiere Username
            username = jwtUtil.extractUsername(jwt);

            // Wenn Username vorhanden und noch nicht authentifiziert
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Validiere Token
                if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {

                    // Erstelle Authentication Token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Setze Authentication im Security Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token ungültig oder abgelaufen
            logger.error("JWT Token validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}