package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.FinanceTransactionDTO;
import com.Finn.everything_app.model.FinanceTransaction;
import org.springframework.stereotype.Component;

@Component
public class FinanceTransactionMapper {

    public FinanceTransactionDTO toDTO(FinanceTransaction transaction) {
        if (transaction == null) return null;

        FinanceTransactionDTO dto = new FinanceTransactionDTO();
        dto.setId(transaction.getId());
        dto.setAmount(transaction.getAmount());
        dto.setType(transaction.getType());
        dto.setCategory(transaction.getCategory());
        dto.setSubcategory(transaction.getSubcategory());
        dto.setDescription(transaction.getDescription());
        dto.setTransactionDate(transaction.getTransactionDate());
        dto.setPaymentMethod(transaction.getPaymentMethod());
        dto.setBudgetCategoryId(transaction.getBudgetCategory() != null ? transaction.getBudgetCategory().getId() : null);
        dto.setTags(transaction.getTags());
        dto.setReceiptUrl(transaction.getReceiptUrl());
        dto.setIsRecurring(transaction.getIsRecurring());
        dto.setRecurringFrequency(transaction.getRecurringFrequency());
        dto.setCounterparty(transaction.getCounterparty());
        dto.setSource(transaction.getSource() != null ? transaction.getSource().name() : null);
        dto.setCategoryLocked(transaction.getCategoryLocked());
        dto.setContractId(transaction.getContract() != null ? transaction.getContract().getId() : null);
        dto.setValueDate(transaction.getValueDate());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setUpdatedAt(transaction.getUpdatedAt());

        return dto;
    }

    public FinanceTransaction toEntity(FinanceTransactionDTO dto) {
        if (dto == null) return null;

        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setId(dto.getId());
        transaction.setAmount(dto.getAmount());
        transaction.setType(dto.getType());
        transaction.setCategory(dto.getCategory());
        transaction.setSubcategory(dto.getSubcategory());
        transaction.setDescription(dto.getDescription());
        transaction.setTransactionDate(dto.getTransactionDate());
        transaction.setPaymentMethod(dto.getPaymentMethod());
        transaction.setTags(dto.getTags());
        transaction.setReceiptUrl(dto.getReceiptUrl());
        transaction.setIsRecurring(dto.getIsRecurring());
        transaction.setRecurringFrequency(dto.getRecurringFrequency());
        transaction.setCounterparty(dto.getCounterparty());
        transaction.setValueDate(dto.getValueDate());
        // source, categoryLocked, externalId und contract bleiben aussen vor: sie beschreiben die
        // Herkunft der Buchung. Wer sie aus dem Client uebernaehme, koennte eine getippte Buchung
        // als Bankbuchung ausgeben und damit den Dedup-Schluessel des Imports vergiften.

        return transaction;
    }
}