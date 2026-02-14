package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceTransactionService {

    private final FinanceTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    @Transactional
    public FinanceTransaction createTransaction(Long userId, FinanceTransaction transaction, Long budgetCategoryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        transaction.setUser(user);

        if (budgetCategoryId != null) {
            BudgetCategory category = budgetCategoryRepository.findById(budgetCategoryId)
                    .orElseThrow(() -> new RuntimeException("Budget-Kategorie nicht gefunden"));
            transaction.setBudgetCategory(category);
        }

        return transactionRepository.save(transaction);
    }

    public List<FinanceTransaction> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId);
    }

    public FinanceTransaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaktion nicht gefunden"));
    }

    public List<FinanceTransaction> getTransactionsInDateRange(Long userId, LocalDate start, LocalDate end) {
        return transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);
    }

    public List<FinanceTransaction> getTransactionsByType(Long userId, String type) {
        return transactionRepository.findByUserIdAndType(userId, String.valueOf(TransactionType.valueOf(type)));
    }

    public List<FinanceTransaction> getTransactionsByCategory(Long userId, String category) {
        return transactionRepository.findByUserIdAndCategory(userId, category);
    }

    public List<FinanceTransaction> searchTransactions(Long userId, String query) {
        return transactionRepository.findByUserIdAndTag(userId, query);
    }

    @Transactional
    public FinanceTransaction updateTransaction(Long id, FinanceTransaction updatedTransaction) {
        FinanceTransaction transaction = getTransactionById(id);

        if (updatedTransaction.getAmount() != null) {
            transaction.setAmount(updatedTransaction.getAmount());
        }
        if (updatedTransaction.getType() != null) {
            transaction.setType(updatedTransaction.getType());
        }
        if (updatedTransaction.getCategory() != null) {
            transaction.setCategory(updatedTransaction.getCategory());
        }
        if (updatedTransaction.getSubcategory() != null) {
            transaction.setSubcategory(updatedTransaction.getSubcategory());
        }
        if (updatedTransaction.getDescription() != null) {
            transaction.setDescription(updatedTransaction.getDescription());
        }
        if (updatedTransaction.getTransactionDate() != null) {
            transaction.setTransactionDate(updatedTransaction.getTransactionDate());
        }
        if (updatedTransaction.getPaymentMethod() != null) {
            transaction.setPaymentMethod(updatedTransaction.getPaymentMethod());
        }
        if (updatedTransaction.getTags() != null) {
            transaction.setTags(updatedTransaction.getTags());
        }
        if (updatedTransaction.getReceiptUrl() != null) {
            transaction.setReceiptUrl(updatedTransaction.getReceiptUrl());
        }

        return transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        FinanceTransaction transaction = getTransactionById(id);
        transactionRepository.delete(transaction);
    }

    // STATISTICS

    public FinanceStatisticsDTO calculateStatistics(Long userId) {
        List<FinanceTransaction> allTransactions = getUserTransactions(userId);

        double totalIncome = allTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        double totalExpenses = allTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        Map<String, Double> expensesByCategory = allTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        Map<String, Double> incomeByCategory = allTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        String topExpenseCategory = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Double topExpenseAmount = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue)
                .orElse(0.0);

        FinanceStatisticsDTO stats = new FinanceStatisticsDTO();
        stats.setTotalIncome(totalIncome);
        stats.setTotalExpenses(totalExpenses);
        stats.setNetBalance(totalIncome - totalExpenses);
        stats.setExpensesByCategory(expensesByCategory);
        stats.setIncomeByCategory(incomeByCategory);
        stats.setTopExpenseCategory(topExpenseCategory);
        stats.setTopExpenseAmount(topExpenseAmount);

        return stats;
    }

    public FinanceStatisticsDTO calculateMonthlyStatistics(Long userId, LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());

        List<FinanceTransaction> monthTransactions = getTransactionsInDateRange(userId, start, end);

        double monthlyIncome = monthTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        double monthlyExpenses = monthTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        double monthlySavings = monthlyIncome - monthlyExpenses;

        Map<String, Double> expensesByCategory = monthTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        double averageDailyExpenses = monthlyExpenses / month.lengthOfMonth();

        FinanceStatisticsDTO stats = new FinanceStatisticsDTO();
        stats.setMonthlyIncome(monthlyIncome);
        stats.setMonthlyExpenses(monthlyExpenses);
        stats.setMonthlySavings(monthlySavings);
        stats.setExpensesByCategory(expensesByCategory);
        stats.setAverageDailyExpenses(averageDailyExpenses);

        return stats;
    }

    public FinanceStatisticsDTO calculateYearlyStatistics(Long userId, int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<FinanceTransaction> yearTransactions = getTransactionsInDateRange(userId, start, end);

        double totalIncome = yearTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        double totalExpenses = yearTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        FinanceStatisticsDTO stats = new FinanceStatisticsDTO();
        stats.setTotalIncome(totalIncome);
        stats.setTotalExpenses(totalExpenses);
        stats.setNetBalance(totalIncome - totalExpenses);

        return stats;
    }

    public CategoryBreakdownDTO getCategoryBreakdown(Long userId, LocalDate start, LocalDate end) {
        List<FinanceTransaction> transactions = getTransactionsInDateRange(userId, start, end);

        Map<String, Double> expensesByCategory = transactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        Map<String, Double> incomeByCategory = transactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        Map<String, Integer> transactionCountByCategory = transactions.stream()
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingInt(t -> 1)
                ));

        String topExpenseCategory = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Double topExpenseAmount = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue)
                .orElse(0.0);

        String topIncomeCategory = incomeByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Double topIncomeAmount = incomeByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue)
                .orElse(0.0);

        CategoryBreakdownDTO breakdown = new CategoryBreakdownDTO();
        breakdown.setExpensesByCategory(expensesByCategory);
        breakdown.setIncomeByCategory(incomeByCategory);
        breakdown.setTransactionCountByCategory(transactionCountByCategory);
        breakdown.setTopExpenseCategory(topExpenseCategory);
        breakdown.setTopExpenseAmount(topExpenseAmount);
        breakdown.setTopIncomeCategory(topIncomeCategory);
        breakdown.setTopIncomeAmount(topIncomeAmount);

        return breakdown;
    }

    public SpendingTrendsDTO getSpendingTrends(Long userId, LocalDate start, LocalDate end, String groupBy) {
        List<FinanceTransaction> transactions = getTransactionsInDateRange(userId, start, end);

        // Gruppiere nach Monat
        Map<String, List<FinanceTransaction>> groupedTransactions = transactions.stream()
                .collect(Collectors.groupingBy(t ->
                        t.getTransactionDate().getYear() + "-" +
                                String.format("%02d", t.getTransactionDate().getMonthValue())
                ));

        List<SpendingTrendsDTO.TrendDataPoint> expenseTrend = new ArrayList<>();
        List<SpendingTrendsDTO.TrendDataPoint> incomeTrend = new ArrayList<>();

        for (Map.Entry<String, List<FinanceTransaction>> entry : groupedTransactions.entrySet()) {
            String period = entry.getKey();
            List<FinanceTransaction> periodTransactions = entry.getValue();

            double expenses = periodTransactions.stream()
                    .filter(t -> "AUSGABE".equals(t.getType()))
                    .mapToDouble(FinanceTransaction::getAmount)
                    .sum();

            double income = periodTransactions.stream()
                    .filter(t -> "EINNAHME".equals(t.getType()))
                    .mapToDouble(FinanceTransaction::getAmount)
                    .sum();

            int expenseCount = (int) periodTransactions.stream()
                    .filter(t -> "AUSGABE".equals(t.getType()))
                    .count();

            int incomeCount = (int) periodTransactions.stream()
                    .filter(t -> "EINNAHME".equals(t.getType()))
                    .count();

            LocalDate periodDate = periodTransactions.get(0).getTransactionDate();

            expenseTrend.add(new SpendingTrendsDTO.TrendDataPoint(periodDate, period, expenses, expenseCount));
            incomeTrend.add(new SpendingTrendsDTO.TrendDataPoint(periodDate, period, income, incomeCount));
        }

        double avgMonthlyExpense = expenseTrend.stream()
                .mapToDouble(SpendingTrendsDTO.TrendDataPoint::getAmount)
                .average()
                .orElse(0.0);

        double avgMonthlyIncome = incomeTrend.stream()
                .mapToDouble(SpendingTrendsDTO.TrendDataPoint::getAmount)
                .average()
                .orElse(0.0);

        SpendingTrendsDTO trends = new SpendingTrendsDTO();
        trends.setExpenseTrend(expenseTrend);
        trends.setIncomeTrend(incomeTrend);
        trends.setAverageMonthlyExpense(avgMonthlyExpense);
        trends.setAverageMonthlyIncome(avgMonthlyIncome);
        trends.setTrendDirection("STABLE");

        return trends;
    }

    public MonthlyFinanceReportDTO generateMonthlyReport(Long userId, LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = month.withDayOfMonth(month.lengthOfMonth());

        List<FinanceTransaction> monthTransactions = getTransactionsInDateRange(userId, start, end);

        double totalIncome = monthTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        double totalExpenses = monthTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        Map<String, Double> expensesByCategory = monthTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        Map<String, Double> incomeByCategory = monthTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .collect(Collectors.groupingBy(
                        FinanceTransaction::getCategory,
                        Collectors.summingDouble(FinanceTransaction::getAmount)
                ));

        int totalTransactions = monthTransactions.size();
        int incomeTransactions = (int) monthTransactions.stream()
                .filter(t -> "EINNAHME".equals(t.getType()))
                .count();
        int expenseTransactions = (int) monthTransactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .count();

        double avgDailyExpenses = totalExpenses / month.lengthOfMonth();
        double savingsRate = totalIncome > 0 ? ((totalIncome - totalExpenses) / totalIncome) * 100 : 0;

        String topExpenseCategory = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        Double topExpenseAmount = expensesByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue)
                .orElse(0.0);

        MonthlyFinanceReportDTO report = new MonthlyFinanceReportDTO();
        report.setMonth(month.getYear() + "-" + String.format("%02d", month.getMonthValue()));
        report.setTotalIncome(totalIncome);
        report.setTotalExpenses(totalExpenses);
        report.setNetSavings(totalIncome - totalExpenses);
        report.setExpensesByCategory(expensesByCategory);
        report.setIncomeByCategory(incomeByCategory);
        report.setTotalTransactions(totalTransactions);
        report.setIncomeTransactions(incomeTransactions);
        report.setExpenseTransactions(expenseTransactions);
        report.setAverageDailyExpenses(avgDailyExpenses);
        report.setSavingsRate(savingsRate);
        report.setTopExpenseCategory(topExpenseCategory);
        report.setTopExpenseAmount(topExpenseAmount);

        return report;
    }
}