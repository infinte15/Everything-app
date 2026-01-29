package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    // Preferences finden
    Optional<UserPreferences> findByUserId(Long userId);

    // Prüfe ob Preferences existieren
    boolean existsByUserId(Long userId);

    // Lösche Preferences
    void deleteByUserId(Long userId);
}