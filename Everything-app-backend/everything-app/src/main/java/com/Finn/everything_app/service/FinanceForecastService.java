package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ContractDTO;
import com.Finn.everything_app.dto.FinanceForecastDTO;
import com.Finn.everything_app.mapper.ContractMapper;
import com.Finn.everything_app.model.BankAccount;
import com.Finn.everything_app.model.Contract;
import com.Finn.everything_app.model.FinanceTransaction;
import com.Finn.everything_app.model.TransactionType;
import com.Finn.everything_app.repository.BankAccountRepository;
import com.Finn.everything_app.repository.ContractRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Beantwortet die einzige Frage, auf die es ankommt: <em>Was bleibt bis Monatsende?</em>
 *
 * <pre>
 * verfügbar = Kontostand
 *           − noch fällige Vertragsausgaben
 *           − Ø Tagesausgabe ohne Verträge × Resttage
 *           + noch erwartete Vertragseinnahmen
 * </pre>
 *
 * <p>Die Trennung in Vertrags- und Alltagsausgaben ist der Kern: Verträge sind einzeln bekannt und
 * werden mit Datum und Betrag angesetzt, alles andere nur als Durchschnitt. Wer beides in einen
 * Mittelwert wirft, verrechnet die Miete zweimal - einmal im Durchschnitt und einmal als Vertrag.
 *
 * <p><strong>Ohne verbundenes Konto gibt es keine Zahl</strong>, nur die Vertragsseite. Eine aus
 * den getippten Buchungen zusammengezaehlte Null waere keine Prognose, sondern eine Behauptung.
 */
@Service
@RequiredArgsConstructor
public class FinanceForecastService {

    /** Zeitraum fuer den Durchschnitt der Alltagsausgaben. */
    private static final int AVERAGE_LOOKBACK_MONTHS = 3;

    /** Schutz gegen Endlosschleifen bei einem unplausiblen Intervall aus der Datenbank. */
    private static final int MAX_OCCURRENCES_PER_MONTH = 40;

    private final FinanceTransactionRepository transactionRepository;
    private final ContractRepository contractRepository;
    private final BankAccountRepository accountRepository;
    private final ContractMapper contractMapper;

    public FinanceForecastDTO forecast(Long userId, LocalDate month) {
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());
        LocalDate today = LocalDate.now();

        // Bei einem vergangenen Monat gibt es nichts zu prognostizieren - der Anker ist dann das
        // Monatsende selbst, und die Kurve besteht ausschliesslich aus Ist-Werten.
        LocalDate anchor = today.isBefore(monthStart) ? monthStart
                : (today.isAfter(monthEnd) ? monthEnd : today);

        FinanceForecastDTO dto = new FinanceForecastDTO();
        dto.setMonth(String.format("%04d-%02d", monthStart.getYear(), monthStart.getMonthValue()));
        dto.setMonthStart(monthStart);
        dto.setMonthEnd(monthEnd);
        dto.setDaysRemaining((int) monthEnd.toEpochDay() - (int) anchor.toEpochDay());

        List<FinanceTransaction> monthTransactions =
                transactionRepository.findByUserIdAndTransactionDateBetween(userId, monthStart, monthEnd);
        dto.setMonthIncome(round(sum(monthTransactions, true)));
        dto.setMonthExpenses(round(sum(monthTransactions, false)));

        double dailyVariable = averageDailyVariableExpenses(userId, today);
        dto.setAverageDailyVariableExpenses(round(dailyVariable));

        List<Occurrence> occurrences = upcomingOccurrences(userId, anchor, monthEnd);
        double contractExpenses = occurrences.stream()
                .filter(occurrence -> occurrence.contract().getDirection() == TransactionType.EXPENSE)
                .mapToDouble(occurrence -> occurrence.contract().getAmount())
                .sum();
        double contractIncome = occurrences.stream()
                .filter(occurrence -> occurrence.contract().getDirection() == TransactionType.INCOME)
                .mapToDouble(occurrence -> occurrence.contract().getAmount())
                .sum();

