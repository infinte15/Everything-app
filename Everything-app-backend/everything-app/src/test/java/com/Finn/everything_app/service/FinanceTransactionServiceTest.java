package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.repository.BudgetCategoryRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceTransactionServiceTest {

    @Mock FinanceTransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    @Mock BudgetCategoryRepository budgetCategoryRepository;

    @InjectMocks FinanceTransactionService service;

    @Test
    void getTransactionsByTypeReichtDenDeutschenSpaltenwertDurch() {
        when(transactionRepository.findByUserIdAndType(eq(1L), anyString())).thenReturn(List.of());

        service.getTransactionsByType(1L, "EINNAHME");

        // Die Spalte trägt "EINNAHME"/"AUSGABE" - alles andere liefert immer eine leere Liste.
        verify(transactionRepository).findByUserIdAndType(1L, "EINNAHME");
    }

    @Test
    void getTransactionsByTypeUebersetztAuchDenEnumNamen() {
        when(transactionRepository.findByUserIdAndType(eq(1L), anyString())).thenReturn(List.of());

        service.getTransactionsByType(1L, "EXPENSE");

        verify(transactionRepository).findByUserIdAndType(1L, "AUSGABE");
    }

    @Test
    void getTransactionsByTypeLehntUnbekannteWerteMitBadRequestAb() {
        // Vorher lief hier TransactionType.valueOf(type) in eine IllegalArgumentException und
        // damit in eine 500 - und zwar für jeden Wert, der tatsächlich in der Spalte steht.
        BadRequestException fehler = assertThrows(BadRequestException.class,
                () -> service.getTransactionsByType(1L, "Quatsch"));

        assertTrue(fehler.getMessage().contains("Quatsch"));
        verifyNoInteractions(transactionRepository);
    }
}
