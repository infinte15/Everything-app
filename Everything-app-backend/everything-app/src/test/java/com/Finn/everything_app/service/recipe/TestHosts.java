package com.Finn.everything_app.service.recipe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Namensaufloesung fuer Tests - ohne Netz.
 *
 * <p>Kein Test darf echtes DNS befragen: das macht die Suite langsam, unzuverlaessig und
 * offline unbrauchbar. Und ausgerechnet der interessanteste Fall - ein oeffentlich klingender
 * Name, der auf {@code 127.0.0.1} zeigt - laesst sich anders gar nicht pruefen.
 *
 * <p>Namen, die hier nicht stehen, fliegen mit {@link UnknownHostException} auf. Das ist
 * Absicht: ein vergessener Eintrag soll laut scheitern und nicht still ins Netz greifen.
 */
final class TestHosts {

    private TestHosts() {}

    /** Irgendeine oeffentliche Adresse - example.com. */
    static final String PUBLIC_IP = "93.184.216.34";

    /** Alles Namentliche zeigt ins offene Netz. Fuer Tests, die nicht ueber DNS gehen. */
    static SafeUrlValidator allPublic() {
        return new SafeUrlValidator(host -> InetAddress.getAllByName(
                looksLikeLiteral(host) ? host : PUBLIC_IP));
    }

    /** Namen gezielt auf Adressen legen - fuer die Faelle, um die es geht. */
    static SafeUrlValidator resolving(Map<String, String> names) {
        return new SafeUrlValidator(host -> {
            String mapped = names.get(host);
            if (mapped != null) {
                return InetAddress.getAllByName(mapped);
            }
            if (looksLikeLiteral(host)) {
                return InetAddress.getAllByName(host);
            }
            throw new UnknownHostException(host);
        });
    }

    /** Ein Name, der auf mehrere Adressen zeigt. */
    static SafeUrlValidator resolvingAll(String name, String... ips) {
        return new SafeUrlValidator(host -> {
            if (!host.equals(name)) {
                throw new UnknownHostException(host);
            }
            InetAddress[] addresses = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) {
                addresses[i] = InetAddress.getByName(ips[i]);
            }
            return addresses;
        });
    }

    /**
     * Zahlen und Doppelpunkte statt Buchstaben - dann fragt {@code getAllByName} kein DNS.
     *
     * <p>Auch die krummen Schreibweisen ({@code 2130706433}, {@code 0177.0.0.1}) fallen
     * hierunter; ob die JVM sie annimmt oder ablehnt, ist genau das, was geprueft werden soll.
     */
    private static boolean looksLikeLiteral(String host) {
        return host.matches("[0-9.]+") || host.contains(":")
                || (host.startsWith("[") && host.endsWith("]"));
    }
}
