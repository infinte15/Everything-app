package com.Finn.everything_app.service.bank;

import com.Finn.everything_app.exception.BankConnectionException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Aufbau des Tokens ist vollstaendig vorgegeben, und ein Fehler darin sieht von aussen immer
 * gleich aus: 401. Deshalb steht hier fest, was im Kopf und in den Claims stehen muss.
 */
class EnableBankingTokenFactoryTest {

    private static KeyPair schluesselpaar;

    @TempDir Path verzeichnis;

    @BeforeAll
    static void erzeugeSchluessel() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        schluesselpaar = generator.generateKeyPair();
    }

    @Test
    void tokenTraegtKidIssuerUndAudience() throws Exception {
        EnableBankingTokenFactory factory = factory(pkcs8Pem());

        Jws<Claims> token = Jwts.parser()
                .verifyWith(schluesselpaar.getPublic())
                .build()
                .parseSignedClaims(factory.currentToken());

        assertEquals("meine-app-id", token.getHeader().getKeyId(),
                "kid ist die Application-ID - ohne sie kann die Gegenseite den Schlüssel nicht zuordnen");
        assertEquals("JWT", token.getHeader().getType());
        assertEquals("RS256", token.getHeader().getAlgorithm(), "andere Verfahren werden abgelehnt");
        assertEquals("enablebanking.com", token.getPayload().getIssuer());
        assertTrue(token.getPayload().getAudience().contains("api.enablebanking.com"));
    }

    @Test
    void laufzeitBleibtUnterVierundzwanzigStunden() throws Exception {
        EnableBankingTokenFactory factory = factory(pkcs8Pem());

        Claims claims = Jwts.parser()
                .verifyWith(schluesselpaar.getPublic())
                .build()
                .parseSignedClaims(factory.currentToken())
                .getPayload();

        Duration laufzeit = Duration.between(
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());

        // Ein längeres Token wird von der Gegenseite grundsätzlich abgelehnt.
        assertTrue(laufzeit.toHours() <= 24, "Laufzeit war " + laufzeit);
        assertTrue(laufzeit.toMinutes() > 0);
    }

    @Test
    void tokenWirdWiederverwendet() throws Exception {
        EnableBankingTokenFactory factory = factory(pkcs8Pem());

        // Für jeden Aufruf neu zu signieren wäre bei einem Erst-Import über hunderte Seiten
        // hunderte RSA-Signaturen für nichts.
        assertEquals(factory.currentToken(), factory.currentToken());
    }

    @Test
    void fehlendeApplicationIdErgibtEineVerstaendlicheMeldung() throws Exception {
        EnableBankingProperties properties = new EnableBankingProperties();
        properties.setProvider("live");
        properties.setPrivateKeyPath(pkcs8Pem().toString());
        properties.setApplicationId(null);

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> new EnableBankingTokenFactory(properties).currentToken());

        assertTrue(fehler.getMessage().contains("application-id"), fehler.getMessage());
    }

    @Test
    void fehlenderSchluesselErgibtEineVerstaendlicheMeldung() {
        EnableBankingProperties properties = new EnableBankingProperties();
        properties.setApplicationId("meine-app-id");
        properties.setPrivateKeyPath(verzeichnis.resolve("gibt-es-nicht.pem").toString());

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> new EnableBankingTokenFactory(properties).currentToken());

        // Der häufigste Fall beim Einrichten: die Datei wurde nie heruntergeladen.
        assertTrue(fehler.getMessage().contains("Control-Panel"), fehler.getMessage());
    }

    @Test
    void pkcs1SchluesselWirdBenanntStattAlsAllgemeinerFehler() throws Exception {
        Path pem = verzeichnis.resolve("pkcs1.pem");
        Files.writeString(pem, """
                -----BEGIN RSA PRIVATE KEY-----
                MIIBOgIBAAJBAK
                -----END RSA PRIVATE KEY-----
                """);

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> factory(pem).currentToken());

        // "Der Schlüssel ist ungültig" hilft niemandem weiter - der Umwandlungsbefehl schon.
        assertTrue(fehler.getMessage().contains("openssl"), fehler.getMessage());
    }

    // ==================== Hilfen ====================

    private EnableBankingTokenFactory factory(Path pem) {
        EnableBankingProperties properties = new EnableBankingProperties();
        properties.setProvider("live");
        properties.setApplicationId("meine-app-id");
        properties.setPrivateKeyPath(pem.toString());
        return new EnableBankingTokenFactory(properties);
    }

    private Path pkcs8Pem() throws Exception {
        Path pem = verzeichnis.resolve("enablebanking.pem");
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(schluesselpaar.getPrivate().getEncoded());
        Files.writeString(pem,
                "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
        return pem;
    }
}
