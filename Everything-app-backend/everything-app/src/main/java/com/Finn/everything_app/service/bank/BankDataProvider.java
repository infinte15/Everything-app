package com.Finn.everything_app.service.bank;

import java.time.LocalDate;
import java.util.List;

/**
 * Zugriff auf Kontodaten einer Bank - die einzige Stelle, an der ein konkreter Anbieter sichtbar ist.
 *
 * <p>Die Typen in dieser Schnittstelle sind eigene Records und nicht die JSON-Klassen von Enable
 * Banking. Dieselbe Trennung wie zwischen {@code ScheduleInput}/{@code ScheduledItem} und dem
 * CP-SAT-Solver: was der Rest des Systems sieht, soll nicht davon abhaengen, wie ein Anbieter
 * seine Felder nennt.
 *
 * <p>Zwei Implementierungen, ausgewaehlt ueber {@code enablebanking.provider}:
 * {@link EnableBankingClient} (live) und {@link DemoBankDataProvider} (demo, Standard).
 */
public interface BankDataProvider {

    /** Sprechender Name fuer Logs und die Kennzeichnung in der Oberflaeche. */
    String providerName();

    /** Alle Institute eines Landes. Bei Sparkassen sind das mehrere hundert regionale Eintraege. */
    List<AspspInfo> listAspsps(String country);

    /** Startet die Zustimmung und liefert die URL, auf die der Browser des Nutzers geschickt wird. */
    AuthStart startAuth(AuthRequest request);

    /** Loest den Code aus dem Callback in eine Sitzung samt Kontoliste ein. */
    SessionResult redeemSession(String code);

    /**
     * Salden eines Kontos. Mehrere Eintraege sind der Normalfall - welcher der "aktuelle" ist,
     * entscheidet {@link BankBalance#preferenceRank()}.
     */
    List<BankBalance> fetchBalances(String accountUid, PsuContext psu);

    /**
     * Umsaetze ab {@code from}.
     *
     * @param deepBackfill beim Erst-Import {@code true}: holt die laengste verfuegbare Historie
     *                     statt eines festen Zeitraums. Das ist nur unmittelbar nach der
     *                     Autorisierung sinnvoll - danach klemmen die meisten Banken auf 90 Tage.
     * @param psu          {@code null} beim naechtlichen Job (unbeaufsichtigt, faellt unter das
     *                     Abruflimit), gesetzt bei einer vom Nutzer ausgeloesten Aktualisierung.
     */
    List<BankTx> fetchTransactions(String accountUid, LocalDate from, boolean deepBackfill, PsuContext psu);
}
