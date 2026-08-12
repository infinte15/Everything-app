package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Entscheidet, ob der Server eine vom Nutzer genannte Adresse abrufen darf.
 *
 * <p><b>Das hier ist die Sicherheitsgrenze des Rezept-Imports.</b> Vorher stand an dieser Stelle
 * eine Liste mit genau einem Host ({@code chefkoch.de}); seit der Import jede Rezeptseite lesen
 * soll, gibt es keinen Namen mehr, an dem man sich festhalten koennte. Ein angemeldeter Nutzer,
 * der dem Server eine beliebige Adresse zum Abrufen gibt, laesst ihn sonst in jedes Netz greifen,
 * das der Server erreicht - {@code localhost:8080}, die Datenbank, alles im selben Subnetz.
 *
 * <p>Geprueft wird deshalb nicht der <em>Name</em>, sondern die <em>Adresse dahinter</em>: der
 * Host wird aufgeloest, und jede zurueckgegebene IP muss im offenen Netz liegen.
 *
 * <p><b>Was offen bleibt.</b> Pruefen und Verbinden sind zwei getrennte Namensaufloesungen. Wer
 * einen eigenen Nameserver betreibt, kann der Pruefung eine oeffentliche Adresse und dem
 * anschliessenden Verbinden {@code 127.0.0.1} antworten (DNS Rebinding). Dagegen hilft nur, auf
 * die geprueft IP zu verbinden und den Namen als {@code Host}-Kopfzeile mitzugeben - was
 * {@code java.net.http.HttpClient} nicht kann und einen eigenen HTTP-Unterbau samt selbst
 * gebauter TLS-Namenspruefung braeuchte. Fuer eine App mit einem Nutzer ist das
 * unverhaeltnismaessig: der Angreifer muesste erst ein Konto haben. In der Praxis abgemildert
 * wird es durch den DNS-Zwischenspeicher der JVM selbst - {@code networkaddress.cache.ttl} steht
 * ohne Sicherheitsmanager auf 30 Sekunden, und zwischen Pruefen und Verbinden liegen
 * Millisekunden, sodass fast immer die geprueft Antwort auch die verwendete ist. Die
 * eigentliche Loesung ist eine Ausgangsregel auf Netzebene, nicht Java-Code.
 *
 * <p>Zweite offene Stelle: {@link InetAddress#getAllByName(String)} kennt kein Zeitlimit. Ein
 * boesartiger Nameserver kann den Anfrage-Thread bis zum Zeitlimit des Systems aufhalten. Das
 * laesst sich nicht wegkapseln - ein {@code CompletableFuture} mit Frist gibt nur frueher auf,
 * der Thread haengt weiter. Begrenzt wird es in der Praxis durch das Zeitlimit der App.
 */
@Component
@Slf4j
public class SafeUrlValidator {

    /**
     * Namensaufloesung als einsetzbares Stueck - sonst braeuchte jeder Test echtes DNS.
     *
     * <p>Nur so ist ausgerechnet der interessanteste Fall pruefbar: ein oeffentlich klingender
     * Name, der auf {@code 127.0.0.1} zeigt.
     */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /** Eine Meldung fuer alles Interne. Zwei waeren ein Portscanner mit deutscher Oberflaeche. */
    static final String NOT_PUBLIC = "Diese Adresse führt nicht ins offene Netz.";

    private static final String NO_URL = "Das ist keine Web-Adresse.";

    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443);

    private final HostResolver resolver;

    public SafeUrlValidator() {
        this(InetAddress::getAllByName);
    }

    /** Fuer Tests: eine Aufloesung, die nicht ins Netz geht. */
    SafeUrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Form der Adresse pruefen und vereinheitlichen - ohne Namensaufloesung, also billig.
     *
     * <p>Die URI wird aus den geprueften Teilen neu gebaut, damit Pruefung und Abruf hinterher
     * ueber dieselbe Adresse reden. Wo Validator und HTTP-Client eine Adresse verschieden lesen,
     * entstehen genau die Luecken, die diese Klasse schliessen soll.
     */
    public URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BadRequestException(NO_URL);
        }

        URI uri;
        try {
            uri = new URI(toAscii(rawUrl.trim()));
        } catch (URISyntaxException e) {
            throw new BadRequestException(NO_URL);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException(NO_URL);
        }

        // "https://chefkoch.de@127.0.0.1/" - alles vor dem @ ist Schmuck, der echte Host steht
        // dahinter. Wer solche Adressen einfuegt, meint nichts Gutes.
        if (uri.getUserInfo() != null) {
            throw new BadRequestException(NO_URL);
        }

        // null bei "http:///rezept" und bei Autoritaeten mit Zeichen, die die Registry-Syntax
        // nicht kennt (etwa "_"). Ablehnen schliesst die ganze Familie von Faellen, in denen
        // URI und HTTP-Client verschieden lesen.
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException(NO_URL);
        }

        if (!ALLOWED_PORTS.contains(uri.getPort())) {
            // Kostet die verschwindend seltene Rezeptseite auf :8443 und nimmt dafuer die ganze
            // Familie "interner Dienst auf krummem Port hinter oeffentlichem Namen".
            throw new BadRequestException(NOT_PUBLIC);
        }

        String host = normalizeHost(uri.getHost());
        rejectOddNumericHost(host);

        // Aus den *rohen* Teilen zusammensetzen, nicht aus den dekodierten: der mehrteilige
        // URI-Konstruktor kodiert neu, und aus einem kodierten "%26" im Abfrageteil wuerde
        // dabei ein trennendes "&". Der Fragmentteil faellt weg - der wird ohnehin nie
        // gesendet.
        StringBuilder rebuilt = new StringBuilder(scheme).append("://").append(host);
        if (uri.getPort() != -1) {
            rebuilt.append(':').append(uri.getPort());
        }
        String rawPath = uri.getRawPath();
        rebuilt.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
        if (uri.getRawQuery() != null) {
            rebuilt.append('?').append(uri.getRawQuery());
        }

        try {
            return new URI(rebuilt.toString());
        } catch (URISyntaxException e) {
            throw new BadRequestException(NO_URL);
        }
    }

    /**
     * Umlaute im Hostnamen nach Punycode - <em>bevor</em> {@link URI} die Adresse liest.
     *
     * <p>{@code new URI("https://bücher.example/x").getHost()} gibt {@code null}: der Konstruktor
     * kennt nur die ASCII-Registry-Syntax und legt den Rest unbesehen in {@code authority} ab.
     * Ohne diesen Schritt waere jede Adresse mit Umlaut "keine Web-Adresse" - und die gibt es.
     */
    private String toAscii(String rawUrl) {
        int schemeEnd = rawUrl.indexOf("://");
        if (schemeEnd < 0) {
            return rawUrl;
        }
        int authorityStart = schemeEnd + 3;
        int authorityEnd = rawUrl.length();
        for (int i = authorityStart; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }

        String authority = rawUrl.substring(authorityStart, authorityEnd);
        if (authority.chars().allMatch(c -> c < 128)) {
            return rawUrl;
        }

        // Benutzerteil und Port bleiben, wie sie sind - umgeschrieben wird nur der Name. Ein
        // Benutzerteil fliegt gleich danach ohnehin auf.
        String prefix = "";
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            prefix = authority.substring(0, at + 1);
            authority = authority.substring(at + 1);
        }
        String suffix = "";
        int colon = authority.lastIndexOf(':');
        if (colon >= 0 && authority.indexOf(']') < colon) {
            suffix = authority.substring(colon);
            authority = authority.substring(0, colon);
        }

        try {
            authority = IDN.toASCII(authority);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(NO_URL);
        }

        return rawUrl.substring(0, authorityStart) + prefix + authority + suffix
                + rawUrl.substring(authorityEnd);
    }

    /**
     * Zahlenhosts muessen die uebliche Schreibweise haben.
     *
     * <p>{@code 2130706433}, {@code 127.1} und {@code 0177.0.0.1} sind alle drei
     * {@code 127.0.0.1} - in Schreibweisen, die jeder Textvergleich auf "127." uebersieht und
     * die verschiedene Programme verschieden lesen ({@code 0177} ist mal oktal 127, mal dezimal
     * 177). Wer eine Rezeptseite einfuegt, schreibt keine davon.
     */
    private void rejectOddNumericHost(String host) {
        if (!host.matches("[0-9.]+")) {
            return;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw new BadRequestException(NOT_PUBLIC);
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3
                    || (part.length() > 1 && part.charAt(0) == '0')
                    || Integer.parseInt(part) > 255) {
                throw new BadRequestException(NOT_PUBLIC);
            }
        }
    }

    /**
     * Kleinschreibung, Punycode und der abschliessende Punkt.
     *
     * <p>{@code chefkoch.de.} loest genauso auf wie {@code chefkoch.de}, vergleicht sich aber
     * anders - und {@code IDN.toASCII} macht aus verschiedenen Schreibweisen desselben Namens
     * eine.
     */
    private String normalizeHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        try {
            return IDN.toASCII(host);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(NO_URL);
        }
    }

    /**
     * Loest den Host auf und besteht darauf, dass <b>jede</b> Antwort im offenen Netz liegt.
     *
     * <p>Nicht nur die erste: {@code getAllByName} gibt eine Menge zurueck, und welche davon
     * beim Verbinden genommen wird, entscheidet nicht diese Klasse. Ein Name mit einem
     * oeffentlichen und einem {@code 10.x}-Eintrag ist deshalb abzulehnen.
     */
    public void assertPublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            // Bewusst dieselbe Meldung wie bei internen Adressen: sonst verraet der Server,
            // welche Namen es intern gibt.
            throw new BadRequestException(NOT_PUBLIC);
        }

        if (addresses == null || addresses.length == 0) {
            throw new BadRequestException(NOT_PUBLIC);
        }

        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                log.warn("Rezept-Import abgelehnt: {} zeigt auf {}", host, address.getHostAddress());
                throw new BadRequestException(NOT_PUBLIC);
            }
        }
    }

    static boolean isPublic(InetAddress address) {
        InetAddress candidate = unwrapIpv4Mapped(address);

        if (candidate.isAnyLocalAddress()          // 0.0.0.0, ::
                || candidate.isLoopbackAddress()   // 127/8, ::1
                || candidate.isLinkLocalAddress()  // 169.254/16 - die Metadaten-Schnittstelle der Cloud
                || candidate.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || candidate.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = candidate.getAddress();

        if (candidate instanceof Inet6Address) {
            // fc00::/7 - eindeutig lokal, und von isSiteLocalAddress() nicht erfasst.
            return (bytes[0] & 0xFE) != 0xFC;
        }

        int a = bytes[0] & 0xFF;
        int b = bytes[1] & 0xFF;
        int c = bytes[2] & 0xFF;

        if (a == 100 && b >= 64 && b <= 127) return false;              // 100.64/10, Carrier-NAT
        if (a == 192 && b == 0 && c == 0) return false;                 // 192.0.0/24, IETF-Protokolle
        if (a == 192 && b == 0 && c == 2) return false;                 // 192.0.2/24, Dokumentation
        if (a == 198 && b == 51 && c == 100) return false;              // 198.51.100/24
        if (a == 203 && b == 0 && c == 113) return false;               // 203.0.113/24
        if (a == 198 && (b == 18 || b == 19)) return false;             // 198.18/15, Messaufbauten
        if (a >= 240) return false;                                     // 240/4 und 255.255.255.255

        return true;
    }

    /**
     * {@code ::ffff:127.0.0.1} ist eine IPv4-Adresse in IPv6-Kleidung.
     *
     * <p>Java packt sie mal aus und mal nicht - {@code isLoopbackAddress()} auf der verpackten
     * Form ist nicht verlaesslich. Also von Hand auspacken und die vier Bytes pruefen.
     *
     * <p><b>Nicht ausgepackt werden {@code ::} und {@code ::1}.</b> Rein von den Bytes her sehen
     * sie aus wie die (veraltete) IPv4-kompatible Form {@code ::a.b.c.d}, und wer sie auspackt,
     * macht aus {@code ::1} die Adresse {@code 0.0.0.1} - die keine der
     * {@code isXxxAddress()}-Pruefungen mehr erwischt. Genau so waere Localhost ueber IPv6 durch
     * die Pruefung gerutscht. Deshalb: nur auspacken, wenn danach ein erstes Byte ungleich null
     * steht.
     */
    private static InetAddress unwrapIpv4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address ipv6)) {
            return address;
        }
        byte[] bytes = ipv6.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return address;
            }
        }
        boolean ffff = (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
        boolean compat = bytes[10] == 0 && bytes[11] == 0 && bytes[12] != 0;
        if (!ffff && !compat) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (UnknownHostException e) {
            // Vier Bytes sind immer eine gueltige IPv4-Adresse; kommt nicht vor.
            return address;
        }
    }
}
