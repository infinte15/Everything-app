package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.BodyWeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyWeightEntryRepository extends JpaRepository<BodyWeightEntry, Long> {

    /** Aelteste zuerst - so, wie die Kurve gezeichnet wird. */
    List<BodyWeightEntry> findByUserIdOrderByDateAsc(Long userId);

    List<BodyWeightEntry> findByUserIdAndDateGreaterThanEqualOrderByDateAsc(Long userId, LocalDate from);

    Optional<BodyWeightEntry> findByUserIdAndDate(Long userId, LocalDate date);

    Optional<BodyWeightEntry> findFirstByUserIdOrderByDateDesc(Long userId);

    Optional<BodyWeightEntry> findByIdAndUserId(Long id, Long userId);
}
