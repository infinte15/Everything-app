package com.Finn.everything_app.service.bank;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen der Bankanbindung.
 *
 * <p>Das Projekt liest Konfiguration sonst durchgaengig mit {@code @Value}. Fuer diese sechs
 * zusammengehoerigen Werte ist eine eigene Klasse die passendere Form - sie werden gemeinsam
 * gebraucht, und der Provider muss ohnehin pruefen, ob im Live-Betrieb alles gesetzt ist.
 */
@ConfigurationProperties("enablebanking")
@Data
public class EnableBankingProperties {

    /**
     * {@code demo} oder {@code live}. Standard ist {@code demo}, damit ein frisch aufgesetztes
     * System ohne Zugangsdaten lauffaehig ist.
     */
    private String provider = "demo";

    /** Application-ID aus dem Control Panel; wandert als {@code kid} in den JWT-Kopf. */
    private String applicationId;

    /**
     * Pfad zum privaten Schluessel (PKCS#8-PEM). Bewusst ausserhalb des Projektverzeichnisses,
     * anders als die uebrigen Zugangsdaten in application.properties - ein Schluessel gehoert
     * nicht in die Versionsverwaltung.
     */
    private String privateKeyPath = System.getProperty("user.home") + "/.everything-app/enablebanking.pem";

    /** Muss im Control Panel als erlaubte Redirect-URL hinterlegt sein, sonst lehnt die Bank ab. */
    private String redirectUrl = "http://localhost:8080/api/finance/bank/callback";

    /**
     * Gewuenschte Gueltigkeit der Zustimmung. Wird auf das gedeckelt, was das Institut zulaesst
     * (meist 180 Tage) - eine Verlaengerung gibt es nicht, danach ist eine neue Zustimmung faellig.
     */
    private int consentDays = 180;

    /**
     * Wie weit der Erst-Import zurueckgeht. Die volle Historie liefern die meisten Institute nur
     * unmittelbar nach der Zustimmung; danach bleiben 90 Tage.
     */
    private int backfillDays = 730;

    /** Ueberlappung beim laufenden Sync, damit nachtraeglich gebuchte Umsaetze nicht durchrutschen. */
    private int syncOverlapDays = 7;

    public boolean isDemo() {
        return !"live".equalsIgnoreCase(provider);
    }
}
