package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.BankMapper;
import com.Finn.everything_app.model.BankConnection;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.BankSyncService;
import com.Finn.everything_app.service.bank.PsuContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bankanbindung: Institut auswaehlen, zustimmen, abrufen, trennen.
 *
 * <p>Zwei Dinge weichen hier vom Rest des Projekts ab, beide unvermeidlich:
 *
 * <ul>
 *   <li><strong>{@code /callback} ist oeffentlich</strong> und muss es sein - der Browser kehrt von
 *       der Bank ohne JWT zurueck. Die Zuordnung zum Nutzer laeuft ausschliesslich ueber den
 *       einmalig verwendbaren {@code state}.</li>
 *   <li><strong>{@code /callback} liefert HTML</strong>, als einziger Endpunkt im Projekt. Ein
 *       JSON-Rumpf im Browserfenster waere das Letzte, was der Nutzer nach der Anmeldung sieht.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finance/bank")
@RequiredArgsConstructor
@Slf4j
public class BankController {

    private final BankSyncService bankSyncService;
    private final BankMapper bankMapper;

    // ==================== Verbinden ====================

    @GetMapping("/aspsps")
    public ResponseEntity<List<AspspDTO>> listAspsps(
            @RequestParam(required = false, defaultValue = "DE") String country) {

        return ResponseEntity.ok(bankSyncService.listAspsps(country).stream()
                .map(bankMapper::toDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(
            @CurrentUser Long userId,
            @Valid @RequestBody ConnectBankRequest request) {

        String authUrl = bankSyncService.startAuthorization(
                userId, request.getAspspName(), request.getAspspCountry());

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Rueckkehr aus dem Bank-Login.
     *
     * <p>Der Erst-Import laeuft <strong>synchron hier</strong> und nicht im naechtlichen Job: die
     * volle Historie liefern die meisten Institute nur unmittelbar nach der Zustimmung, danach
     * bleiben 90 Tage. Wer diesen Aufruf asynchron macht, verliert die Historie unwiederbringlich.
     *
     * <p>{@code @CurrentUser} ist hier unbenutzbar - der Argument-Resolver wirft ohne
     * Authorization-Header, was aus dem fehlenden JWT eine 500 machen wuerde.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {

        if (error != null && !error.isBlank()) {
            return ResponseEntity.ok(page("Abgebrochen",
                    "Die Bank hat die Anmeldung abgelehnt oder du hast sie abgebrochen.", false));
        }

        try {
            BankConnection connection = bankSyncService.completeAuthorization(code, state);
            BankSyncService.SyncSummary summary =
                    bankSyncService.syncConnection(connection.getId(), psuContext(request), true);

            log.info("Erst-Import für {}: {} Buchungen aus {} Konten",
                    connection.getAspspName(), summary.imported(), summary.accounts());

            return ResponseEntity.ok(page("Konto verbunden",
                    summary.imported() + " Buchungen wurden übernommen. Du kannst dieses Fenster "
                            + "schließen und zur App zurückkehren.", true));

        } catch (Exception e) {
            log.warn("Callback fehlgeschlagen", e);
            return ResponseEntity.ok(page("Hat nicht geklappt", e.getMessage(), false));
        }
    }

    // ==================== Abrufen und verwalten ====================

    @GetMapping("/connections")
    public ResponseEntity<List<BankConnectionDTO>> getConnections(@CurrentUser Long userId) {
        return ResponseEntity.ok(bankSyncService.getConnections(userId).stream()
                .map(bankMapper::toDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<BankAccountDTO>> getAccounts(@CurrentUser Long userId) {
        return ResponseEntity.ok(bankSyncService.getAccounts(userId).stream()
                .map(bankMapper::toDTO)
                .collect(Collectors.toList()));
    }

    @PatchMapping("/accounts/{id}")
    public ResponseEntity<BankAccountDTO> updateAccount(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @RequestBody BankAccountDTO request) {

        return ResponseEntity.ok(bankMapper.toDTO(bankSyncService.updateAccount(
                userId, id, request.getSyncEnabled(), request.getDisplayName())));
    }

    /**
     * Vom Nutzer ausgeloester Abruf.
     *
     * <p>Reicht die Angaben des Aufrufers als PSU-Kontext durch - damit gilt der Abruf bei der Bank
     * als beaufsichtigt und faellt nicht unter das Limit von typischerweise vier Abrufen pro Tag.
     */
    @PostMapping("/sync")
    public ResponseEntity<BankSyncResultDTO> sync(
            @CurrentUser Long userId,
            HttpServletRequest request) {

        BankSyncService.SyncSummary summary = bankSyncService.syncUser(userId, psuContext(request));
        return ResponseEntity.ok(new BankSyncResultDTO(
                summary.accounts(), summary.imported(), summary.skipped(), summary.warnings()));
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> disconnect(@CurrentUser Long userId, @PathVariable Long id) {
        bankSyncService.disconnect(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Kennzeichnung fuer die Oberflaeche - im Demo-Betrieb sind alle Zahlen erfunden. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("demo", bankSyncService.isDemoProvider()));
    }

    // ==================== Hilfen ====================

    /**
     * Baut den PSU-Kontext aus dem eingehenden Request.
     *
     * <p>Alles-oder-nichts: ein unvollstaendiger Satz Kopfzeilen wird von der Schnittstelle
     * zurueckgewiesen. Fehlt etwas, wird deshalb {@code null} geliefert und der Abruf laeuft als
     * unbeaufsichtigt - langsamer, aber er laeuft.
     */
    private PsuContext psuContext(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String language = request.getHeader("Accept-Language");

        PsuContext psu = new PsuContext(ip, userAgent, language == null ? "de" : language);
        return psu.isComplete() ? psu : null;
    }

    /** Die einzige HTML-Seite des Projekts - im selben Dunkelgrau wie die App. */
    private String page(String heading, String message, boolean success) {
        String accent = success ? "#C2C1FF" : "#EC7C8A";
        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { margin:0; min-height:100vh; display:flex; align-items:center;
                           justify-content:center; background:#0E0E0E; color:#E7E5E5;
                           font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; }
                    .card { background:#131313; border-radius:12px; padding:32px; max-width:420px;
                            margin:24px; text-align:center; }
                    .dot { width:44px; height:44px; border-radius:22px; background:%s; margin:0 auto 20px; }
                    h1 { font-size:22px; font-weight:700; margin:0 0 12px; }
                    p { font-size:15px; line-height:1.5; color:#ACABAA; margin:0; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="dot"></div>
                    <h1>%s</h1>
                    <p>%s</p>
                  </div>
                </body>
                </html>
                """.formatted(escape(heading), accent, escape(heading), escape(message));
    }

    /** Der Text kann aus einer Fehlermeldung der Bank stammen und landet in einer HTML-Seite. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
