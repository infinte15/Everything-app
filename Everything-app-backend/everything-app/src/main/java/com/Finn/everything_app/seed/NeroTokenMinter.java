package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Erzeugt einmalig das langlebige Token fuer Nero und schreibt es ins Log.
 *
 * <p>Bewusst kein Endpunkt: ein Pfad, der Token ausgibt, waere dauerhaft
 * Angriffsflaeche - dieser Runner existiert wie {@code DevAuthController} nur, wenn
 * sein Schalter an ist, und ist im Normalbetrieb nicht einmal eine Bean.
 *
 * <pre>
 * lokal:  ./mvnw spring-boot:run -Dspring-boot.run.arguments="\
 *           --app.nero.mint-token=true --app.nero.mint-for-username=dev_tester"
 * Server: docker compose run --rm \
 *           -e APP_NERO_MINT_TOKEN=true -e APP_NERO_MINT_FOR_USERNAME=&lt;name&gt; backend
 * </pre>
 *
 * <p>Danach das Flag wieder aus. Widerrufen laesst sich das Token nur durch Rotation
 * von {@code jwt.secret} - deshalb darf Nero laut {@code SecurityConfig} nichts loeschen.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.nero.mint-token", havingValue = "true")
public class NeroTokenMinter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NeroTokenMinter.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /** Nero laeuft unter diesem Nutzer - eigene Daten haette ein eigener Nutzer nicht. */
    @Value("${app.nero.mint-for-username:}")
    private String username;

    @Value("${app.nero.token-days:365}")
    private long tokenDays;

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank()) {
            log.error("app.nero.mint-token ist an, aber app.nero.mint-for-username ist leer.");
            return;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.error("Kein Nutzer '{}' gefunden - kein Token erzeugt.", username);
            return;
        }

        String token = jwtUtil.generateToken(
                user.getUsername(),
                user.getId(),
                JwtUtil.CLIENT_NERO,
                Duration.ofDays(tokenDays).toMillis());

        // WARN, damit es auch bei hochgezogenem Log-Level nicht untergeht.
        log.warn("""

                =====================================================================
                NERO_APP_TOKEN (Nutzer '{}', gueltig {} Tage) - in Neros .env eintragen
                und app.nero.mint-token danach wieder auf false setzen:

                {}
                =====================================================================
                """, username, tokenDays, token);
    }
}
