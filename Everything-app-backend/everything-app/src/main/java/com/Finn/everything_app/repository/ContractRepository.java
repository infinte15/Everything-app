package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Contract;
import com.Finn.everything_app.model.TransactionType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByUserIdOrderByNameAsc(Long userId);

    // Gekuendigte Vertraege haben nextDueDate = null und landen deshalb am Ende. Dass H2 in den
    // Tests genauso sortiert wie PostgreSQL, liegt einzig am DEFAULT_NULL_ORDERING=HIGH in
    // src/test/resources/application.properties.
    List<Contract> findByUserIdAndActiveTrueOrderByNextDueDateAsc(Long userId);

    Optional<Contract> findByIdAndUserId(Long id, Long userId);

    // Vertragserkennung: Kandidaten zur normalisierten Gegenpartei. Die Betragstoleranz prueft
    // der Service - eine UNIQUE-Bedingung ueber einem double waere fragil, und "Amazon Prime 8,99"
    // neben "Amazon Music 10,99" sind zwei legitime Vertraege.
    List<Contract> findByUserIdAndCounterpartyKey(Long userId, String counterpartyKey);

    // Prognose-Fenster: was wird bis Monatsende noch faellig
    List<Contract> findByUserIdAndActiveTrueAndNextDueDateBetween(Long userId, LocalDate from, LocalDate to);

    // Prognose: Einnahmen (Gehalt) getrennt von Ausgaben rechnen
    List<Contract> findByUserIdAndActiveTrueAndDirection(Long userId, TransactionType direction);
}
