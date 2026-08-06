package com.Finn.everything_app.service.bank;

import com.Finn.everything_app.exception.BankConnectionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zugriff auf die Enable-Banking-Schnittstelle.
 *
 * <p>Erster ausgehender HTTP-Client im Projekt. {@code RestClient} statt {@code WebClient}, weil er
 * mit {@code spring-boot-starter-web} schon vorhanden ist und keine zusaetzliche Abhaengigkeit
 * kostet.
 *
 * <p>Die Antworten werden bewusst als {@link JsonNode} gelesen und von Hand in die eigenen Records
 * uebersetzt, statt sie auf gespiegelte JSON-Klassen zu binden: fast jedes Feld der Spezifikation
 * ist optional, und die wenigen, auf die es ankommt, sollen an einer Stelle sichtbar geprueft
 * werden statt verstreut ueber zwei Dutzend Klassen.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "enablebanking.provider", havingValue = "live")
public class EnableBankingClient implements BankDataProvider {

    private static final String BASE_URL = "https://api.enablebanking.com";

    /** Die Schnittstelle verlangt fuer valid_until RFC3339 mit Zeitzonenversatz. */
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final EnableBankingTokenFactory tokenFactory;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EnableBankingClient(EnableBankingTokenFactory tokenFactory,
                               ObjectMapper objectMapper,
                               RestClient.Builder restClientBuilder) {
        this.tokenFactory = tokenFactory;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        log.info("Bankanbindung laeuft im LIVE-Modus gegen {}", BASE_URL);
    }

    @Override
    public String providerName() {
        return "enablebanking";
    }

    // ==================== Institute ====================

    @Override
    public List<AspspInfo> listAspsps(String country) {
        JsonNode response = get("/aspsps?country=" + (country == null ? "DE" : country), null);

        List<AspspInfo> result = new ArrayList<>();
        for (JsonNode node : response.path("aspsps")) {
            result.add(new AspspInfo(
                    node.path("name").asText(null),
                    node.path("country").asText(null),
                    node.path("logo").asText(null),
                    node.path("group").path("name").asText(null),
                    node.path("beta").asBoolean(false),
                    supportsRedirect(node),
                    // maximum_consent_validity kommt in Sekunden.
                    Math.max(1, node.path("maximum_consent_validity").asInt(0) / 86400)));
        }
        return result;
    }

    /**
     * Nicht jedes Institut kann Redirect. Die Genossenschaftsbanken etwa arbeiten ausschliesslich
     * DECOUPLED/EMBEDDED - dort wuerde ein Redirect in einem leeren Browserfenster enden.
     */
    private boolean supportsRedirect(JsonNode aspsp) {
        for (JsonNode method : aspsp.path("auth_methods")) {
            if ("REDIRECT".equalsIgnoreCase(method.path("approach").asText(""))) {
                return true;
            }
        }
        return false;
    }

    // ==================== Zustimmung ====================

