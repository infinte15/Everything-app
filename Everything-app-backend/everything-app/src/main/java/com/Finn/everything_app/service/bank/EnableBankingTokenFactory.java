package com.Finn.everything_app.service.bank;

import com.Finn.everything_app.exception.BankConnectionException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Erzeugt das Zugriffstoken fuer Enable Banking.
 *
 * <p>Es gibt keinen Token-Endpunkt und keinen Refresh: das selbst signierte JWT <em>ist</em> die
 * Zugangsberechtigung. Aufbau ist fest vorgegeben - RS256, {@code kid} = Application-ID,
 * {@code iss} = enablebanking.com, {@code aud} = api.enablebanking.com. Laenger als 24 Stunden
 * gueltige Tokens werden abgelehnt; hier ist die Laufzeit eine Stunde, erneuert nach 50 Minuten.
 *
 * <p>Der private Schluessel liegt als PKCS#8-PEM vor, das liest der {@code KeyFactory} aus dem JDK -
 * BouncyCastle waere nur noetig, wenn es ein PKCS#1-Schluessel ({@code BEGIN RSA PRIVATE KEY}) waere.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnableBankingTokenFactory {

    private static final String ISSUER = "enablebanking.com";
    private static final String AUDIENCE = "api.enablebanking.com";
    private static final long LIFETIME_SECONDS = 3600;
    private static final long REFRESH_BEFORE_SECONDS = 600;

    private final EnableBankingProperties properties;

    private PrivateKey cachedKey;
    private String cachedToken;
    private Instant cachedTokenExpiry;

    /** Liefert ein gueltiges Token; erneuert es zehn Minuten vor Ablauf. */
    public synchronized String currentToken() {
        Instant now = Instant.now();
        if (cachedToken != null && cachedTokenExpiry != null
                && now.isBefore(cachedTokenExpiry.minusSeconds(REFRESH_BEFORE_SECONDS))) {
            return cachedToken;
        }

        if (properties.getApplicationId() == null || properties.getApplicationId().isBlank()) {
            throw new BankConnectionException(
                    "Die Bankanbindung ist nicht eingerichtet: enablebanking.application-id fehlt.");
        }

        Instant expiry = now.plusSeconds(LIFETIME_SECONDS);
        cachedToken = Jwts.builder()
                .header()
                    .keyId(properties.getApplicationId())
                    .type("JWT")
                    .and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey(), Jwts.SIG.RS256)
                .compact();
        cachedTokenExpiry = expiry;
        return cachedToken;
    }

    private synchronized PrivateKey privateKey() {
        if (cachedKey != null) {
            return cachedKey;
        }
        Path path = Path.of(properties.getPrivateKeyPath());
        if (!Files.isReadable(path)) {
            throw new BankConnectionException(
                    "Der private Schlüssel für die Bankanbindung ist nicht lesbar: " + path
                            + ". Die Datei wird beim Anlegen der Anwendung im Enable-Banking-"
                            + "Control-Panel heruntergeladen.");
        }
        try {
            String pem = Files.readString(path)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            cachedKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            return cachedKey;
        } catch (IllegalArgumentException e) {
            // Der haeufigste Fehlerfall: PKCS#1 statt PKCS#8 ("BEGIN RSA PRIVATE KEY").
            throw new BankConnectionException(
                    "Der private Schlüssel ist kein PKCS#8-PEM. Erwartet wird ein Block "
                            + "\"BEGIN PRIVATE KEY\"; \"BEGIN RSA PRIVATE KEY\" muss vorher "
                            + "umgewandelt werden (openssl pkcs8 -topk8 -nocrypt).");
        } catch (Exception e) {
            throw new BankConnectionException(
                    "Der private Schlüssel konnte nicht gelesen werden: " + e.getMessage());
        }
    }
}
