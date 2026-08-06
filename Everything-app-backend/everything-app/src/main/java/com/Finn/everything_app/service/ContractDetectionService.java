package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.ContractRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Erkennt wiederkehrende Zahlungen und fuehrt daraus {@link Contract}s.
 *
 * <p>Laeuft nach jedem Sync. Gruppiert die Buchungen nach der normalisierten Gegenpartei
 * ({@link CounterpartyNormalizer}), misst die Abstaende zwischen aufeinanderfolgenden Buchungen und
 * erklaert eine Reihe zum Vertrag, wenn Rhythmus <em>und</em> Betrag stabil genug sind.
 *
 * <p>Bewusst konservativ: lieber ein Abo uebersehen, als eine Reihe zufaelliger Supermarktbesuche
 * zum Vertrag erklaeren und damit die Prognose verfaelschen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractDetectionService {

    /** Unter drei Buchungen gibt es nur einen einzigen Abstand - daraus laesst sich kein Rhythmus ablesen. */
    private static final int MIN_OCCURRENCES = 3;

    /** Zulaessige Streuung der Betraege um den Median. */
    private static final double AMOUNT_TOLERANCE = 0.15;

    /** Ab dem 1,5-fachen des Intervalls ohne Buchung gilt ein Vertrag als gekuendigt. */
    private static final double OVERDUE_FACTOR = 1.5;

    /** Wie weit zurueck ueberhaupt nach Reihen gesucht wird. */
    private static final int LOOKBACK_DAYS = 800;

    private final FinanceTransactionRepository transactionRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;

    /** Bekannte Rhythmen: Ober-/Untergrenze in Tagen und das zugehoerige Etikett. */
    private record Rhythm(int minDays, int maxDays, ContractFrequency frequency) {
    }

    /**
     * Die Baender sind bewusst weit.
     *
     * <p>Ein "monatlicher" Einzug trifft nie denselben Abstand: die Monate sind 28 bis 31 Tage lang,
     * Wochenenden und Feiertage schieben um bis zu drei Tage, und beides addiert sich. Zwischen dem
     * 8. Maerz und dem 10. April liegen 33 Tage - mit einem Band von 27 bis 32 waere genau das kein
     * Abo mehr. Ueberschneidungsfrei bleiben die Baender trotzdem, also bleibt die Zuordnung
     * eindeutig.
     */
    private static final List<Rhythm> RHYTHMS = List.of(
            new Rhythm(6, 9, ContractFrequency.WEEKLY),
            new Rhythm(12, 17, ContractFrequency.BIWEEKLY),
            new Rhythm(25, 35, ContractFrequency.MONTHLY),
            new Rhythm(55, 66, ContractFrequency.BIMONTHLY),
            new Rhythm(84, 98, ContractFrequency.QUARTERLY),
            new Rhythm(175, 192, ContractFrequency.SEMIANNUAL),
            new Rhythm(353, 378, ContractFrequency.YEARLY));

    @Transactional
    public void detectForUser(Long userId) {
        List<FinanceTransaction> transactions = transactionRepository
                .findByUserIdAndTransactionDateAfterOrderByTransactionDateAsc(
                        userId, LocalDate.now().minusDays(LOOKBACK_DAYS));

        if (transactions.isEmpty()) {
            return;
        }

        Map<String, List<FinanceTransaction>> groups = transactions.stream()
                .filter(tx -> !CounterpartyNormalizer.normalize(tx.getCounterparty()).isEmpty())
                .collect(Collectors.groupingBy(
                        tx -> CounterpartyNormalizer.normalize(tx.getCounterparty()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Contract> existing = contractRepository.findByUserIdOrderByNameAsc(userId);
        Set<Long> touched = new HashSet<>();
        int created = 0;

        for (Map.Entry<String, List<FinanceTransaction>> entry : groups.entrySet()) {
            for (List<FinanceTransaction> series : splitByAmount(entry.getValue())) {
                Detection detection = analyse(series);
                if (detection == null) {
                    continue;
                }
                Contract contract = upsert(userId, entry.getKey(), series, detection, existing);
                if (contract != null) {
                    touched.add(contract.getId());
                    created++;
                }
            }
        }

        markCancelled(existing, touched);
        log.debug("Vertragserkennung für User {}: {} Reihen erkannt", userId, created);
    }

    // ==================== Analyse ====================

    /**
     * Trennt Buchungen derselben Gegenpartei nach Betragsniveau.
     *
     * <p>"Amazon Prime 8,99" und "Amazon Music 10,99" sind zwei Vertraege, keiner davon mit einem
     * Median dazwischen. Ohne diese Trennung entstuende ein Fantasievertrag ueber 9,99.
     */
    private List<List<FinanceTransaction>> splitByAmount(List<FinanceTransaction> transactions) {
        List<FinanceTransaction> sorted = transactions.stream()
                .sorted(Comparator.comparingDouble(FinanceTransaction::getAmount))
                .toList();

        List<List<FinanceTransaction>> buckets = new ArrayList<>();
        List<FinanceTransaction> current = new ArrayList<>();

        for (FinanceTransaction tx : sorted) {
            if (current.isEmpty()) {
                current.add(tx);
                continue;
            }
            double reference = current.get(0).getAmount();
            if (Math.abs(tx.getAmount() - reference) <= reference * AMOUNT_TOLERANCE) {
                current.add(tx);
            } else {
                buckets.add(current);
                current = new ArrayList<>(List.of(tx));
            }
        }
        if (!current.isEmpty()) {
            buckets.add(current);
        }

        return buckets.stream()
                .filter(bucket -> bucket.size() >= MIN_OCCURRENCES)
                .map(bucket -> bucket.stream()
                        .sorted(Comparator.comparing(FinanceTransaction::getTransactionDate))
                        .collect(Collectors.toList()))
                .toList();
    }

    private record Detection(ContractFrequency frequency, int intervalDays, double amount, LocalDate lastDate) {
    }

    private Detection analyse(List<FinanceTransaction> series) {
        if (series.size() < MIN_OCCURRENCES) {
            return null;
        }
        // Eine Reihe muss in einer Richtung laufen - Gehalt und Ruecklastschrift sind nicht dasselbe.
        String type = series.get(0).getType();
        if (series.stream().anyMatch(tx -> !type.equals(tx.getType()))) {
            return null;
        }

        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < series.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(
                    series.get(i - 1).getTransactionDate(), series.get(i).getTransactionDate()));
        }
        // Zwei Buchungen am selben Tag sind eine Doppelbuchung, kein Rhythmus.
        gaps.removeIf(gap -> gap <= 0);
        if (gaps.size() < MIN_OCCURRENCES - 1) {
            return null;
        }

        long median = medianLong(gaps);
        ContractFrequency frequency = classify(median);
        if (frequency == null) {
            return null;
        }

        // Jeder einzelne Abstand muss zum erkannten Rhythmus passen; ein Ausreisser genuegt,
        // um aus zufaelligen Wiederholungen einen scheinbaren Vertrag zu machen.
        Rhythm rhythm = RHYTHMS.stream()
                .filter(r -> r.frequency() == frequency)
                .findFirst()
                .orElseThrow();
        long outliers = gaps.stream()
                .filter(gap -> gap < rhythm.minDays() || gap > rhythm.maxDays())
                .count();
        if (outliers > 0) {
            return null;
        }

        double amount = medianDouble(series.stream().map(FinanceTransaction::getAmount).toList());
        return new Detection(frequency, (int) median, round(amount),
                series.get(series.size() - 1).getTransactionDate());
    }

    private ContractFrequency classify(long days) {
        for (Rhythm rhythm : RHYTHMS) {
            if (days >= rhythm.minDays() && days <= rhythm.maxDays()) {
                return rhythm.frequency();
            }
        }
        return null;
    }

    // ==================== Speichern ====================

    private Contract upsert(Long userId, String key, List<FinanceTransaction> series,
                            Detection detection, List<Contract> existing) {
        Contract contract = existing.stream()
                .filter(c -> key.equals(c.getCounterpartyKey()))
                .filter(c -> c.getAmount() != null
                        && Math.abs(c.getAmount() - detection.amount()) <= detection.amount() * AMOUNT_TOLERANCE)
                .findFirst()
                .orElse(null);

        if (contract != null && Boolean.FALSE.equals(contract.getDetectedAutomatically())) {
            // Von Hand angelegt oder korrigiert - die Erkennung schreibt nicht darueber.
            // Faellig-Datum und Zaehler bleiben ebenfalls in der Hand des Nutzers.
            return contract;
        }

        FinanceTransaction latest = series.get(series.size() - 1);
        boolean isNew = contract == null;

        if (isNew) {
            contract = new Contract();
            contract.setUser(userRepository.getReferenceById(userId));
            contract.setCounterpartyKey(key);
            contract.setDetectedAutomatically(true);
        }

        contract.setName(displayName(latest, key));
        contract.setCategory(latest.getCategory() != null ? latest.getCategory() : "Sonstiges");
        contract.setSubcategory(latest.getSubcategory());
        contract.setDirection("EINNAHME".equals(latest.getType())
                ? TransactionType.INCOME
                : TransactionType.EXPENSE);
        contract.setAmount(detection.amount());
        contract.setFrequency(detection.frequency());
        contract.setIntervalDays(detection.intervalDays());
        contract.setLastBookingDate(detection.lastDate());
        contract.setOccurrenceCount(series.size());
        applyLifecycle(contract, detection.lastDate(), detection.intervalDays());

        contractRepository.save(contract);

        for (FinanceTransaction tx : series) {
            tx.setContract(contract);
            tx.setIsRecurring(true);
        }
        transactionRepository.saveAll(series);

        return contract;
    }

    /** Der Rohname der Gegenpartei ist lesbarer als der normalisierte Schluessel. */
    private String displayName(FinanceTransaction latest, String fallback) {
        String raw = latest.getCounterparty();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }

    /**
     * Entscheidet ueber aktiv/gekuendigt und das naechste Faelligkeitsdatum.
     *
     * <p>Muss auch beim Erkennen laufen, nicht nur bei den unberuehrt gebliebenen Vertraegen: die
     * Buchungshistorie eines gekuendigten Abos bleibt ja bestehen, die Reihe ist also weiterhin
     * erkennbar. Wer hier stur {@code active = true} setzt, hat ein Abo, das nie endet - und eine
     * Prognose, die eine Zahlung erwartet, die nicht mehr kommt.
     */
    private void applyLifecycle(Contract contract, LocalDate lastDate, int intervalDays) {
        long silentDays = ChronoUnit.DAYS.between(lastDate, LocalDate.now());

        if (silentDays > intervalDays * OVERDUE_FACTOR) {
            contract.setActive(false);
            if (contract.getCancelledAt() == null) {
                contract.setCancelledAt(LocalDateTime.now());
            }
            contract.setNextDueDate(null);
        } else {
            contract.setActive(true);
            contract.setCancelledAt(null);
            contract.setNextDueDate(lastDate.plusDays(intervalDays));
        }
    }

    /**
     * Dasselbe fuer Vertraege, die dieser Lauf gar nicht mehr gesehen hat - deren Buchungen aus
     * dem Rueckblickfenster gefallen sind.
     *
     * <p>Nur automatisch erkannte: einen von Hand gepflegten Vertrag stillzulegen, weil die Bank
     * noch nicht gebucht hat, waere eine Anmassung.
     */
    private void markCancelled(List<Contract> existing, Set<Long> touched) {
        LocalDate today = LocalDate.now();
        List<Contract> cancelled = new ArrayList<>();

        for (Contract contract : existing) {
            if (touched.contains(contract.getId())
                    || !Boolean.TRUE.equals(contract.getActive())
                    || !Boolean.TRUE.equals(contract.getDetectedAutomatically())
                    || contract.getLastBookingDate() == null
                    || contract.getIntervalDays() == null) {
                continue;
            }
            long silentDays = ChronoUnit.DAYS.between(contract.getLastBookingDate(), today);
            if (silentDays > contract.getIntervalDays() * OVERDUE_FACTOR) {
                contract.setActive(false);
                contract.setCancelledAt(LocalDateTime.now());
                contract.setNextDueDate(null);
                cancelled.add(contract);
            }
        }
        if (!cancelled.isEmpty()) {
            contractRepository.saveAll(cancelled);
        }
    }

    // ==================== Hilfen ====================

    private long medianLong(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
    }

    private double medianDouble(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
