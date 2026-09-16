package com.Finn.everything_app.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /** Wer kein client-Claim traegt, ist die App selbst. Gilt auch fuer alle Bestandstoken. */
    public static final String CLIENT_APP = "app";

    /** Nero, die Sprachschnittstelle. Laeuft unter derselben userId, darf aber weniger. */
    public static final String CLIENT_NERO = "nero";

    /** Wird ein Token ohne {@code tv}-Claim vorgelegt, gilt Stand 0 - siehe {@link #extractTokenVersion}. */
    public static final int INITIAL_TOKEN_VERSION = 0;

    //Generiere JWT Token
    public String generateToken(String username, Long userId, int tokenVersion) {
        return generateToken(username, userId, tokenVersion, CLIENT_APP, expiration);
    }

    /**
     * Token fuer einen bestimmten Client mit eigener Laufzeit.
     *
     * <p>Die App hat genau einen Nutzer, und jede Entity haengt per Fremdschluessel an ihm - ein
     * eigener technischer Nutzer fuer Nero haette also einen leeren Kalender. Nero laeuft deshalb
     * unter derselben {@code userId} und unterscheidet sich nur ueber diesen Claim. Daraus leitet
     * {@link JwtAuthenticationFilter} die Rolle ab, mit der {@link SecurityConfig} die
     * gefaehrlichen Pfade sperrt.
     *
     * <p>Widerrufen laesst sich auch dieses Token ueber {@code POST /api/auth/logout-all}: es
     * zaehlt {@code tokenVersion} am Nutzer hoch und entwertet damit App- und Nero-Token
     * zugleich. Eine Sperrliste einzelner Token gibt es weiterhin nicht - das bleibt der
     * Grund, warum Nero nicht loeschen darf.
     */
    public String generateToken(String username, Long userId, int tokenVersion,
                                String client, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("client", client);
        claims.put("tv", tokenVersion);
        return createToken(claims, username, ttlMillis);
    }

    //Erstelle Token
    private String createToken(Map<String, Object> claims, String subject, long ttlMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    //Extrahiere Username
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    //Extrahiere User-ID aus Token
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * Extrahiere den Client. Fehlt der Claim, ist es ein Token aus der Zeit davor - also die App.
     */
    public String extractClient(String token) {
        String client = extractAllClaims(token).get("client", String.class);
        return (client == null || client.isBlank()) ? CLIENT_APP : client;
    }

    /**
     * Extrahiere den Widerrufs-Stand. Fehlt der Claim, ist es ein Token aus der Zeit davor -
     * es zaehlt als Stand 0 und bleibt damit gueltig, solange am Nutzer nie widerrufen wurde.
     * Nach dem ersten {@code logout-all} faellt es wie jedes andere alte Token heraus.
     */
    public int extractTokenVersion(String token) {
        Integer tv = extractAllClaims(token).get("tv", Integer.class);
        return tv == null ? INITIAL_TOKEN_VERSION : tv;
    }

    //Extrahiere Expiration Date
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //Extrahiere spezifischen Claim
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    //Extrahiere alle Claims
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //Prüfe ob Token abgelaufen ist
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    //Validiere Token
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }
}