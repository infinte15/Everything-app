package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades")
@Data
public class Grade {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_name", nullable = false, length = 200)
    private String examName;

    @Column(nullable = false)
    private Double grade;

    @Column(nullable = false)
    private Integer weight = 100;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "exam_type", length = 50)
    private String examType;

    /**
     * Zählt diese Teilleistung in den Modulschnitt? Ein Schein wird abgelegt und bestanden,
     * verschiebt den Schnitt aber nicht. Bewusst ein Flag und keine nullable grade-Spalte:
     * ddl-auto=update nimmt ein NOT NULL auf Postgres nicht wieder weg.
     * Bestandszeilen haben hier NULL, siehe normalizeDefaults().
     */
    @Column(name = "counts_toward_grade")
    private Boolean countsTowardGrade = true;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    /** Alte Noten kennen countsTowardGrade nicht; sie haben immer gezählt. */
    @PostLoad
    protected void normalizeDefaults() {
        if (countsTowardGrade == null) countsTowardGrade = true;
        if (weight == null) weight = 100;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}