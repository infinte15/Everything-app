package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.BankAccount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByUserId(Long userId);

    // Besitzpruefung vor Aendern/Loeschen
    Optional<BankAccount> findByIdAndUserId(Long id, Long userId);

    // Ersetzt die bewusst weggelassene Inverse-Collection auf BankConnection
    List<BankAccount> findByConnectionId(Long connectionId);

    // Upsert beim Sync. Bewusst ueber den identification_hash und nicht ueber die account_uid:
    // die UID wechselt bei jeder Neu-Autorisierung (siehe BankAccount).
    Optional<BankAccount> findByUserIdAndIdentificationHash(Long userId, String identificationHash);

    // Nur diese Konten holt der Sync ab
    List<BankAccount> findByUserIdAndSyncEnabledTrue(Long userId);

    // Startwert der Prognose. Nur Konten mit aktivem Sync, sonst zaehlen interne Umbuchungen doppelt.
    @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM BankAccount a " +
            "WHERE a.user.id = :userId AND a.syncEnabled = true")
    Double getTotalBalance(@Param("userId") Long userId);
}
