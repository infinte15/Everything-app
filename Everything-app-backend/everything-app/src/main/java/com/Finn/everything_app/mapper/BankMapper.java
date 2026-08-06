package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.AspspDTO;
import com.Finn.everything_app.dto.BankAccountDTO;
import com.Finn.everything_app.dto.BankConnectionDTO;
import com.Finn.everything_app.model.BankAccount;
import com.Finn.everything_app.model.BankConnection;
import com.Finn.everything_app.service.bank.AspspInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Uebersetzt Verbindung, Konto und Institut in die Form, die die App sieht.
 *
 * <p>Bewusst nur in eine Richtung: keines dieser Objekte wird von der App geschrieben. Was der
 * Nutzer aendern darf, ist genau ein Schalter ({@code syncEnabled}), und der geht ueber einen
 * eigenen Endpunkt - ein {@code toEntity} waere hier eine Einladung, versehentlich Salden oder
 * Sitzungs-IDs aus dem Client zu uebernehmen.
 */
@Component
public class BankMapper {

    public BankConnectionDTO toDTO(BankConnection connection) {
        if (connection == null) return null;

        BankConnectionDTO dto = new BankConnectionDTO();
        dto.setId(connection.getId());
        dto.setAspspName(connection.getAspspName());
        dto.setAspspCountry(connection.getAspspCountry());
        dto.setStatus(connection.getStatus() != null ? connection.getStatus().name() : null);
        dto.setValidUntil(connection.getValidUntil());
        dto.setLastSyncAt(connection.getLastSyncAt());
        dto.setLastSyncError(connection.getLastSyncError());
        dto.setCreatedAt(connection.getCreatedAt());

        if (connection.getValidUntil() != null) {
            dto.setDaysUntilExpiry(ChronoUnit.DAYS.between(LocalDateTime.now(), connection.getValidUntil()));
        }
        return dto;
    }

    public BankAccountDTO toDTO(BankAccount account) {
        if (account == null) return null;

        BankAccountDTO dto = new BankAccountDTO();
        dto.setId(account.getId());
        dto.setDisplayName(account.getDisplayName());
        dto.setIbanSuffix(suffix(account.getIban()));
        dto.setCurrency(account.getCurrency());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setBalanceUpdatedAt(account.getBalanceUpdatedAt());
        dto.setSyncEnabled(account.getSyncEnabled());

        if (account.getConnection() != null) {
            dto.setConnectionId(account.getConnection().getId());
            dto.setAspspName(account.getConnection().getAspspName());
        }
        return dto;
    }

    public AspspDTO toDTO(AspspInfo aspsp) {
        if (aspsp == null) return null;
        return new AspspDTO(aspsp.name(), aspsp.country(), aspsp.logoUrl(),
                aspsp.group(), aspsp.beta(), aspsp.redirectSupported());
    }

    private String suffix(String iban) {
        if (iban == null || iban.length() < 4) {
            return null;
        }
        return iban.substring(iban.length() - 4);
    }
}