    @Override
    public AuthStart startAuth(AuthRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access", Map.of(
                "valid_until", request.validUntil().atStartOfDay()
                        .atOffset(ZoneOffset.UTC).format(RFC3339),
                "balances", true,
                "transactions", true));
        body.put("aspsp", Map.of("name", request.aspspName(), "country", request.aspspCountry()));
        body.put("state", request.state());
        body.put("redirect_url", request.redirectUrl());
        // psu_type wird dringend empfohlen: die Vorgabe haengt sonst am Institut, und ein
        // Fehlgriff fuehrt zu einer fehlschlagenden Anmeldung oder zu fehlenden Konten.
        body.put("psu_type", "personal");
        body.put("language", "de");

        JsonNode response = post("/auth", body, null);
        return new AuthStart(
                response.path("url").asText(null),
                response.path("authorization_id").asText(null));
    }

    @Override
    public SessionResult redeemSession(String code) {
        JsonNode response = post("/sessions", Map.of("code", code), null);

        List<SessionResult.ProviderAccount> accounts = new ArrayList<>();
        for (JsonNode node : response.path("accounts")) {
            String hash = node.path("identification_hash").asText(null);
            if (hash == null) {
                log.warn("Konto ohne identification_hash uebersprungen - ohne stabilen Schluessel "
                        + "waere es bei jeder Neu-Autorisierung ein neues Konto");
                continue;
            }
            accounts.add(new SessionResult.ProviderAccount(
                    // uid darf fehlen (gesperrte oder aufgeloeste Konten) - dann ist das Konto
                    // sichtbar, aber nicht abrufbar.
                    node.path("uid").asText(null),
                    hash,
                    node.path("account_id").path("iban").asText(null),
                    firstNonBlank(node.path("name").asText(null), node.path("product").asText(null)),
                    node.path("currency").asText("EUR")));
        }

        LocalDateTime validUntil = parseOffsetDateTime(
                response.path("access").path("valid_until").asText(null));

        return new SessionResult(response.path("session_id").asText(null), validUntil, accounts);
    }

    // ==================== Kontodaten ====================

    @Override
    public List<BankBalance> fetchBalances(String accountUid, PsuContext psu) {
        JsonNode response = get("/accounts/" + accountUid + "/balances", psu);

        List<BankBalance> result = new ArrayList<>();
        for (JsonNode node : response.path("balances")) {
            JsonNode amount = node.path("balance_amount");
            result.add(new BankBalance(
                    node.path("balance_type").asText(null),
                    node.path("name").asText(null),
                    parseAmount(amount.path("amount").asText("0")),
                    amount.path("currency").asText("EUR")));
        }
        return result;
    }

    @Override
    public List<BankTx> fetchTransactions(String accountUid, LocalDate from, boolean deepBackfill, PsuContext psu) {
        List<BankTx> result = new ArrayList<>();
        String continuationKey = null;
        int pages = 0;

        do {
            StringBuilder path = new StringBuilder("/accounts/").append(accountUid)
                    .append("/transactions?date_from=").append(from);
            if (deepBackfill) {
                // "longest" nimmt date_from nur als Untergrenze, laeuft bis zum aeltesten
                // verfuegbaren Umsatz und scheitert nicht an einem nicht abgedeckten Zeitraum.
                path.append("&strategy=longest");
            }
            if (continuationKey != null) {
                path.append("&continuation_key=").append(continuationKey);
            }

            JsonNode response = get(path.toString(), psu);
            for (JsonNode node : response.path("transactions")) {
                BankTx tx = toBankTx(node);
                if (tx != null) {
                    result.add(tx);
                }
            }

            // Eine leere Seite mit gesetztem Schluessel ist zulaessig - erst null beendet die Schleife.
            continuationKey = response.path("continuation_key").isNull()
                    ? null
                    : response.path("continuation_key").asText(null);
            pages++;
        } while (continuationKey != null && pages < 200);

        if (continuationKey != null) {
            log.warn("Umsatzabruf nach {} Seiten abgebrochen - continuation_key war weiterhin gesetzt", pages);
        }
        return result;
    }

    private BankTx toBankTx(JsonNode node) {
        JsonNode amount = node.path("transaction_amount");
        String status = node.path("status").asText("");
        String indicator = node.path("credit_debit_indicator").asText("");

        LocalDate bookingDate = parseDate(node.path("booking_date").asText(null));
        if (bookingDate == null) {
            bookingDate = parseDate(node.path("value_date").asText(null));
        }
        if (bookingDate == null) {
            log.warn("Buchung ohne Datum uebersprungen: {}", node.path("entry_reference").asText("?"));
            return null;
        }

        return new BankTx(
                node.path("entry_reference").asText(null),
                "BOOK".equalsIgnoreCase(status),
                "CRDT".equalsIgnoreCase(indicator),
                Math.abs(parseAmount(amount.path("amount").asText("0"))),
                amount.path("currency").asText("EUR"),
                bookingDate,
                parseDate(node.path("value_date").asText(null)),
                counterpartyOf(node, "CRDT".equalsIgnoreCase(indicator)),
                remittanceOf(node));
    }

    /**
     * Bei einer Ausgabe ist die Gegenpartei der Empfaenger, bei einer Einnahme der Absender -
     * vertauscht man das, traegt jede Buchung den eigenen Namen.
     */
    private String counterpartyOf(JsonNode node, boolean income) {
        String name = income
                ? node.path("debtor").path("name").asText(null)
                : node.path("creditor").path("name").asText(null);
        return firstNonBlank(name,
                node.path("creditor").path("name").asText(null),
                node.path("debtor").path("name").asText(null));
    }

    /** remittance_information ist ein Array von Zeilen, kein einzelner String. */
    private String remittanceOf(JsonNode node) {
        JsonNode info = node.path("remittance_information");
        if (info.isArray() && !info.isEmpty()) {
            List<String> lines = new ArrayList<>();
            info.forEach(line -> {
                String text = line.asText("").trim();
                if (!text.isEmpty()) {
                    lines.add(text);
                }
            });
            return lines.isEmpty() ? null : String.join("\n", lines);
        }
        return firstNonBlank(info.asText(null), node.path("note").asText(null));
    }

    // ==================== HTTP ====================

    private JsonNode get(String path, PsuContext psu) {
        return exchange(() -> restClient.get()
                .uri(path)
                .headers(headers -> applyHeaders(headers, psu))
                .retrieve()
                .body(String.class), path);
    }

    private JsonNode post(String path, Object body, PsuContext psu) {
        return exchange(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyHeaders(headers, psu))
                .body(body)
                .retrieve()
                .body(String.class), path);
    }

    private JsonNode exchange(java.util.function.Supplier<String> call, String path) {
        String raw;
        try {
            raw = call.get();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw translate(e, path);
        } catch (Exception e) {
            throw new BankConnectionException(
                    "Die Bank ist gerade nicht erreichbar (" + e.getMessage() + ").");
        }
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new BankConnectionException("Die Antwort der Bank war nicht lesbar.");
        }
    }

    /**
     * Uebersetzt einen Fehler der Schnittstelle in eine verstaendliche Meldung.
     *
     * <p>Verzweigt wird am Feld {@code error}, nicht am HTTP-Status: {@code code} im Fehlerbody ist
     * nur der Status als Zahl, und eine abgelaufene Zustimmung kommt als 401 - genau wie mehrere
     * andere Faelle.
     */
    private BankConnectionException translate(org.springframework.web.client.RestClientResponseException e,
                                              String path) {
        String errorCode = null;
        String detail = null;
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            errorCode = body.path("error").asText(null);
            detail = firstNonBlank(body.path("detail").asText(null), body.path("message").asText(null));
        } catch (Exception ignored) {
            // Fehlerbody unlesbar - dann bleibt es beim Status.
        }

        log.warn("Bankabruf {} fehlgeschlagen: status={} error={} detail={}",
                path, e.getStatusCode().value(), errorCode, detail);

        String message = switch (errorCode == null ? "" : errorCode) {
            case "EXPIRED_SESSION", "CLOSED_SESSION", "REVOKED_SESSION", "SESSION_DOES_NOT_EXIST" ->
                    "Die Zustimmung für dieses Konto ist abgelaufen oder wurde widerrufen. "
                            + "Die Verbindung muss neu eingerichtet werden.";
            case "ASPSP_RATE_LIMIT_EXCEEDED" ->
                    "Die Bank erlaubt heute keine weiteren automatischen Abrufe mehr. "
                            + "Der nächste Versuch ist in einigen Stunden wieder möglich.";
            case "ASPSP_TIMEOUT", "ASPSP_ERROR" ->
                    "Die Bank hat nicht rechtzeitig geantwortet. Bitte später erneut versuchen.";
            case "NO_ACCOUNTS_ADDED" ->
                    "Für diese Anwendung ist noch kein Konto freigeschaltet. Im Enable-Banking-"
                            + "Control-Panel unter \"Link accounts\" das eigene Konto verknüpfen.";
            case "REDIRECT_URI_NOT_ALLOWED" ->
                    "Die Rücksprungadresse ist im Control Panel nicht hinterlegt.";
            case "WRONG_AUTHORIZATION_CODE", "EXPIRED_AUTHORIZATION_CODE" ->
                    "Der Anmeldevorgang ist abgelaufen. Bitte die Verbindung erneut starten.";
            default -> "Die Bank hat die Anfrage abgelehnt"
                    + (detail == null ? "." : ": " + detail);
        };
        return new BankConnectionException(message, errorCode);
    }

    private void applyHeaders(HttpHeaders headers, PsuContext psu) {
        headers.setBearerAuth(tokenFactory.currentToken());
        // Alles oder nichts: ein unvollstaendiger Satz PSU-Kopfzeilen wird zurueckgewiesen.
        // Ohne sie gilt der Abruf als unbeaufsichtigt und faellt unter das Tageslimit.
        if (psu != null && psu.isComplete()) {
            headers.set("Psu-Ip-Address", psu.ipAddress());
            headers.set("Psu-User-Agent", psu.userAgent());
            if (psu.language() != null && !psu.language().isBlank()) {
                headers.set("Psu-Accept-Language", psu.language());
            }
        }
    }

    // ==================== Hilfen ====================

    /** Betraege kommen als String; ueber double zu parsen wuerde Cent-Fehler einbauen. */
    private double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseOffsetDateTime(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equals(value)) {
                return value;
            }
        }
        return null;
    }
}