        dto.setUpcomingContractExpenses(round(contractExpenses));
        dto.setUpcomingContractIncome(round(contractIncome));
        dto.setProjectedVariableExpenses(round(dailyVariable * dto.getDaysRemaining()));
        dto.setUpcoming(distinctContracts(occurrences));

        Double currentBalance = currentBalance(userId);
        if (currentBalance == null) {
            // Ohne Bankanbindung bleiben available und currentBalance null; die Vertragsseite
            // oben ist trotzdem gefuellt und fuer sich genommen aussagekraeftig.
            dto.setSeries(List.of());
            return dto;
        }

        dto.setCurrentBalance(round(currentBalance));
        List<FinanceForecastDTO.DayPoint> series = new ArrayList<>();
        series.addAll(actualSeries(monthTransactions, monthStart, anchor, currentBalance));

        Map<LocalDate, Double> byDate = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            double signed = occurrence.contract().getDirection() == TransactionType.INCOME
                    ? occurrence.contract().getAmount()
                    : -occurrence.contract().getAmount();
            byDate.merge(occurrence.date(), signed, Double::sum);
        }

        // Was heute faellig ist, zaehlt zur Prognose und nicht zur Vergangenheit: der Kontostand
        // von heute enthaelt es noch nicht, sonst waere der Vertrag nicht mehr faellig. Ohne diese
        // Zeile stuende es in upcomingContractExpenses, fehlte aber in available - und die Kernzahl
        // waere um genau diesen Betrag zu hoch.
        double available = currentBalance + byDate.getOrDefault(anchor, 0.0);

        for (LocalDate day = anchor.plusDays(1); !day.isAfter(monthEnd); day = day.plusDays(1)) {
            available += byDate.getOrDefault(day, 0.0) - dailyVariable;
            series.add(new FinanceForecastDTO.DayPoint(day, round(available), true));
        }

        dto.setAvailable(round(available));
        dto.setShortfall(available < 0);
        dto.setSeries(series);
        return dto;
    }

    // ==================== Kurve ====================

    /**
     * Rekonstruiert den Saldo der bereits vergangenen Tage.
     *
     * <p>Rueckwaerts, weil nur <em>ein</em> Saldo bekannt ist: der von heute. Der Stand am Ende von
     * Tag {@code d-1} ist der von Tag {@code d} abzueglich dessen, was an Tag {@code d} bewegt
     * wurde.
     */
    private List<FinanceForecastDTO.DayPoint> actualSeries(List<FinanceTransaction> monthTransactions,
                                                           LocalDate monthStart, LocalDate anchor,
                                                           double anchorBalance) {
        Map<LocalDate, Double> net = new HashMap<>();
        for (FinanceTransaction transaction : monthTransactions) {
            if (transaction.getTransactionDate().isAfter(anchor)) {
                continue;
            }
            double signed = "EINNAHME".equals(transaction.getType())
                    ? transaction.getAmount()
                    : -transaction.getAmount();
            net.merge(transaction.getTransactionDate(), signed, Double::sum);
        }

        List<FinanceForecastDTO.DayPoint> reversed = new ArrayList<>();
        double running = anchorBalance;
        for (LocalDate day = anchor; !day.isBefore(monthStart); day = day.minusDays(1)) {
            reversed.add(new FinanceForecastDTO.DayPoint(day, round(running), false));
            running -= net.getOrDefault(day, 0.0);
        }
        reversed.sort(Comparator.comparing(FinanceForecastDTO.DayPoint::getDate));
        return reversed;
    }

    // ==================== Vertraege ====================

    /** Ein einzelnes Faelligkeitsdatum eines Vertrags im Prognosefenster. */
    private record Occurrence(Contract contract, LocalDate date) {
    }

    /**
     * Alle Faelligkeiten im Fenster - ein woechentlicher Vertrag kommt darin mehrfach vor.
     *
     * <p>Gerechnet wird mit {@code intervalDays}, dem gemessenen Abstand, nicht mit dem Etikett
     * {@code frequency}: ein Abo, das faktisch alle 28 Tage bucht, waere sonst um zwei Tage im
     * Monat versetzt und faellt ueber ein Jahr um fast einen ganzen Zyklus daneben.
     */
    private List<Occurrence> upcomingOccurrences(Long userId, LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();

        for (Contract contract : contractRepository.findByUserIdAndActiveTrueOrderByNextDueDateAsc(userId)) {
            if (contract.getNextDueDate() == null || contract.getAmount() == null) {
                continue;
            }
            int interval = contract.getIntervalDays() != null && contract.getIntervalDays() > 0
                    ? contract.getIntervalDays()
                    : 30;

            LocalDate due = contract.getNextDueDate();
            // Eine ueberfaellige Buchung steht noch aus - sie faellt nicht weg, nur weil die Bank
            // sie noch nicht gebucht hat. Sie wird auf den naechsten Tag im Fenster gezogen.
            if (due.isBefore(from)) {
                due = from;
            }

            int guard = 0;
            while (!due.isAfter(to) && guard++ < MAX_OCCURRENCES_PER_MONTH) {
                result.add(new Occurrence(contract, due));
                due = due.plusDays(interval);
            }
        }
        result.sort(Comparator.comparing(Occurrence::date));
        return result;
    }

    /** Fuer die Liste in der Oberflaeche: jeder Vertrag einmal, in der Reihenfolge der Faelligkeit. */
    private List<ContractDTO> distinctContracts(List<Occurrence> occurrences) {
        List<ContractDTO> result = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            Long id = occurrence.contract().getId();
            if (seen.contains(id)) {
                continue;
            }
            seen.add(id);
            ContractDTO dto = contractMapper.toDTO(occurrence.contract());
            // Das naechste Datum im Fenster ist aussagekraeftiger als das gespeicherte, das bei
            // einem ueberfaelligen Vertrag in der Vergangenheit liegt.
            dto.setNextDueDate(occurrence.date());
            result.add(dto);
        }
        return result;
    }

    // ==================== Grundlagen ====================

    /**
     * Durchschnittliche Tagesausgabe <em>ohne</em> Vertraege.
     *
     * <p>Der laufende Monat bleibt aussen vor: er ist unvollstaendig und wuerde den Durchschnitt
     * je nach Tag im Monat systematisch nach unten oder oben ziehen.
     */
    private double averageDailyVariableExpenses(Long userId, LocalDate today) {
        LocalDate end = today.withDayOfMonth(1).minusDays(1);
        LocalDate start = end.withDayOfMonth(1).minusMonths(AVERAGE_LOOKBACK_MONTHS - 1L);

        List<FinanceTransaction> transactions =
                transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);

        double total = transactions.stream()
                .filter(transaction -> "AUSGABE".equals(transaction.getType()))
                .filter(transaction -> transaction.getContract() == null)
                .filter(transaction -> !Boolean.TRUE.equals(transaction.getIsRecurring()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        long days = end.toEpochDay() - start.toEpochDay() + 1;
        return days > 0 ? total / days : 0.0;
    }

    /**
     * Kontostand ueber alle abgerufenen Konten.
     *
     * @return {@code null}, wenn kein Konto verbunden ist oder noch keines einen Saldo gemeldet hat.
     *         Bewusst nicht {@code 0.0}: das waere eine Aussage ueber Geld, das niemand gezaehlt hat.
     */
    private Double currentBalance(Long userId) {
        List<BankAccount> accounts = accountRepository.findByUserIdAndSyncEnabledTrue(userId);
        boolean any = accounts.stream().anyMatch(account -> account.getCurrentBalance() != null);
        if (!any) {
            return null;
        }
        return accounts.stream()
                .filter(account -> account.getCurrentBalance() != null)
                .mapToDouble(BankAccount::getCurrentBalance)
                .sum();
    }

    private double sum(List<FinanceTransaction> transactions, boolean income) {
        String type = income ? "EINNAHME" : "AUSGABE";
        return transactions.stream()
                .filter(transaction -> type.equals(transaction.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
