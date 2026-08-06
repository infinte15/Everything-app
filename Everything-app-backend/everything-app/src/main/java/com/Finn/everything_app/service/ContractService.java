package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Contract;
import com.Finn.everything_app.model.FinanceTransaction;
import com.Finn.everything_app.repository.ContractRepository;
import com.Finn.everything_app.repository.FinanceTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lesen und Pflegen der Vertraege.
 *
 * <p>Die Erkennung selbst liegt in {@link ContractDetectionService}; hier steht nur, was der Nutzer
 * damit tun kann. Die Trennung ist die uebliche: eine Aenderung von Hand setzt
 * {@code detectedAutomatically = false} und stellt den Vertrag damit dauerhaft unter Handbetrieb.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final FinanceTransactionRepository transactionRepository;

    public List<Contract> getContracts(Long userId, boolean activeOnly) {
        return activeOnly
                ? contractRepository.findByUserIdAndActiveTrueOrderByNextDueDateAsc(userId)
                : contractRepository.findByUserIdOrderByNameAsc(userId);
    }

    public Contract getContract(Long userId, Long contractId) {
        return contractRepository.findByIdAndUserId(contractId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vertrag nicht gefunden: " + contractId));
    }

    /** Die Buchungen, aus denen der Vertrag erkannt wurde - die Begruendung zum Vertrag. */
    public List<FinanceTransaction> getTransactions(Long userId, Long contractId) {
        getContract(userId, contractId);
        return transactionRepository.findByContractId(contractId);
    }

    @Transactional
    public Contract save(Contract contract) {
        return contractRepository.save(contract);
    }

    /**
     * Loest den Vertrag auf, ohne die Buchungen anzutasten.
     *
     * <p>Die Zuordnung muss explizit geloest werden: {@code FinanceTransaction.contract} traegt
     * bewusst kein Cascade, ein Loeschen wuerde sonst an der Fremdschluesselbedingung scheitern.
     */
    @Transactional
    public void delete(Long userId, Long contractId) {
        Contract contract = getContract(userId, contractId);

        List<FinanceTransaction> linked = transactionRepository.findByContractId(contractId);
        for (FinanceTransaction transaction : linked) {
            transaction.setContract(null);
            transaction.setIsRecurring(false);
        }
        transactionRepository.saveAll(linked);

        contractRepository.delete(contract);
    }
}
