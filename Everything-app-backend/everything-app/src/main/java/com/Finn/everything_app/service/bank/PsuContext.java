package com.Finn.everything_app.service.bank;

/**
 * Angaben zum Nutzer, der einen Abruf gerade selbst ausgeloest hat.
 *
 * <p>Diese Angaben entscheiden ueber das Abruflimit: ohne sie gilt ein Abruf als unbeaufsichtigt
 * und faellt unter die Beschraenkung aus Art. 36(5) der SCA-RTS - bei den meisten Instituten vier
 * Abrufe pro Tag und Konto, danach lehnt die Bank ab. Mit ihnen gilt er als vom Nutzer angefordert
 * und ist unbegrenzt.
 *
 * <p>Deshalb ein eigener Typ und kein {@code boolean}: die Kopfzeilen muessen <em>vollstaendig</em>
 * oder gar nicht mitgeschickt werden, ein unvollstaendiger Satz wird zurueckgewiesen.
 * {@code null} als Wert bedeutet "unbeaufsichtigt" - das ist der naechtliche Job.
 */
public record PsuContext(String ipAddress, String userAgent, String language) {

    /** Ohne IP ist der Satz unvollstaendig; dann lieber gar keine Kopfzeilen senden. */
    public boolean isComplete() {
        return ipAddress != null && !ipAddress.isBlank()
                && userAgent != null && !userAgent.isBlank();
    }
}
