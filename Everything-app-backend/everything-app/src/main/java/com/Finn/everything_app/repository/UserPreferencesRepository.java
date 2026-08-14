package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    /**
     * Nutzer, deren rollierendes Planungsfenster heute noch nicht weitergeschoben wurde.
     *
     * {@code autoScheduleEnabled IS NULL} zählt als eingeschaltet — genau wie im
     * ScheduleRegenerationCoordinator, der nur auf explizites FALSE prüft. Nutzer ganz ohne
     * Preferences-Zeile fehlen hier bewusst: für die lief der Scheduler noch nie, also gibt es
     * auch kein Fenster weiterzuschieben.
     */
    @Query("SELECT p.user.id FROM UserPreferences p "
            + "WHERE (p.lastScheduleRunDate IS NULL OR p.lastScheduleRunDate < :today) "
            + "AND (p.autoScheduleEnabled IS NULL OR p.autoScheduleEnabled = true)")
    List<Long> findUserIdsNeedingRollForward(@Param("today") LocalDate today);

    // Preferences finden
    Optional<UserPreferences> findByUserId(Long userId);

    // Prüfe ob Preferences existieren
    boolean existsByUserId(Long userId);

    // Lösche Preferences
    void deleteByUserId(Long userId);
}