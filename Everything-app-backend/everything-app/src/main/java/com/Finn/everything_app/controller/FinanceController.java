package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceTransactionService transactionService;
    private final BudgetCategoryService budgetService;
    private final ContractService contractService;
    private final TransactionCategorizer categorizer;
    private final FinanceForecastService forecastService;

    private final FinanceTransactionMapper transactionMapper;
    private final BudgetCategoryMapper budgetMapper;
    private final ContractMapper contractMapper;

    // ==================== TRANSACTIONS ====================


    @GetMapping("/transactions")
    public ResponseEntity<List<FinanceTransactionDTO>> getAllTransactions(@CurrentUser Long userId) {
        List<FinanceTransaction> transactions = transactionService.getUserTransactions(userId);
        return ResponseEntity.ok(
                transactions.stream().map(transactionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/transactions/{id}")
    public ResponseEntity<FinanceTransactionDTO> getTransactionById(@PathVariable Long id) {
        FinanceTransaction transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transactionMapper.toDTO(transaction));
    }

    // ==================== CONTRACTS ====================

    /**
     * Wiederkehrende Zahlungen.
     *
     * <p>Lieferte frueher Buchungen mit {@code isRecurring = true}; seit der Vertragserkennung sind
     * es echte {@code Contract}s. Ein Vertrag traegt Rhythmus, naechste Faelligkeit und die Zahl der
     * Buchungen, aus denen er erkannt wurde - all das lag in einer einzelnen Buchung nicht vor.
     */
    @GetMapping("/contracts")
    public ResponseEntity<List<ContractDTO>> getContracts(
            @CurrentUser Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {

        return ResponseEntity.ok(contractService.getContracts(userId, activeOnly).stream()
                .map(contractMapper::toDTO)
                .collect(Collectors.toList()));
    }

    /** Die Buchungen, aus denen der Vertrag erkannt wurde - macht die Erkennung nachvollziehbar. */
    @GetMapping("/contracts/{id}/transactions")
    public ResponseEntity<List<FinanceTransactionDTO>> getContractTransactions(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        return ResponseEntity.ok(contractService.getTransactions(userId, id).stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList()));
    }

    @PutMapping("/contracts/{id}")
    public ResponseEntity<ContractDTO> updateContract(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ContractDTO contractDTO) {

        Contract contract = contractService.getContract(userId, id);
        contractMapper.applyTo(contract, contractDTO);
        return ResponseEntity.ok(contractMapper.toDTO(contractService.save(contract)));
    }

    @DeleteMapping("/contracts/{id}")
    public ResponseEntity<Void> deleteContract(@CurrentUser Long userId, @PathVariable Long id) {
        contractService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/transactions/date-range")
    public ResponseEntity<List<FinanceTransactionDTO>> getTransactionsByDateRange(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<FinanceTransaction> transactions = transactionService.getTransactionsInDateRange(userId, start, end);
        return ResponseEntity.ok(
                transactions.stream().map(transactionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/transactions/type/{type}")
    public ResponseEntity<List<FinanceTransactionDTO>> getTransactionsByType(
            @CurrentUser Long userId,
            @PathVariable String type) {

        List<FinanceTransaction> transactions = transactionService.getTransactionsByType(userId, type);
        return ResponseEntity.ok(
                transactions.stream().map(transactionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/transactions/category/{category}")
    public ResponseEntity<List<FinanceTransactionDTO>> getTransactionsByCategory(
            @CurrentUser Long userId,
            @PathVariable String category) {

        List<FinanceTransaction> transactions = transactionService.getTransactionsByCategory(userId, category);
        return ResponseEntity.ok(
                transactions.stream().map(transactionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/transactions/search")
    public ResponseEntity<List<FinanceTransactionDTO>> searchTransactions(
            @CurrentUser Long userId,
            @RequestParam String query) {

        List<FinanceTransaction> transactions = transactionService.searchTransactions(userId, query);
        return ResponseEntity.ok(
                transactions.stream().map(transactionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/transactions")
    public ResponseEntity<FinanceTransactionDTO> createTransaction(
            @CurrentUser Long userId,
            @Valid @RequestBody FinanceTransactionDTO transactionDTO) {

        FinanceTransaction transaction = transactionMapper.toEntity(transactionDTO);
        FinanceTransaction created = transactionService.createTransaction(
                userId,
                transaction,
                transactionDTO.getBudgetCategoryId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                transactionMapper.toDTO(created)
        );
    }


    @PutMapping("/transactions/{id}")
    public ResponseEntity<FinanceTransactionDTO> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody FinanceTransactionDTO transactionDTO) {

        FinanceTransaction transaction = transactionMapper.toEntity(transactionDTO);
        FinanceTransaction updated = transactionService.updateTransaction(id, transaction);

        return ResponseEntity.ok(transactionMapper.toDTO(updated));
    }


    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kategorie korrigieren - und daraus lernen.
     *
     * <p>Legt zugleich eine Regel auf die normalisierte Gegenpartei an, damit kuenftige Buchungen
     * derselben Quelle richtig einsortiert werden. {@code affectedCount} in der Antwort zaehlt die
     * frueheren Buchungen derselben Gegenpartei: damit kann die App "auch auf 23 frühere Buchungen
     * anwenden?" anbieten, statt es stillschweigend zu tun oder stillschweigend zu lassen.
     */
    @PatchMapping("/transactions/{id}/category")
    public ResponseEntity<RecategorizeResultDTO> recategorize(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RecategorizeRequest request) {

        TransactionCategorizer.Recategorized result = categorizer.recategorize(
                userId, id, request.getCategory(), request.getSubcategory(), request.isApplyToPast());

        return ResponseEntity.ok(new RecategorizeResultDTO(
                transactionMapper.toDTO(result.transaction()),
                result.affected(),
                request.isApplyToPast()));
    }

    // ==================== BUDGET CATEGORIES ====================


    @GetMapping("/budgets")
    public ResponseEntity<List<BudgetCategoryDTO>> getAllBudgets(@CurrentUser Long userId) {
        List<BudgetCategory> budgets = budgetService.getUserBudgets(userId);
        return ResponseEntity.ok(
                budgets.stream().map(budgetMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/budgets/{id}")
    public ResponseEntity<BudgetCategoryDTO> getBudgetById(@PathVariable Long id) {
        BudgetCategory budget = budgetService.getBudgetById(id);
        return ResponseEntity.ok(budgetMapper.toDTO(budget));
    }


    @GetMapping("/budgets/active")
    public ResponseEntity<List<BudgetCategoryDTO>> getActiveBudgets(@CurrentUser Long userId) {
        List<BudgetCategory> budgets = budgetService.getActiveBudgets(userId);
        return ResponseEntity.ok(
                budgets.stream().map(budgetMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/budgets")
    public ResponseEntity<BudgetCategoryDTO> createBudget(
            @CurrentUser Long userId,
            @Valid @RequestBody BudgetCategoryDTO budgetDTO) {

        BudgetCategory budget = budgetMapper.toEntity(budgetDTO);
        BudgetCategory created = budgetService.createBudget(userId, budget);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                budgetMapper.toDTO(created)
        );
    }


    @PutMapping("/budgets/{id}")
    public ResponseEntity<BudgetCategoryDTO> updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody BudgetCategoryDTO budgetDTO) {

        BudgetCategory budget = budgetMapper.toEntity(budgetDTO);
        BudgetCategory updated = budgetService.updateBudget(id, budget);

        return ResponseEntity.ok(budgetMapper.toDTO(updated));
    }


    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        budgetService.deleteBudget(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== STATISTICS ====================


    @GetMapping("/stats/overview")
    public ResponseEntity<FinanceStatisticsDTO> getFinanceOverview(@CurrentUser Long userId) {
        FinanceStatisticsDTO stats = transactionService.calculateStatistics(userId);
        return ResponseEntity.ok(stats);
    }


    @GetMapping("/stats/monthly")
    public ResponseEntity<FinanceStatisticsDTO> getMonthlyStatistics(
            @CurrentUser Long userId,
            @RequestParam(required = false) String month) {

        LocalDate targetMonth = month != null ? LocalDate.parse(month + "-01") : LocalDate.now();
        FinanceStatisticsDTO stats = transactionService.calculateMonthlyStatistics(userId, targetMonth);

        return ResponseEntity.ok(stats);
    }


    @GetMapping("/stats/yearly")
    public ResponseEntity<FinanceStatisticsDTO> getYearlyStatistics(
            @CurrentUser Long userId,
            @RequestParam(required = false) Integer year) {

        int targetYear = year != null ? year : LocalDate.now().getYear();
        FinanceStatisticsDTO stats = transactionService.calculateYearlyStatistics(userId, targetYear);

        return ResponseEntity.ok(stats);
    }


    @GetMapping("/stats/category-breakdown")
    public ResponseEntity<CategoryBreakdownDTO> getCategoryBreakdown(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        CategoryBreakdownDTO breakdown = transactionService.getCategoryBreakdown(userId, start, end);
        return ResponseEntity.ok(breakdown);
    }


    @GetMapping("/stats/budget-progress")
    public ResponseEntity<List<BudgetProgressDTO>> getBudgetProgress(@CurrentUser Long userId) {
        List<BudgetProgressDTO> progress = budgetService.calculateBudgetProgress(userId);
        return ResponseEntity.ok(progress);
    }


    @GetMapping("/stats/spending-trends")
    public ResponseEntity<SpendingTrendsDTO> getSpendingTrends(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false, defaultValue = "MONTHLY") String groupBy) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        SpendingTrendsDTO trends = transactionService.getSpendingTrends(userId, start, end, groupBy);
        return ResponseEntity.ok(trends);
    }

    // ==================== FORECAST ====================

    /**
     * Was bis Monatsende noch bleibt.
     *
     * <p>Ohne verbundenes Konto ist {@code available} {@code null} - die Oberflaeche zeigt dann den
     * "Konto verbinden"-Zustand statt einer erfundenen Zahl. Die Vertragsseite der Antwort ist
     * trotzdem gefuellt.
     */
    @GetMapping("/forecast")
    public ResponseEntity<FinanceForecastDTO> getForecast(
            @CurrentUser Long userId,
            @RequestParam(required = false) String month) {

        LocalDate targetMonth = month != null ? LocalDate.parse(month + "-01") : LocalDate.now();
        return ResponseEntity.ok(forecastService.forecast(userId, targetMonth));
    }

    // ==================== REPORTS ====================


    @GetMapping("/reports/monthly")
    public ResponseEntity<MonthlyFinanceReportDTO> getMonthlyReport(
            @CurrentUser Long userId,
            @RequestParam(required = false) String month) {

        LocalDate targetMonth = month != null ? LocalDate.parse(month + "-01") : LocalDate.now();
        MonthlyFinanceReportDTO report = transactionService.generateMonthlyReport(userId, targetMonth);

        return ResponseEntity.ok(report);
    }
}