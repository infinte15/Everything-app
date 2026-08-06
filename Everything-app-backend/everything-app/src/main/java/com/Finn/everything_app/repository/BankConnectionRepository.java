package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.BankConnection;
import com.Finn.everything_app.model.BankConnectionStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    // Alle Verbindungen eines Nutzers
    List<BankConnection> findByUserId(Long userId);

    // Besitzpruefung vor Trennen/Loeschen
    Optional<BankConnection> findByIdAndUserId(Long id, Long userId);

    // Browser-Callback -> Nutzer. Der Redirect bringt kein JWT mit, der State ist die einzige Zuordnung.
    Optional<BankConnection> findByAuthState(String authState);

    List<BankConnection> findByUserIdAndStatus(Long userId, BankConnectionStatus status);

    // Naechtlicher Sync ueber alle Nutzer hinweg
    List<BankConnection> findByStatus(BankConnectionStatus status);
}
