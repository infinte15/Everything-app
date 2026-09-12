package com.Finn.everything_app.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Messfühler für die {@code SCHED}-Logzeile des Schedulers.
 *
 * <p><b>Warum über das Log und nicht über das Ergebnis.</b> {@link ScheduleResult} gibt nur
 * {@code solverStatus} heraus; Zielwert, Phasenzeiten, Intervallzahl und Drop-Kosten liegen auf
 * dem internen {@link SolveOutcome}, das den Service nie verlässt. Genau diese Zahlen loggt
 * {@code SmartSchedulerService.logRunMetrics} aber ohnehin schon einzeilig als
 * {@code schlüssel=wert}. Der Fühler hängt sich also an etwas an, das es bereits gibt — der
 * Produktionscode musste dafür nicht angefasst werden, und es entsteht keine zweite Wahrheit
 * darüber, was ein Lauf gekostet hat.
 *
 * <p>Verwendung im Test:
 * <pre>{@code
 * try (SchedLog log = SchedLog.anhaengen()) {
 *     service.generateOptimalSchedule(1L, von, bis);
 *     assertEquals("OPTIMAL", log.letzter().status());
 * }
 * }</pre>
 */
final class SchedLog implements AutoCloseable {

    /** Ein Lauf, so wie er in der SCHED-Zeile steht. */
    record Lauf(Map<String, String> felder) {

        long   totalMs()    { return zahl("totalMs"); }
        long   collectMs()  { return zahl("collectMs"); }
        long   solveMs()    { return zahl("solveMs"); }
        long   p1Ms()       { return zahl("p1Ms"); }
        long   p2Ms()       { return zahl("p2Ms"); }
        long   persistMs()  { return zahl("persistMs"); }
        int    intervalle() { return (int) zahl("intervalle"); }
        int    tagIntervalle() { return (int) zahl("tagIv"); }
        int    placeables() { return (int) zahl("placeables"); }
        int    taskChunksImModell() { return (int) zahl("taskChunks"); }
        long   drop()       { return zahl("drop"); }
        long   greedyDrop() { return zahl("greedyDrop"); }
        boolean greedy()    { return Boolean.parseBoolean(felder.getOrDefault("greedy", "false")); }
        long   obj()        { return zahl("obj"); }
        String status()     { return felder.getOrDefault("status", "?"); }
        String p1Status()   { return felder.getOrDefault("p1Status", "?"); }
        boolean p2Retry()   { return Boolean.parseBoolean(felder.getOrDefault("p2Retry", "false")); }
        int    overdue()    { return (int) zahl("overdue"); }
        int    verdraengt() { return (int) zahl("verdraengt"); }
        int    atRisk()     { return (int) zahl("atRisk"); }

        /** {@code bloecke=3+2} — Aufgabenblöcke plus alles Wiederkehrende. */
        int taskBloecke()  { return teil("bloecke", 0); }
        int restBloecke()  { return teil("bloecke", 1); }
        int bloecke()      { return taskBloecke() + restBloecke(); }

        /** {@code relief=1+0} — CATCH_UP plus SQUEEZE. */
        int reliefCatchUp() { return teil("relief", 0); }
        int reliefSqueeze() { return teil("relief", 1); }

        private long zahl(String schluessel) {
            String wert = felder.get(schluessel);
            if (wert == null) throw new AssertionError(
                    "SCHED-Zeile hat kein Feld '" + schluessel + "': " + felder);
            return Long.parseLong(wert);
        }

        private int teil(String schluessel, int index) {
            String wert = felder.get(schluessel);
            if (wert == null) throw new AssertionError(
                    "SCHED-Zeile hat kein Feld '" + schluessel + "': " + felder);
            String[] stuecke = wert.split("\\+");
            return Integer.parseInt(stuecke[index]);
        }

        @Override
        public String toString() { return felder.toString(); }
    }

    private final Logger logger = (Logger) LoggerFactory.getLogger(SmartSchedulerService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private SchedLog() {
        appender.start();
        logger.addAppender(appender);
    }

    static SchedLog anhaengen() {
        return new SchedLog();
    }

    /** Alle bisher gesehenen Läufe, in der Reihenfolge, in der sie gelaufen sind. */
    List<Lauf> laeufe() {
        List<Lauf> out = new ArrayList<>();
        // Kopie, weil der Appender waehrend der Iteration weiterschreiben kann.
        for (ILoggingEvent e : new ArrayList<>(appender.list)) {
            String zeile = e.getFormattedMessage();
            if (zeile == null || !zeile.startsWith("SCHED ")) continue;
            out.add(new Lauf(zerlege(zeile.substring("SCHED ".length()))));
        }
        return out;
    }

    /**
     * Der zuletzt gelaufene Lauf.
     *
     * <p>Wirft, wenn es keinen gibt — ein Test, der hier ins Leere greift, hat entweder den
     * Scheduler gar nicht aufgerufen oder der Lauf ist vor {@code logRunMetrics} abgebrochen.
     * Beides ist ein Testfehler und darf nicht als {@code null} weiterlaufen.
     */
    Lauf letzter() {
        List<Lauf> alle = laeufe();
        if (alle.isEmpty()) throw new AssertionError(
                "keine SCHED-Zeile aufgezeichnet — lief generateOptimalSchedule überhaupt?");
        return alle.get(alle.size() - 1);
    }

    void leeren() {
        appender.list.clear();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static Map<String, String> zerlege(String rest) {
        Map<String, String> felder = new LinkedHashMap<>();
        for (String token : rest.trim().split("\\s+")) {
            int gleich = token.indexOf('=');
            if (gleich > 0) felder.put(token.substring(0, gleich), token.substring(gleich + 1));
        }
        return felder;
    }
}
