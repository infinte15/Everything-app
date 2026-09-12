package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Der gelernte Schätzfaktor — die Ist-Zeit-Rückkopplung.
 *
 * <p>Geprüft wird das, was den Plan verändert: wann überhaupt gelernt wird, gegen was gemessen wird
 * und wo die Klammer greift. Der Faktor multipliziert die Restdauer JEDER offenen Aufgabe; ein
 * Fehler hier verschiebt nicht einen Block, sondern den ganzen Kalender.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schätzkalibrierung")
class EstimateCalibrationServiceTest {

    @Mock TaskRepository taskRepository;

    @InjectMocks
    EstimateCalibrationService service;

    private long naechsteId = 1L;

    /** Unter fünf Stichproben ist der Median eine Anekdote — dann wird nicht gelernt. */
    @Test
    void unterFuenfStichprobenWirdNichtGelernt() {
        fertig(4, 60, 180);   // jede Aufgabe brauchte das Dreifache

        assertEquals(EstimateCalibrationService.NEUTRAL, service.faktorFuer(1L), 0.001,
                "vier Aufgaben reichen für kein Urteil");
    }

    @Test
    void abFuenfStichprobenGiltDerMedian() {
        fertig(5, 60, 90);   // 90/60 = 1,5

        assertEquals(1.5, service.faktorFuer(1L), 0.001);
    }

    /**
     * Eine einzelne entgleiste Aufgabe verschiebt den Faktor nicht.
     *
     * <p>Genau dafür der Median statt des Mittelwerts: eine Aufgabe, die statt einer Stunde zehn
     * gebraucht hat (Urlaub dazwischen, Aufgabe neu zugeschnitten), zieht einen Mittelwert über jede
     * Grenze — und der Faktor wirkt auf ALLE offenen Aufgaben.
     */
    @Test
    void einAusreisserVerschiebtDenMedianNicht() {
        List<Task> alle = new ArrayList<>(bauen(5, 60, 66));   // 1,1
        alle.add(task(60, 3000, TaskStatus.COMPLETED));        // 50-facher Ausreißer
        when(taskRepository.findByUserIdAndStatus(eq(1L), eq(TaskStatus.COMPLETED))).thenReturn(alle);

        assertEquals(1.1, service.faktorFuer(1L), 0.001);
    }

    /** Nach oben geklammert: sonst würde eine Warnlawine aus einer Hilfe. */
    @Test
    void derFaktorIstNachObenGeklammert() {
        fertig(6, 60, 600);   // Faktor 10

        assertEquals(EstimateCalibrationService.MAX_FAKTOR, service.faktorFuer(1L), 0.001);
    }

    /**
     * Nach unten gar nicht: wer großzügig schätzt, hat kein Problem, das ein Planer lösen muss —
     * und ein Faktor unter 1 würde Blöcke kürzen, die der Nutzer selbst so gesetzt hat.
     */
    @Test
    void grosszuegigeSchaetzungenWerdenNichtNachUntenKorrigiert() {
        fertig(6, 120, 30);   // Faktor 0,25

        assertEquals(EstimateCalibrationService.NEUTRAL, service.faktorFuer(1L), 0.001);
    }

    /** Bestandszeilen ohne Ursprungswert sind keine Stichprobe — sie haben keinen Bezugspunkt. */
    @Test
    void bestandszeilenOhneUrsprungsschaetzungZaehlenNicht() {
        List<Task> alle = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Task t = task(60, 180, TaskStatus.COMPLETED);
            t.setOriginalEstimateMinutes(null);
            alle.add(t);
        }
        when(taskRepository.findByUserIdAndStatus(eq(1L), eq(TaskStatus.COMPLETED))).thenReturn(alle);

        assertEquals(EstimateCalibrationService.NEUTRAL, service.faktorFuer(1L), 0.001);
    }

    /**
     * Gemessen wird gegen das Maximum aus abgehakter Zeit und geltender Schätzung.
     *
     * <p>Hat der Nutzer die Schätzung nachgezogen, aber nicht jeden Block abgehakt, ist die
     * korrigierte Schätzung das ehrlichere Maß — und umgekehrt.
     */
    @Test
    void dasIstIstDasMaximumAusAbgehaktUndKorrigierterSchaetzung() {
        List<Task> alle = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Task t = task(60, 0, TaskStatus.COMPLETED);   // nichts abgehakt
            t.setEstimatedDurationMinutes(120);           // aber Schätzung verdoppelt
            alle.add(t);
        }
        when(taskRepository.findByUserIdAndStatus(eq(1L), eq(TaskStatus.COMPLETED))).thenReturn(alle);

        assertEquals(2.0, service.faktorFuer(1L), 0.001);
    }

    /** Nur die jüngsten Aufgaben zählen — wer besser schätzen lernt, soll das merken. */
    @Test
    void nurDieJuengstenAufgabenZaehlen() {
        List<Task> alle = new ArrayList<>();
        // 20 alte Aufgaben mit Faktor 2, danach 20 neue mit Faktor 1: nur die neuen dürfen zählen.
        for (int i = 0; i < 20; i++) {
            Task alt = task(60, 120, TaskStatus.COMPLETED);
            alt.setCompletedAt(LocalDateTime.now().minusDays(100 - i));
            alle.add(alt);
        }
        for (int i = 0; i < 20; i++) {
            Task neu = task(60, 60, TaskStatus.COMPLETED);
            neu.setCompletedAt(LocalDateTime.now().minusDays(20 - i));
            alle.add(neu);
        }
        when(taskRepository.findByUserIdAndStatus(eq(1L), eq(TaskStatus.COMPLETED))).thenReturn(alle);

        assertEquals(EstimateCalibrationService.NEUTRAL, service.faktorFuer(1L), 0.001,
                "die zwanzig alten Aufgaben mit Faktor 2 dürfen nicht mehr durchschlagen");
    }

    // ------------------------------------------------------------------

    private void fertig(int anzahl, int ursprung, int ist) {
        when(taskRepository.findByUserIdAndStatus(eq(1L), eq(TaskStatus.COMPLETED)))
                .thenReturn(bauen(anzahl, ursprung, ist));
    }

    private List<Task> bauen(int anzahl, int ursprung, int ist) {
        List<Task> out = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) out.add(task(ursprung, ist, TaskStatus.COMPLETED));
        return out;
    }

    private Task task(int ursprung, int completedMinutes, TaskStatus status) {
        Task t = new Task();
        t.setId(naechsteId++);
        t.setTitle("T" + t.getId());
        t.setOriginalEstimateMinutes(ursprung);
        t.setEstimatedDurationMinutes(ursprung);
        t.setCompletedMinutes(completedMinutes);
        t.setStatus(status);
        t.setCompletedAt(LocalDateTime.now().minusDays(naechsteId));
        return t;
    }
}
