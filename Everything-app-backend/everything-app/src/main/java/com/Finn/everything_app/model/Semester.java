package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein Semester, z.B. „WS 2025/26".
 *
 * Bewusst KEIN eigenes Modul-Entity daneben: {@link Course} ist bereits Parent von Grade,
 * StudyNote, FlashcardDeck und CourseSchedule und ist damit das Modul. Eine zusätzliche
 * Ebene zwänge alle vier Relationen, sich einen Parent auszusuchen, und erzeugte eine
 * Fach/Modul-Dualität, die die Oberfläche ohnehin nicht trennt.
 *
 * {@code Course.semester} (String) bleibt daneben bestehen und wird vom SemesterService
 * synchron gehalten — so funktionieren die bestehenden Frontend-Filter unverändert weiter.
 */
@Entity
@Table(name = "semesters", indexes = {
        @Index(name = "idx_semesters_user", columnList = "user_id")
})
@Data
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Reihenfolge in der Oberfläche. Chronologisch sortieren geht bei Labels wie „WS 25/26" nicht. */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    /** Höchstens eines pro Nutzer — im SemesterService erzwungen, nicht per Constraint. */
    @Column(name = "is_current")
    private Boolean isCurrent = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    protected void normalizeDefaults() {
        if (orderIndex == null) orderIndex = 0;
        if (isCurrent == null)  isCurrent  = false;
    }
}
