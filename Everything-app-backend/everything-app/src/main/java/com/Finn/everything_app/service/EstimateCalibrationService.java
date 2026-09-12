package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lernt, um welchen Faktor ein Nutzer seine Aufgaben zu knapp schätzt — die Ist-Zeit-Rückkopplung.
 *
 * <p><b>Das Problem.</b> Die Schätzung einer Aufgabe war bisher eine Einbahnstraße. Braucht eine
 * Aufgabe länger als geschätzt, hakt der Nutzer ihre Blöcke ab, die Minuten laufen in
 * {@code completedMinutes}, und sobald die Schätzung aufgebraucht ist, gilt die Aufgabe als fertig
 * (siehe {@code CalendarEventService.creditBlock}). Er trägt dann eine neue, größere Schätzung nach.
 * Aus dieser Korrektur hat niemand etwas gelernt: die NÄCHSTE Aufgabe wurde genauso knapp geschätzt,
 * und der Plan drumherum war wieder falsch. Genau das ruiniert einen Auto-Plan — nicht eine falsch
 * geschätzte Aufgabe, sondern die zwanzig Blöcke, die um sie herum an der falschen Stelle liegen.
 *
 * <p><b>Das Signal.</b> {@link Task#getOriginalEstimateMinutes()} hält die zuerst eingetragene
 * Schätzung fest, {@code completedMinutes} und die geltende Schätzung sagen, was es am Ende wurde.
 * Das Verhältnis der beiden über die letzten abgeschlossenen Aufgaben ist der gesuchte Faktor.
 *
 * <p><b>Warum der MEDIAN und nicht der Mittelwert.</b> Eine einzelne Aufgabe, die statt einer
 * Stunde zehn gebraucht hat (Urlaub dazwischen, Aufgabe neu zugeschnitten, Schätzung verrutscht),
 * zieht einen Mittelwert über jede Grenze. Der Median ignoriert sie. Dieselbe Überlegung wie beim
 * p50 in {@code SmartSchedulerLastTest}: gemessen wird die Verteilung, nicht der Ausschlag.
 *
 * <p><b>Und warum er geklammert ist.</b> Der Faktor multipliziert die RESTDAUER jeder offenen
 * Aufgabe, also die Größe, aus der der Löser sein Modell baut. Nach oben offen würde er bei einem
 * Nutzer, der grundsätzlich um das Dreifache daneben liegt, jeden Tag dreifach füllen und damit
 * mehr Aufgaben als gefährdet melden, als er vorher unterbrachte — aus einer Hilfe würde eine
 * Warnlawine. Nach unten gar nicht: dass jemand zu GROSSZÜGIG schätzt, ist kein Problem, das ein
 * Planer lösen muss, und ein Faktor unter 1 würde Blöcke kürzen, die der Nutzer selbst gesetzt hat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EstimateCalibrationService {

    private final TaskRepository taskRepository;

    /** Ohne Korrektur — der Wert, der nichts verändert. */
    public static final double NEUTRAL = 1.0;

    /**
     * So viele abgeschlossene Aufgaben braucht es mindestens, bevor überhaupt gelernt wird.
     *
     * <p>Unter fünf Stichproben ist der Median eine Anekdote. Und der Preis für ein zu frühes Urteil
     * ist hoch: der Faktor wirkt auf JEDE offene Aufgabe, also würde eine einzige schiefgelaufene
     * Aufgabe den ganzen Kalender aufblähen.
     */
    static final int MIN_STICHPROBEN = 5;

    /** Nur die jüngsten Aufgaben zählen — wer besser schätzen lernt, soll das auch merken. */
    static final int FENSTER = 20;

    /** Obergrenze des Faktors; Herleitung siehe Klassen-Javadoc. */
    static final double MAX_FAKTOR = 2.0;

    /**
     * Der Korrekturfaktor für diesen Nutzer, immer in {@code [1.0, MAX_FAKTOR]}.
     *
     * <p>Wird vom Scheduler EINMAL pro Lauf geholt und dann an die Zerlegung durchgereicht: eine
     * Abfrage je Lauf, nicht eine je Aufgabe.
     */
    public double faktorFuer(Long userId) {
        List<Double> quotienten = new ArrayList<>();

        List<Task> fertig = taskRepository.findByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        fertig.sort(Comparator.comparing(Task::getCompletedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed());

        for (Task t : fertig) {
            if (quotienten.size() >= FENSTER) break;
            Integer ursprung = t.getOriginalEstimateMinutes();
            if (ursprung == null || ursprung <= 0) continue;   // Bestandszeile ohne Bezugspunkt

            // Das Ist ist das Maximum aus abgehakter Zeit und geltender Schätzung: hat der Nutzer
            // die Schätzung nachgezogen, aber nicht jeden Block abgehakt, ist die korrigierte
            // Schätzung das ehrlichere Maß — und umgekehrt.
            int ist = Math.max(nz(t.getCompletedMinutes()), nz(t.getEstimatedDurationMinutes()));
            if (ist <= 0) continue;
            quotienten.add(ist / (double) ursprung);
        }

        if (quotienten.size() < MIN_STICHPROBEN) return NEUTRAL;

        double median = median(quotienten);
        double faktor = Math.min(MAX_FAKTOR, Math.max(NEUTRAL, median));
        if (faktor > NEUTRAL) {
            log.debug("Schätzkorrektur für User {}: Faktor {} aus {} Aufgaben (Median {})",
                    userId, String.format("%.2f", faktor), quotienten.size(),
                    String.format("%.2f", median));
        }
        return faktor;
    }

    private static double median(List<Double> werte) {
        List<Double> sortiert = werte.stream().sorted().toList();
        int n = sortiert.size();
        return n % 2 == 1
                ? sortiert.get(n / 2)
                : (sortiert.get(n / 2 - 1) + sortiert.get(n / 2)) / 2.0;
    }

    private static int nz(Integer wert) {
        return wert != null ? wert : 0;
    }
}
