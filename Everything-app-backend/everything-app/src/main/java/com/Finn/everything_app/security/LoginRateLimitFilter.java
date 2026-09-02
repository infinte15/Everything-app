package com.Finn.everything_app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bremst Rateversuche auf {@code /api/auth/login} - nach den Aenderungen aus Phase 1 der einzige
 * oeffentliche Weg an ein Token.
 *
 * <p>Vor Cloudflare liegt zusaetzlich eine Rate-Limiting-Rule auf demselben Pfad. Dieser Filter
 * greift auch dann, wenn die Anfrage nicht von dort kommt - etwa aus dem LAN gegen den Container.
 *
 * <p>Bewusst ohne Bibliothek: ein fester Zaehler pro Fenster ist fuer diesen Zweck genau richtig,
 * und der Bestand kommt ohne zusaetzliche Abhaengigkeit aus. Wichtig ist stattdessen das
 * <strong>Aufraeumen</strong> - eine Map, die pro gesehener IP einen Eintrag behaelt und nie
 * loescht, waere selbst der Angriffspunkt.
 *
 * <p>Die Grenze gilt pro Client-IP. Damit das hinter Caddy und Cloudflare die echte IP ist,
 * braucht es {@code server.forward-headers-strategy=NATIVE} (steht im Prod-Profil). Ohne das
 * saehe der Filter nur die Adresse des Reverse Proxy und wuerde alle Clients gemeinsam zaehlen.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // vor der Security-Filterkette, damit gar nichts erst geprueft wird
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH = "/api/auth/login";

    /** Ab so vielen Eintraegen wird beim naechsten Zugriff aufgeraeumt. */
    private static final int CLEANUP_THRESHOLD = 1_000;

    private final int maxAttempts;
    private final Duration window;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(
            @Value("${app.login-rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${app.login-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!PATH.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (allow(request.getRemoteAddr())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
        response.getWriter().write(
                "{\"message\":\"Zu viele Anmeldeversuche. Bitte kurz warten.\"}");
    }

    private boolean allow(String clientIp) {
        Instant now = Instant.now();

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.isExpired(now, window));
        }

        Window w = windows.compute(clientIp, (ip, existing) ->
                (existing == null || existing.isExpired(now, window)) ? new Window(now) : existing);

        return w.count.incrementAndGet() <= maxAttempts;
    }

    private record Window(Instant startedAt, AtomicInteger count) {
        Window(Instant startedAt) {
            this(startedAt, new AtomicInteger());
        }

        boolean isExpired(Instant now, Duration window) {
            return startedAt.plus(window).isBefore(now);
        }
    }
}
