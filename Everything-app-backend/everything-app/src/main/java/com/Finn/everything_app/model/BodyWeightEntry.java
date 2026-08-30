package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein gewogener Wert an einem Tag.
 *
 * <p>Pro Nutzer und Tag hoechstens ein Eintrag - das erzwingt der UNIQUE-Index. Wer zweimal auf
 * die Waage steigt, ueberschreibt den Wert des Tages, statt eine Kurve mit zwei Punkten am
 * selben Datum zu erzeugen, durch die keine Linie geht.
 */
@Entity
@Table(name = "body_weight_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_body_weight_user_date", columnNames = {"user_id", "entry_date"}),
        indexes = @Index(name = "idx_body_weight_user_date", columnList = "user_id, entry_date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BodyWeightEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    /** Gewicht in Kilogramm. */
    @Column(nullable = false)
    private Double weightKg;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
