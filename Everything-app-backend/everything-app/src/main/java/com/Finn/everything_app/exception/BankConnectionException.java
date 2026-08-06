package com.Finn.everything_app.exception;

/**
 * Fehler beim Zugriff auf die Bank.
 *
 * <p>Existiert, damit die Rohmeldung eines Drittanbieters nicht ueber den 500er-Auffangbehandler
 * in der App landet. {@link #getErrorCode()} traegt den sprechenden Code der Schnittstelle
 * (z.B. {@code EXPIRED_SESSION}, {@code ASPSP_RATE_LIMIT_EXCEEDED}), damit der Aufrufer daran
 * verzweigen kann - der HTTP-Status taugt dafuer nicht: eine abgelaufene Zustimmung kommt als 401,
 * und 401 bedeutet auch mehrere andere Dinge.
 */
public class BankConnectionException extends RuntimeException {

    private final String errorCode;

    public BankConnectionException(String message) {
        this(message, null);
    }

    public BankConnectionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** Die Zustimmung ist weg - der Nutzer muss die Verbindung neu aufsetzen. */
    public boolean isConsentGone() {
        return "EXPIRED_SESSION".equals(errorCode)
                || "CLOSED_SESSION".equals(errorCode)
                || "REVOKED_SESSION".equals(errorCode)
                || "SESSION_DOES_NOT_EXIST".equals(errorCode);
    }

    /** Abruflimit erreicht - kein Defekt, sondern eine Vorgabe der Bank. */
    public boolean isRateLimited() {
        return "ASPSP_RATE_LIMIT_EXCEEDED".equals(errorCode);
    }
}
