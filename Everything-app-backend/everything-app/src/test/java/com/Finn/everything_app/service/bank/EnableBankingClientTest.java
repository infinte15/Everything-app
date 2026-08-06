package com.Finn.everything_app.service.bank;

import com.Finn.everything_app.exception.BankConnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Der Client gegen einen gefaelschten Server.
 *
 * <p>Geprueft wird genau das, worin die Spezifikation von der naheliegenden Annahme abweicht:
 * die Umhuellung der Bankenliste, der Betrag als String, das Verwendungszweck-<em>Array</em>, die
 * Paginierung ueber den Antwortkoerper - und dass eine leere Seite die Schleife nicht beendet.
 */
class EnableBankingClientTest {

    private EnableBankingClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        EnableBankingTokenFactory tokenFactory = mock(EnableBankingTokenFactory.class);
        when(tokenFactory.currentToken()).thenReturn("test-token");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EnableBankingClient(tokenFactory, new ObjectMapper(), builder);
    }

    @Test
    void bankenlisteWirdAusdemUmschlagGeholt() {
        server.expect(requestTo("https://api.enablebanking.com/aspsps?country=DE"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"aspsps":[
                          {"name":"Sparkasse Bodensee","country":"DE","logo":"https://x/l.png",
                           "beta":false,"maximum_consent_validity":15552000,
                           "group":{"name":"Sparkassen"},
                           "auth_methods":[{"approach":"REDIRECT"}]},
                          {"name":"Volksbank Musterstadt","country":"DE",
                           "maximum_consent_validity":7776000,
                           "auth_methods":[{"approach":"DECOUPLED"}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<AspspInfo> institute = client.listAspsps("DE");

        assertEquals(2, institute.size(), "die Liste steckt unter \"aspsps\", nicht direkt im Rumpf");
        assertEquals("Sparkassen", institute.get(0).group());
        assertTrue(institute.get(0).redirectSupported());
        // maximum_consent_validity kommt in Sekunden: 15552000 s = 180 Tage.
        assertEquals(180, institute.get(0).maxConsentDays());
        assertFalse(institute.get(1).redirectSupported(),
                "DECOUPLED würde in einem leeren Browserfenster enden");
        server.verify();
    }

    @Test
    void paginierungLaeuftBisDerSchluesselNullIst() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions?date_from=")))
                .andRespond(withSuccess("""
                        {"transactions":[%s],"continuation_key":"seite-2"}
                        """.formatted(buchung("ref-1", "12.34")), MediaType.APPLICATION_JSON));

        // Eine leere Seite mit gesetztem Schluessel ist zulaessig - wer hier abbricht, verliert
        // alles, was danach kommt.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("continuation_key=seite-2")))
                .andRespond(withSuccess("""
                        {"transactions":[],"continuation_key":"seite-3"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("continuation_key=seite-3")))
                .andRespond(withSuccess("""
                        {"transactions":[%s],"continuation_key":null}
                        """.formatted(buchung("ref-2", "56.78")), MediaType.APPLICATION_JSON));

        List<BankTx> buchungen = client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), false, null);

        assertEquals(2, buchungen.size(), "die Buchung hinter der leeren Seite fehlt");
        assertEquals("ref-2", buchungen.get(1).entryReference());
        server.verify();
    }

    @Test
    void tiefimportSetztDieLaengsteStrategie() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("strategy=longest")))
                .andRespond(withSuccess("""
                        {"transactions":[],"continuation_key":null}
                        """, MediaType.APPLICATION_JSON));

        client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), true, null);
        server.verify();
    }

    @Test
    void buchungWirdVollstaendigUebersetzt() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions")))
                .andRespond(withSuccess("""
                        {"transactions":[{
                          "entry_reference":"ref-1",
                          "status":"BOOK",
                          "credit_debit_indicator":"DBIT",
                          "transaction_amount":{"currency":"EUR","amount":"13.99"},
                          "booking_date":"2026-08-03",
                          "value_date":"2026-08-04",
                          "creditor":{"name":"Netflix International B.V."},
                          "debtor":{"name":"Ich Selbst"},
                          "remittance_information":["Netflix Abo","Kundennummer 4711"]
                        }],"continuation_key":null}
                        """, MediaType.APPLICATION_JSON));

        BankTx tx = client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), false, null).get(0);

        assertTrue(tx.booked());
        assertFalse(tx.income(), "DBIT ist eine Ausgabe");
        // Der Betrag kommt als String; über double geparst entstünden Cent-Fehler.
        assertEquals(13.99, tx.amount(), 0.0001);
        assertEquals(LocalDate.of(2026, 8, 3), tx.bookingDate());
        assertEquals(LocalDate.of(2026, 8, 4), tx.valueDate());
        // Bei einer Ausgabe ist die Gegenpartei der Empfänger - vertauscht trüge jede Buchung
        // den eigenen Namen.
        assertEquals("Netflix International B.V.", tx.counterparty());
        // remittance_information ist ein Array von Zeilen, kein einzelner String.
        assertEquals("Netflix Abo\nKundennummer 4711", tx.description());
    }

    @Test
    void einnahmeNimmtDenAbsenderAlsGegenpartei() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions")))
                .andRespond(withSuccess("""
                        {"transactions":[{
                          "entry_reference":"ref-1","status":"BOOK","credit_debit_indicator":"CRDT",
                          "transaction_amount":{"currency":"EUR","amount":"2480.00"},
                          "booking_date":"2026-07-31",
                          "creditor":{"name":"Ich Selbst"},
                          "debtor":{"name":"Muster GmbH"},
                          "remittance_information":["Gehalt Juli"]
                        }],"continuation_key":null}
                        """, MediaType.APPLICATION_JSON));

        BankTx tx = client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), false, null).get(0);

        assertTrue(tx.income());
        assertEquals("Muster GmbH", tx.counterparty());
    }

    @Test
    void vorgemerkteBuchungWirdAlsSolcheGemeldet() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions")))
                .andRespond(withSuccess("""
                        {"transactions":[{
                          "status":"PDNG","credit_debit_indicator":"DBIT",
                          "transaction_amount":{"currency":"EUR","amount":"9.99"},
                          "booking_date":"2026-08-05",
                          "remittance_information":["Vorgemerkt"]
                        }],"continuation_key":null}
                        """, MediaType.APPLICATION_JSON));

        BankTx tx = client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), false, null).get(0);

        // Der Sync filtert sie aus - sie ändert sich noch und käme beim Buchen ein zweites Mal an.
        assertFalse(tx.booked());
        assertNull(tx.entryReference(), "vorgemerkte Buchungen tragen meist keine Referenz");
    }

    @Test
    void saldenTragenIhrenTypFuerDiePraeferenzkette() {
        server.expect(requestTo("https://api.enablebanking.com/accounts/uid-1/balances"))
                .andRespond(withSuccess("""
                        {"balances":[
                          {"name":"Gebucht","balance_type":"CLBD",
                           "balance_amount":{"currency":"EUR","amount":"1000.00"}},
                          {"name":"Erwartet","balance_type":"XPCD",
                           "balance_amount":{"currency":"EUR","amount":"957.83"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<BankBalance> salden = client.fetchBalances("uid-1", null);

        assertEquals(2, salden.size());
        BankBalance bevorzugt = salden.stream()
                .min(java.util.Comparator.comparingInt(BankBalance::preferenceRank))
                .orElseThrow();
        assertEquals("XPCD", bevorzugt.type(),
                "XPCD kommt dem am nächsten, was die Bank dem Nutzer anzeigt");
        assertEquals(957.83, bevorzugt.amount(), 0.0001);
    }

    @Test
    void kontoOhneIdentificationHashWirdUebersprungen() {
        server.expect(requestTo("https://api.enablebanking.com/sessions"))
                .andRespond(withSuccess("""
                        {"session_id":"sess-1",
                         "access":{"valid_until":"2027-02-02T12:00:00+00:00"},
                         "accounts":[
                           {"uid":"uid-1","identification_hash":"hash-1",
                            "account_id":{"iban":"DE02120300000000202051"},
                            "name":"Girokonto","currency":"EUR"},
                           {"uid":"uid-2","name":"Ohne Hash","currency":"EUR"}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        SessionResult session = client.redeemSession("code-1");

        // Ohne stabilen Schlüssel entstünde bei jeder Neu-Autorisierung ein zweites Konto samt
        // vollständiger Buchungshistorie.
        assertEquals(1, session.accounts().size());
        assertEquals("hash-1", session.accounts().get(0).identificationHash());
        assertNotNull(session.validUntil());
    }

    // ==================== Fehler ====================

    @Test
    void abgelaufeneZustimmungWirdAmErrorFeldErkanntNichtAmStatus() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/balances")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"Session expired","code":401,
                                 "error":"EXPIRED_SESSION","detail":"consent no longer valid"}
                                """));

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> client.fetchBalances("uid-1", null));

        // 401 allein sagt nichts: es bedeutet auch mehrere andere Dinge.
        assertEquals("EXPIRED_SESSION", fehler.getErrorCode());
        assertTrue(fehler.isConsentGone());
        assertTrue(fehler.getMessage().contains("neu eingerichtet"), fehler.getMessage());
    }

    @Test
    void abruflimitIstKeinDefekt() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"rate limit","code":429,
                                 "error":"ASPSP_RATE_LIMIT_EXCEEDED"}
                                """));

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> client.fetchTransactions("uid-1", LocalDate.of(2026, 1, 1), false, null));

        assertTrue(fehler.isRateLimited());
        assertFalse(fehler.isConsentGone(), "die Verbindung bleibt gültig");
    }

    @Test
    void unbekannterFehlerBehaeltDenTextDerBank() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/balances")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"nope","code":400,"error":"SOMETHING_NEW",
                                 "detail":"Konto nicht gefunden"}
                                """));

        BankConnectionException fehler = assertThrows(BankConnectionException.class,
                () -> client.fetchBalances("uid-1", null));

        assertTrue(fehler.getMessage().contains("Konto nicht gefunden"), fehler.getMessage());
    }

    // ==================== PSU-Kopfzeilen ====================

    @Test
    void vollstaendigerPsuKontextWirdMitgeschickt() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/balances")))
                .andExpect(header("Psu-Ip-Address", "192.0.2.10"))
                .andExpect(header("Psu-User-Agent", "EverythingApp/1.0"))
                .andExpect(header("Psu-Accept-Language", "de"))
                .andRespond(withSuccess("{\"balances\":[]}", MediaType.APPLICATION_JSON));

        client.fetchBalances("uid-1", new PsuContext("192.0.2.10", "EverythingApp/1.0", "de"));
        server.verify();
    }

    @Test
    void unvollstaendigerPsuKontextWirdGarNichtGeschickt() {
        // Alles oder nichts: ein unvollständiger Satz wird mit 422 zurückgewiesen. Lieber
        // unbeaufsichtigt abrufen als gar nicht.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/balances")))
                .andExpect(headerDoesNotExist("Psu-Ip-Address"))
                .andExpect(headerDoesNotExist("Psu-User-Agent"))
                .andRespond(withSuccess("{\"balances\":[]}", MediaType.APPLICATION_JSON));

        client.fetchBalances("uid-1", new PsuContext("192.0.2.10", null, "de"));
        server.verify();
    }

    private String buchung(String referenz, String betrag) {
        return """
                {"entry_reference":"%s","status":"BOOK","credit_debit_indicator":"DBIT",
                 "transaction_amount":{"currency":"EUR","amount":"%s"},
                 "booking_date":"2026-08-03","remittance_information":["Test"]}
                """.formatted(referenz, betrag);
    }
}
