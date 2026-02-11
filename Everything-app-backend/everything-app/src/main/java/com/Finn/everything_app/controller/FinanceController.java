package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.model.security.CurrentUser;
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
@CrossOrigin(origins = "*")
public class FinanceController {

    private final FinanceTransactionService transactionService;
    private final BudgetCategoryService budgetService;

    private final FinanceTransactionMapper transactionMapper;
    private final BudgetCategoryMapper budgetMapper;

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