package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByIdAndUserId(Long id, Long userId);

    List<Semester> findByUserIdOrderByOrderIndexAscIdAsc(Long userId);

    long countByUserId(Long userId);

    @Query("select coalesce(max(s.orderIndex), -1) from Semester s where s.user.id = :userId")
    int findMaxOrderIndex(@Param("userId") Long userId);

    /**
     * Setzt das isCurrent-Flag bei allen anderen Semestern zurück. Als eine Anweisung, damit
     * „genau eines ist aktuell" nicht davon abhängt, dass der Aufrufer alle Zeilen lädt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Semester s set s.isCurrent = false where s.user.id = :userId and s.id <> :keepId")
    void clearCurrentFlagExcept(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
