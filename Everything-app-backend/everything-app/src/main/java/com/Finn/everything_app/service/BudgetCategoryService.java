package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.BudgetProgressDTO;
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
public class BudgetCategoryService {

    private final BudgetCategoryRepository budgetCategoryRepository;
    private final UserRepository userRepository;
    private final FinanceTransactionRepository transactionRepository;

    @Transactional
    public BudgetCategory createBudget(Long userId, BudgetCategory budget) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        budget.setUser(user);

        // Setze Periode-Dates wenn nicht vorhanden
        if (budget.getPeriodStart() == null) {
            budget.setPeriodStart(LocalDate.now());
        }
        if (budget.getPeriodEnd() == null) {
            // Standard: 1 Monat
            budget.setPeriodEnd(budget.getPeriodStart().plusMonths(1));
        }

        return budgetCategoryRepository.save(budget);
    }

    public List<BudgetCategory> getUserBudgets(Long userId) {
        List<BudgetCategory> budgets = budgetCategoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Berechne aktuelle Ausgaben für jedes Budget
        for (BudgetCategory budget : budgets) {
            calculateBudgetStatus(budget);
        }

        return budgets;
    }

    public BudgetCategory getBudgetById(Long id) {
        BudgetCategory budget = budgetCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget nicht gefunden"));

        calculateBudgetStatus(budget);
        return budget;
    }

    public List<BudgetCategory> getActiveBudgets(Long userId) {
        List<BudgetCategory> budgets = budgetCategoryRepository.findByUserIdAndIsActiveTrue(userId);

        for (BudgetCategory budget : budgets) {
            calculateBudgetStatus(budget);
        }

        return budgets;
    }

    @Transactional
    public BudgetCategory updateBudget(Long id, BudgetCategory updatedBudget) {
        BudgetCategory budget = budgetCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget nicht gefunden"));

        if (updatedBudget.getName() != null) {
            budget.setName(updatedBudget.getName());
        }
        if (updatedBudget.getDescription() != null) {
            budget.setDescription(updatedBudget.getDescription());
        }
        if (updatedBudget.getBudgetLimit() != null) {
            budget.setBudgetLimit(updatedBudget.getBudgetLimit());
        }
        if (updatedBudget.getPeriod() != null) {
            budget.setPeriod(updatedBudget.getPeriod());
        }
        if (updatedBudget.getPeriodStart() != null) {
            budget.setPeriodStart(updatedBudget.getPeriodStart());
        }
        if (updatedBudget.getPeriodEnd() != null) {
            budget.setPeriodEnd(updatedBudget.getPeriodEnd());
        }
        if (updatedBudget.getColor() != null) {
            budget.setColor(updatedBudget.getColor());
        }
        if (updatedBudget.getIcon() != null) {
            budget.setIcon(updatedBudget.getIcon());
        }
        if (updatedBudget.getIsActive() != null) {
            budget.setIsActive(updatedBudget.getIsActive());
        }

        return budgetCategoryRepository.save(budget);
    }

    @Transactional
    public void deleteBudget(Long id) {
        BudgetCategory budget = budgetCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget nicht gefunden"));
        budgetCategoryRepository.delete(budget);
    }

    // HELPER METHODS

    private void calculateBudgetStatus(BudgetCategory budget) {
        // Hole alle Transaktionen in der Budget-Periode
        List<FinanceTransaction> transactions = transactionRepository
                .findByUserIdAndTransactionDateBetween(
                        budget.getUser().getId(),
                        budget.getPeriodStart(),
                        budget.getPeriodEnd()
                );

        // Berechne Ausgaben für diese Kategorie
        double currentSpent = transactions.stream()
                .filter(t -> "AUSGABE".equals(t.getType()))
                .filter(t -> budget.getName().equals(t.getCategory()))
                .mapToDouble(FinanceTransaction::getAmount)
                .sum();

        budget.setCurrentSpent(currentSpent);
        budget.setRemainingBudget(budget.getBudgetLimit() - currentSpent);

        int percentageUsed = (int) ((currentSpent / budget.getBudgetLimit()) * 100);
        budget.setPercentageUsed(Math.min(percentageUsed, 100));
    }

    public List<BudgetProgressDTO> calculateBudgetProgress(Long userId) {
        List<BudgetCategory> budgets = getActiveBudgets(userId);

        return budgets.stream().map(budget -> {
            BudgetProgressDTO progress = new BudgetProgressDTO();
            progress.setBudgetId(budget.getId());
            progress.setCategoryName(budget.getName());
            progress.setBudgetLimit(budget.getBudgetLimit());
            progress.setCurrentSpent(budget.getCurrentSpent());
            progress.setRemainingBudget(budget.getRemainingBudget());
            progress.setPercentageUsed(budget.getPercentageUsed());
            progress.setIsOverBudget(budget.getCurrentSpent() > budget.getBudgetLimit());

            // Status bestimmen
            if (budget.getPercentageUsed() >= 100) {
                progress.setStatus("EXCEEDED");
            } else if (budget.getPercentageUsed() >= 80) {
                progress.setStatus("WARNING");
            } else {
                progress.setStatus("ON_TRACK");
            }

            progress.setColor(budget.getColor());

            return progress;
        }).collect(Collectors.toList());
    }
}