package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ContractDTO;
import com.Finn.everything_app.model.Contract;
import com.Finn.everything_app.model.ContractFrequency;
import com.Finn.everything_app.model.TransactionType;
import org.springframework.stereotype.Component;

@Component
public class ContractMapper {

    public ContractDTO toDTO(Contract contract) {
        if (contract == null) return null;

        ContractDTO dto = new ContractDTO();
        dto.setId(contract.getId());
        dto.setName(contract.getName());
        dto.setCategory(contract.getCategory());
        dto.setSubcategory(contract.getSubcategory());
        dto.setDirection(contract.getDirection() != null ? contract.getDirection().name() : null);
        dto.setAmount(contract.getAmount());
        dto.setFrequency(contract.getFrequency() != null ? contract.getFrequency().name() : null);
        dto.setIntervalDays(contract.getIntervalDays());
        dto.setLastBookingDate(contract.getLastBookingDate());
        dto.setNextDueDate(contract.getNextDueDate());
        dto.setOccurrenceCount(contract.getOccurrenceCount());
        dto.setActive(contract.getActive());
        dto.setCancelledAt(contract.getCancelledAt());
        dto.setDetectedAutomatically(contract.getDetectedAutomatically());
        dto.setMonthlyAmount(monthlyAmount(contract));
        return dto;
    }

    /**
     * Nimmt die Felder entgegen, die der Nutzer aendern darf.
     *
     * <p>{@code counterpartyKey}, {@code occurrenceCount} und {@code lastBookingDate} bleiben
     * bewusst aussen vor: sie sind Messergebnisse der Erkennung, keine Eingaben. Wer sie aus dem
     * Client uebernaehme, koennte die Gruppierung von aussen verbiegen.
     */
    public void applyTo(Contract contract, ContractDTO dto) {
        if (dto == null) return;

        contract.setName(dto.getName());
        contract.setCategory(dto.getCategory());
        contract.setSubcategory(dto.getSubcategory());
        contract.setAmount(dto.getAmount());

        if (dto.getDirection() != null) {
            contract.setDirection(TransactionType.valueOf(dto.getDirection()));
        }
        if (dto.getFrequency() != null) {
            ContractFrequency frequency = ContractFrequency.valueOf(dto.getFrequency());
            contract.setFrequency(frequency);
            // Etikett und gemessener Abstand duerfen nicht auseinanderlaufen - sonst rechnet die
            // Prognose mit dem alten Rhythmus weiter, waehrend die Oberflaeche den neuen anzeigt.
            contract.setIntervalDays(defaultIntervalDays(frequency));
        }
        if (dto.getIntervalDays() != null) {
            contract.setIntervalDays(dto.getIntervalDays());
        }
        if (dto.getNextDueDate() != null) {
            contract.setNextDueDate(dto.getNextDueDate());
        }
        if (dto.getActive() != null) {
            contract.setActive(dto.getActive());
        }
        // Ab jetzt Handarbeit: die Erkennung ueberschreibt diesen Vertrag nicht mehr.
        contract.setDetectedAutomatically(false);
    }

    /** Auf einen Monat normalisiert - nur so lassen sich Verträge verschiedener Rhythmen summieren. */
    private Double monthlyAmount(Contract contract) {
        if (contract.getAmount() == null || contract.getFrequency() == null) {
            return null;
        }
        double perMonth = contract.getAmount() * 30.44 / defaultIntervalDays(contract.getFrequency());
        return Math.round(perMonth * 100.0) / 100.0;
    }

    private int defaultIntervalDays(ContractFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> 7;
            case BIWEEKLY -> 14;
            case MONTHLY -> 30;
            case BIMONTHLY -> 61;
            case QUARTERLY -> 91;
            case SEMIANNUAL -> 183;
            case YEARLY -> 365;
            case IRREGULAR -> 30;
        };
    }
}
