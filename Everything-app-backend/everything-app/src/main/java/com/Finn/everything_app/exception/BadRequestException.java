package com.Finn.everything_app.exception;

public class BadRequestException extends RuntimeException {

    /**
     * Kennung fuer die App, wenn sie auf einen Fehler nicht nur mit Anzeigen reagieren soll.
     *
     * <p>Normalerweise reicht die Meldung: sie ist fuer Nutzer geschrieben und wird unveraendert
     * angezeigt. Beim Instagram-Import muss die Oberflaeche aber etwas <em>tun</em> - in den
     * Text-Weg wechseln und die Adresse merken -, und dafuer auf deutschen Prosatext zu pruefen
     * waere eine Falle beim naechsten Umformulieren. Meist {@code null}.
     */
    private final String code;

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
