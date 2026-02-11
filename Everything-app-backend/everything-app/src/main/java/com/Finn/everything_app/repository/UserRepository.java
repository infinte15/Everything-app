package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository Methoden:
    // - save(user)           → Speichern/Updaten
    // - findById(id)         → Suchen nach ID
    // - findAll()            → Alle User holen
    // - deleteById(id)       → Löschen
    // - count()              → Anzahl

    // Custom Queries für SQL
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}