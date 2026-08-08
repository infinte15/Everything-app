package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.BankAccount;
import com.Finn.everything_app.model.BankConnection;
import com.Finn.everything_app.model.BankConnectionStatus;
import com.Finn.everything_app.model.BudgetCategory;
import com.Finn.everything_app.model.Contract;
import com.Finn.everything_app.model.ContractFrequency;
import com.Finn.everything_app.model.FinanceTransaction;
import com.Finn.everything_app.model.TransactionSource;
import com.Finn.everything_app.model.TransactionType;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.BankAccountRepository;
import com.Finn.everything_app.repository.BankConnectionRepository;
import com.Finn.everything_app.repository.BudgetCategoryRepository;
import com.Finn.everything_app.repository.ContractRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.service.CounterpartyNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Demo-Bestand des Finanz-Space: fünf Monate Kontobewegungen eines Werkstudenten, Budgets,
 * erkannte Verträge und eine angebundene Demo-Bank.
 *
 * <p>Die Buchungstexte sind absichtlich echte Händlernamen aus
 * {@code resources/data/category-rules.json}: nur dann vergibt die automatische Kategorisierung
 * dieselbe Kategorie, die hier steht, und der Kategorien-Ring zeigt nicht zwei Namen für
 * dieselbe Sache.
 *
 * <p>{@link Random} mit festem Startwert statt echter Zufallszahlen — zwei Läufe auf zwei
 * Rechnern sollen dieselben Zahlen zeigen, sonst ist ein Screenshot nichts wert.
 */
@Component
@RequiredArgsConstructor
public class DemoFinanceData {

    private final FinanceTransactionRepository transactionRepository;
    private final BudgetCategoryRepository budgetRepository;
    private final ContractRepository contractRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankConnectionRepository bankConnectionRepository;

    /** Monate Kontohistorie vor dem laufenden Monat. Jeder Monat sind rund 30 Buchungen. */
    private static final int HISTORY_MONTHS = 2;

    private final Random random = new Random(20260807L);

    /** Wiederkehrende Buchung: Text, Kategorie, Betrag, Buchungstag im Monat. */
    private record Recurring(String description, String counterparty, String category,
                             double amount, int dayOfMonth, ContractFrequency frequency,
                             TransactionType direction) {
    }

    /** Ein Ausgabemuster: Händler, Kategorie, Betragsspanne, ungefähre Häufigkeit im Monat. */
    private record Pattern(String description, String category, double min, double max, int perMonth) {
    }

