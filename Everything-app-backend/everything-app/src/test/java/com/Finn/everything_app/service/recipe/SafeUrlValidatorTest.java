package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Sicherheitsgrenze des Rezept-Imports.
 *
 * <p>Seit der Import jede Adresse lesen soll, gibt es keine Host-Liste mehr, hinter der man sich
 * verstecken kann - was hier durchrutscht, ruft der Server tatsaechlich ab. Deshalb ist das der
 * ausfuehrlichste Test im Rezept-Bereich.
 */
class SafeUrlValidatorTest {

    private final SafeUrlValidator validator = TestHosts.allPublic();

    // ── Form ──────────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "gopher://example.com/x",
            "ftp://example.com/x",
            "jar:http://example.com/a.jar!/b",
            "javascript:alert(1)",
            "kein url",
            "http:///rezept",
    })
    void keineWebAdresse(String url) {
        assertThrows(BadRequestException.class, () -> validator.parse(url), url);
    }

    @Test
    void leereEingabeIstEinKlarerFehler() {
        assertThrows(BadRequestException.class, () -> validator.parse(null));
        assertThrows(BadRequestException.class, () -> validator.parse("   "));
    }

    // "https://chefkoch.de@127.0.0.1/" sieht aus wie chefkoch und ist es nicht: alles vor dem @
    // ist Schmuck, verbunden wird mit dem, was dahinter steht.
    @Test
    void adressenMitBenutzerteilWerdenAbgelehnt() {
        assertThrows(BadRequestException.class,
                () -> validator.parse("https://chefkoch.de@127.0.0.1/rezept"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com:22/",
            "http://example.com:6379/",
            "http://example.com:5432/",
            "http://example.com:8080/",
    })
    void krummePortsWerdenAbgelehnt(String url) {
        assertThrows(BadRequestException.class, () -> validator.parse(url), url);
    }

    @Test
    void vereinheitlichtHostUndBehaeltDieAdresseSonstBei() {
        URI uri = validator.parse("HTTPS://WWW.Chefkoch.DE./rezepte/1/x.html?a=b%26c");

        assertEquals("www.chefkoch.de", uri.getHost());
        assertEquals("/rezepte/1/x.html", uri.getPath());
        // Der kodierte Trenner darf nicht zu einem echten werden - sonst kommt eine andere
        // Seite zurueck als die, die geprueft wurde.
        assertEquals("a=b%26c", uri.getRawQuery());
    }

    @Test
    void punycodeUndUmlautNameSindDieselbeAdresse() {
        assertEquals(validator.parse("https://bücher.example/x").getHost(),
                validator.parse("https://xn--bcher-kva.example/x").getHost());
    }

    // ── Adressen ──────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "10.0.0.5",
            "192.168.1.1",
            "172.16.0.1",
            "0.0.0.0",
            "100.64.0.1",          // Carrier-NAT
            "198.51.100.7",        // Dokumentationsbereich
            "240.0.0.1",
            "255.255.255.255",
            "224.0.0.1",           // Multicast
    })
    void interneIpv4AdressenKommenNichtDurch(String ip) {
        assertThrows(BadRequestException.class,
                () -> assertPublic("http://" + ip + "/x"), ip);
    }

    // Die Metadaten-Schnittstelle jeder Cloud - das lohnendste Ziel ueberhaupt.
    @Test
    void dieMetadatenSchnittstelleIstGesperrt() {
        assertThrows(BadRequestException.class,
                () -> assertPublic("http://169.254.169.254/latest/meta-data/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[::1]",
            "[fd00::1]",           // eindeutig lokal, fc00::/7
            "[fe80::1]",           // Link-lokal
            "[::]",
            "[::ffff:127.0.0.1]",  // IPv4 in IPv6-Kleidung
            "[::ffff:10.0.0.5]",
            "[::127.0.0.1]",       // die veraltete kompatible Form
    })
    void interneIpv6AdressenKommenNichtDurch(String ip) {
        assertThrows(BadRequestException.class,
                () -> assertPublic("http://" + ip + "/x"), ip);
    }

    // 2130706433 ist 127.0.0.1 als eine Zahl geschrieben - eine Schreibweise, die Java kennt
    // und die eine Textpruefung auf "127." glatt uebersieht.
    @ParameterizedTest
    @ValueSource(strings = {"2130706433", "0177.0.0.1", "127.1"})
    void krummeSchreibweisenVonLocalhostKommenNichtDurch(String host) {
        assertThrows(BadRequestException.class,
                () -> assertPublic("http://" + host + "/x"), host);
    }

    @Test
    void localhostAlsNameKommtNichtDurch() {
        SafeUrlValidator local = TestHosts.resolving(Map.of("localhost", "127.0.0.1"));
        assertThrows(BadRequestException.class, () -> local.assertPublicHost("localhost"));
    }

    // Der Fall, den es ohne einsetzbare Aufloesung gar nicht zu pruefen gaebe: ein Name, der
    // oeffentlich klingt und intern zeigt.
    @Test
    void einOeffentlicherNameAufLocalhostKommtNichtDurch() {
        SafeUrlValidator rebind = TestHosts.resolving(Map.of("rezepte.example", "127.0.0.1"));

        assertThrows(BadRequestException.class,
                () -> rebind.assertPublicHost("rezepte.example"));
    }

    // getAllByName gibt eine Menge zurueck, und welche davon beim Verbinden genommen wird,
    // entscheidet nicht der Validator. Ein einziger interner Eintrag reicht deshalb.
    @Test
    void einEinzigerInternerEintragReichtZurAblehnung() {
        SafeUrlValidator mixed =
                TestHosts.resolvingAll("zwei.example", TestHosts.PUBLIC_IP, "10.1.2.3");

        assertThrows(BadRequestException.class, () -> mixed.assertPublicHost("zwei.example"));
    }

    @Test
    void unbekannteNamenVerratenNichtDassSieUnbekanntSind() {
        SafeUrlValidator empty = TestHosts.resolving(Map.of());

        BadRequestException unknown = assertThrows(BadRequestException.class,
                () -> empty.assertPublicHost("gibtsnicht.example"));
        BadRequestException internal = assertThrows(BadRequestException.class,
                () -> TestHosts.resolving(Map.of("intern.example", "10.0.0.9"))
                        .assertPublicHost("intern.example"));

        // Dieselbe Meldung - sonst ist der Import ein Portscanner mit deutscher Oberflaeche.
        assertEquals(unknown.getMessage(), internal.getMessage());
    }

    @Test
    void oeffentlicheAdressenGehenDurch() {
        assertDoesNotThrow(() -> assertPublic("https://www.chefkoch.de/rezepte/1/x.html"));
        assertDoesNotThrow(() -> assertPublic("https://cooking.nytimes.com/recipes/1"));
        assertDoesNotThrow(() -> assertPublic("http://93.184.216.34/x"));
    }

    private void assertPublic(String url) {
        URI uri = validator.parse(url);
        validator.assertPublicHost(uri.getHost());
    }
}