    private static final List<Recurring> RECURRING = List.of(
            new Recurring("Werkstudent Gehalt — Netzwerk Solutions GmbH", "Netzwerk Solutions GmbH",
                    "Einnahmen", 1064.00, 28, ContractFrequency.MONTHLY, TransactionType.INCOME),
            new Recurring("BAföG Nachzahlung Studierendenwerk", "Studierendenwerk",
                    "Einnahmen", 452.00, 1, ContractFrequency.MONTHLY, TransactionType.INCOME),
            new Recurring("Kindergeld Familienkasse", "Familienkasse",
                    "Einnahmen", 250.00, 2, ContractFrequency.MONTHLY, TransactionType.INCOME),

            new Recurring("Miete WG-Zimmer Hausverwaltung Kern", "Hausverwaltung Kern",
                    "Wohnen", 485.00, 3, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("Stadtwerke Abschlag Strom", "Stadtwerke",
                    "Wohnen", 42.00, 5, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("Telekom Deutschland DSL", "Telekom Deutschland",
                    "Wohnen", 29.99, 8, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("o2 Mobilfunk Rechnung", "o2 Telefonica",
                    "Wohnen", 14.99, 12, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("Rundfunkbeitrag ARD ZDF", "Rundfunkbeitrag",
                    "Wohnen", 55.08, 15, ContractFrequency.QUARTERLY, TransactionType.EXPENSE),

            new Recurring("Techniker Krankenkasse Beitrag", "Techniker Krankenkasse",
                    "Gesundheit", 124.50, 15, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("FitX Fitnessstudio Mitgliedsbeitrag", "FitX Fitnessstudio",
                    "Gesundheit", 24.90, 4, ContractFrequency.MONTHLY, TransactionType.EXPENSE),

            new Recurring("Spotify AB Premium", "Spotify",
                    "Unterhaltung", 10.99, 18, ContractFrequency.MONTHLY, TransactionType.EXPENSE),
            new Recurring("Netflix International", "Netflix",
                    "Unterhaltung", 13.99, 20, ContractFrequency.MONTHLY, TransactionType.EXPENSE),

            new Recurring("Deutschlandticket Abo VRR", "VRR",
                    "Transport", 58.00, 1, ContractFrequency.MONTHLY, TransactionType.EXPENSE),

            new Recurring("HUK24 Hausratversicherung", "HUK24",
                    "Sonstiges", 79.20, 22, ContractFrequency.YEARLY, TransactionType.EXPENSE),
            new Recurring("Trade Republic Sparplan MSCI World", "Trade Republic",
                    "Sonstiges", 50.00, 6, ContractFrequency.MONTHLY, TransactionType.EXPENSE));

    // Zusammen rund 650 € im Monat. Mit den Festkosten von gut 850 € bleibt bei 1766 € Einnahmen
    // ein kleiner Überschuss - ein Bestand, der jeden Monat ins Minus läuft, sagt über die
    // Budget-Ansicht nichts, außer dass sie rot werden kann.
    private static final List<Pattern> PATTERNS = List.of(
            new Pattern("REWE Markt GmbH", "Lebensmittel", 12.40, 48.90, 4),
            new Pattern("EDEKA Sagt Danke", "Lebensmittel", 8.20, 42.30, 2),
            new Pattern("ALDI SUED", "Lebensmittel", 15.60, 49.80, 2),
            new Pattern("LIDL Filiale 4712", "Lebensmittel", 9.90, 38.40, 1),
            new Pattern("Alnatura Super Natur Markt", "Lebensmittel", 11.20, 27.60, 1),
            new Pattern("Bäckerei Schneider", "Restaurant", 2.40, 9.80, 3),
            new Pattern("Lieferando.de", "Restaurant", 14.90, 32.50, 1),
            new Pattern("Döner Imbiss Anatolia", "Restaurant", 6.50, 13.00, 2),
            new Pattern("Café Central", "Restaurant", 3.80, 11.40, 2),
            new Pattern("Deutsche Bahn Fernverkehr", "Transport", 19.90, 69.00, 1),
            new Pattern("Tier Mobility", "Transport", 2.20, 7.40, 2),
            new Pattern("dm-drogerie markt", "Gesundheit", 6.40, 34.20, 1),
            new Pattern("Apotheke am Markt", "Gesundheit", 8.90, 27.50, 1),
            new Pattern("Amazon.de Marketplace", "Sonstiges", 11.30, 54.90, 2),
            new Pattern("Media Markt", "Sonstiges", 19.90, 59.00, 1),
            new Pattern("Steam Games", "Unterhaltung", 9.99, 39.99, 1),
            new Pattern("Cineplex Kinocenter", "Unterhaltung", 9.50, 24.00, 1),
            new Pattern("Zalando SE", "Kleidung", 24.90, 89.90, 1));

    @Transactional
    public void seed(User user, LocalDate today) {
        BankAccount account = bank(user, today);
        Map<String, Contract> contracts = contracts(user, today);
        budgets(user, today);

        List<FinanceTransaction> transactions = new ArrayList<>();
        int externalCounter = 1000;

        for (int monthsAgo = HISTORY_MONTHS; monthsAgo >= 0; monthsAgo--) {
            YearMonth month = YearMonth.from(today).minusMonths(monthsAgo);

            for (Recurring entry : RECURRING) {
                if (!dueInMonth(entry, month)) {
                    continue;
                }
                LocalDate date = dayIn(month, entry.dayOfMonth());
                if (date.isAfter(today)) {
                    continue; // die Zukunft bucht die Bank noch nicht
                }
                FinanceTransaction tx = transaction(user, account, entry.description(),
                        entry.counterparty(), entry.category(), entry.amount(),
                        entry.direction(), date, "demo-" + (externalCounter++));
                tx.setIsRecurring(true);
                tx.setRecurringFrequency(entry.frequency().name());
                tx.setContract(contracts.get(entry.description()));
                transactions.add(tx);
            }

            for (Pattern pattern : PATTERNS) {
                for (int i = 0; i < pattern.perMonth(); i++) {
                    LocalDate date = dayIn(month, 1 + random.nextInt(month.lengthOfMonth()));
                    if (date.isAfter(today)) {
                        continue;
                    }
                    double amount = round(pattern.min() + random.nextDouble() * (pattern.max() - pattern.min()));
                    transactions.add(transaction(user, account, pattern.description(),
                            pattern.description(), pattern.category(), amount,
                            TransactionType.EXPENSE, date, "demo-" + (externalCounter++)));
                }
            }
        }

        // Ein paar Einzelposten, die kein Muster abbildet - sie machen den Verlauf glaubhaft.
        transactions.add(manual(user, "Semesterbeitrag Sommersemester", "Studierendenwerk",
                "Sonstiges", 327.40, TransactionType.EXPENSE, today.minusWeeks(9)));
        transactions.add(manual(user, "Lehrbuch Datenbanksysteme (gebraucht)", "Kleinanzeigen",
                "Sonstiges", 24.00, TransactionType.EXPENSE, today.minusWeeks(7)));
        transactions.add(manual(user, "Zahnarzt Eigenanteil Prophylaxe", "Zahnarzt Dr. Ritter",
                "Gesundheit", 89.00, TransactionType.EXPENSE, today.minusWeeks(5)));
        transactions.add(manual(user, "Geburtstagsgeschenk für Lena", "Bar",
                "Sonstiges", 45.00, TransactionType.EXPENSE, today.minusWeeks(3)));
        transactions.add(manual(user, "Steuererstattung Finanzamt", "Finanzamt",
                "Einnahmen", 412.80, TransactionType.INCOME, today.minusWeeks(6)));
        transactions.add(manual(user, "Nachhilfe Mathe (bar)", "Privat",
                "Einnahmen", 60.00, TransactionType.INCOME, today.minusWeeks(2)));
        transactions.add(manual(user, "WG-Kasse Anteil Putzmittel", "WG-Kasse",
                "Sonstiges", 12.50, TransactionType.EXPENSE, today.minusDays(9)));

        transactionRepository.saveAll(transactions);

        updateBalance(account, transactions);
    }

    // ------------------------------------------------------------------ Bausteine

    private FinanceTransaction transaction(User user, BankAccount account, String description,
                                           String counterparty, String category, double amount,
                                           TransactionType direction, LocalDate date, String externalId) {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(user);
        tx.setBankAccount(account);
        tx.setDescription(description);
        tx.setCounterparty(counterparty);
        tx.setCategory(category);
        tx.setAmount(amount);
        tx.setType(direction.toLegacy());
        tx.setTransactionDate(date);
        tx.setValueDate(date);
        tx.setPaymentMethod(direction == TransactionType.INCOME ? "Überweisung" : "Girocard");
        tx.setSource(TransactionSource.BANK);
        tx.setExternalId(externalId);
        return tx;
    }

    /** Bar bezahlt oder von Hand nachgetragen — ohne Bankbezug und ohne externe ID. */
    private FinanceTransaction manual(User user, String description, String counterparty,
                                      String category, double amount, TransactionType direction,
                                      LocalDate date) {
        FinanceTransaction tx = new FinanceTransaction();
        tx.setUser(user);
        tx.setDescription(description);
        tx.setCounterparty(counterparty);
        tx.setCategory(category);
        tx.setAmount(amount);
        tx.setType(direction.toLegacy());
        tx.setTransactionDate(date);
        tx.setPaymentMethod("Bar");
        tx.setSource(TransactionSource.MANUAL);
        tx.setCategoryLocked(true);
        return tx;
    }

    private Map<String, Contract> contracts(User user, LocalDate today) {
        Map<String, Contract> byDescription = new HashMap<>();
        for (Recurring entry : RECURRING) {
            Contract contract = new Contract();
            contract.setUser(user);
            contract.setName(entry.counterparty());
            contract.setCounterpartyKey(CounterpartyNormalizer.normalize(entry.counterparty()));
            contract.setCategory(entry.category());
            contract.setDirection(entry.direction());
            contract.setAmount(entry.amount());
            contract.setFrequency(entry.frequency());
            contract.setActive(true);
            // Als "automatisch erkannt" markiert: genau so entstehen diese Zeilen im Betrieb,
            // und die Oberfläche zeigt erkannte und selbst angelegte Verträge unterschiedlich.
            contract.setDetectedAutomatically(true);
            contract.setOccurrenceCount(occurrences(entry));

            LocalDate last = lastBooking(entry, today);
            contract.setLastBookingDate(last);
            contract.setNextDueDate(nextDue(entry, last));
            byDescription.put(entry.description(), contractRepository.save(contract));
        }

        // Ein gekündigter Vertrag - ohne ihn sieht die Vertragsliste aus, als könne man nichts loswerden.
        Contract cancelled = new Contract();
        cancelled.setUser(user);
        cancelled.setName("DAZN");
        cancelled.setCounterpartyKey(CounterpartyNormalizer.normalize("DAZN Limited"));
        cancelled.setCategory("Unterhaltung");
        cancelled.setDirection(TransactionType.EXPENSE);
        cancelled.setAmount(44.99);
        cancelled.setFrequency(ContractFrequency.MONTHLY);
        cancelled.setActive(false);
        cancelled.setCancelledAt(today.minusWeeks(6).atTime(10, 0));
        cancelled.setDetectedAutomatically(true);
        cancelled.setOccurrenceCount(5);
        cancelled.setLastBookingDate(today.minusWeeks(7));
        contractRepository.save(cancelled);

        return byDescription;
    }

    private void budgets(User user, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        budget(user, "Lebensmittel", 280.0, "#4CAF50", "shopping_cart", month,
                "Wocheneinkauf plus Kleinkram. Ohne Restaurant.");
        budget(user, "Restaurant", 100.0, "#FF7043", "restaurant", month,
                "Mensa zählt nicht mit, die läuft über die Karte.");
        budget(user, "Transport", 130.0, "#42A5F5", "directions_bus", month,
                "Deutschlandticket plus gelegentliche Fernfahrt.");
        budget(user, "Unterhaltung", 70.0, "#AB47BC", "movie", month,
                "Abos und Kino zusammen.");
        // Bewusst zu knapp: eine sichtbar gerissene Grenze gehört in eine Budget-Ansicht,
        // sonst sieht man nie, wie sie das anzeigt.
        budget(user, "Kleidung", 50.0, "#EC407A", "checkroom", month, null);
        budget(user, "Gesundheit", 200.0, "#26A69A", "favorite", month,
                "Krankenkasse dominiert - der Rest ist Puffer.");
        budget(user, "Sonstiges", 170.0, "#78909C", "category", month,
                "Alles, was in keine andere Schublade passt.");
    }

    private void budget(User user, String name, double limit, String color, String icon,
                        YearMonth month, String description) {
        BudgetCategory budget = new BudgetCategory();
        budget.setUser(user);
        budget.setName(name);
        budget.setDescription(description);
        budget.setBudgetLimit(limit);
        budget.setPeriod("MONATLICH");
        budget.setPeriodStart(month.atDay(1));
        budget.setPeriodEnd(month.atEndOfMonth());
        budget.setColor(color);
        budget.setIcon(icon);
        budget.setIsActive(true);
        budgetRepository.save(budget);
    }

    private BankAccount bank(User user, LocalDate today) {
        BankConnection connection = new BankConnection();
        connection.setUser(user);
        connection.setAspspName("Demo Sparkasse");
        connection.setAspspCountry("DE");
        connection.setStatus(BankConnectionStatus.ACTIVE);
        connection.setSessionId("demo-session");
        connection.setAuthState("demo-" + user.getId());
        connection.setValidUntil(today.plusDays(80).atStartOfDay());
        connection.setLastSyncAt(LocalDateTime.now().minusHours(3));
        connection = bankConnectionRepository.save(connection);

        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setConnection(connection);
        account.setIdentificationHash("demo-account-" + user.getId());
        account.setAccountUid("demo-account-uid");
        account.setIban("DE02120300000000202051");
        account.setDisplayName("Girokonto");
        account.setCurrency("EUR");
        account.setSyncEnabled(true);
        return bankAccountRepository.save(account);
    }

    /**
     * Der Kontostand ergibt sich aus den gebuchten Bewegungen plus einem Startguthaben — ein frei
     * gewählter Saldo widerspräche der Umsatzliste, und genau das fällt in einer Demo auf.
     */
    private void updateBalance(BankAccount account, List<FinanceTransaction> transactions) {
        double balance = 1250.00;
        for (FinanceTransaction tx : transactions) {
            if (tx.getBankAccount() == null) {
                continue;
            }
            balance += TransactionType.INCOME.toLegacy().equals(tx.getType())
                    ? tx.getAmount()
                    : -tx.getAmount();
        }
        account.setCurrentBalance(round(balance));
        account.setBalanceUpdatedAt(LocalDateTime.now().minusHours(3));
        bankAccountRepository.save(account);
    }

    // ------------------------------------------------------------------- Rechnen

    private boolean dueInMonth(Recurring entry, YearMonth month) {
        return switch (entry.frequency()) {
            case QUARTERLY -> month.getMonthValue() % 3 == 0;
            case SEMIANNUAL -> month.getMonthValue() % 6 == 0;
            case YEARLY -> month.getMonthValue() == 1;
            default -> true;
        };
    }

    private int occurrences(Recurring entry) {
        return switch (entry.frequency()) {
            case QUARTERLY -> 2;
            case SEMIANNUAL, YEARLY -> 1;
            default -> HISTORY_MONTHS + 1;
        };
    }

    private LocalDate lastBooking(Recurring entry, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        for (int back = 0; back <= 12; back++) {
            YearMonth candidate = month.minusMonths(back);
            if (!dueInMonth(entry, candidate)) {
                continue;
            }
            LocalDate date = dayIn(candidate, entry.dayOfMonth());
            if (!date.isAfter(today)) {
                return date;
            }
        }
        return today;
    }

    private LocalDate nextDue(Recurring entry, LocalDate last) {
        return switch (entry.frequency()) {
            case WEEKLY -> last.plusWeeks(1);
            case BIWEEKLY -> last.plusWeeks(2);
            case BIMONTHLY -> last.plusMonths(2);
            case QUARTERLY -> last.plusMonths(3);
            case SEMIANNUAL -> last.plusMonths(6);
            case YEARLY -> last.plusYears(1);
            default -> last.plusMonths(1);
        };
    }

    /** Der 31. existiert nicht in jedem Monat — abschneiden statt in den Folgemonat rutschen. */
    private LocalDate dayIn(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
