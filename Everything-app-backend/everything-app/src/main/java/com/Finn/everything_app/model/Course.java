package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "courses")
@Data
public class Course {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String code;

    @Column(length = 200)
    private String instructor;

    /**
     * Freitext-Bezeichnung des Semesters. Bleibt erhalten und wird vom SemesterService mit
     * {@link #semesterRef} synchron gehalten: die bestehenden Frontend-Filter arbeiten darauf,
     * und ddl-auto=update könnte die Spalte ohnehin nicht entfernen.
     */
    @Column(length = 50)
    private String semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semesterRef;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 100)
    private String color;

    /**
     * ECTS-Punkte des Moduls. Gewichtet den Modulschnitt im Gesamtschnitt — ohne das zeigte
     * der GPA-Ring im Frontend dauerhaft „—", weil totalEcts immer 0 war.
     * Bestandszeilen haben NULL, siehe normalizeDefaults().
     */
    @Column(name = "ects_credits")
    private Integer ectsCredits = 0;

    @Column(name = "total_notes")
    private Integer totalNotes = 0;

    @Column(name = "total_flashcards")
    private Integer totalFlashcards = 0;

    @Column(name = "total_assignments")
    private Integer totalAssignments = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Bewusst OHNE cascade/orphanRemoval, anders als die beiden Relationen darunter: Notizen
    // bilden seit dem Seitenbaum eine Hierarchie. Eine JPA-Kaskade risse genau die Notizen weg,
    // die am Kurs hängen, und ließe deren Unterseiten mit verschwundener Elternseite zurück.
    // Das Löschen läuft deshalb explizit über StudyNoteService.deleteNotesOfCourse.
    @OneToMany(mappedBy = "course")
    private List<StudyNote> notes;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlashcardDeck> decks;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Grade> grades;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    @PostLoad
    protected void normalizeDefaults() {
        if (ectsCredits == null)      ectsCredits      = 0;
        if (totalNotes == null)       totalNotes       = 0;
        if (totalFlashcards == null)  totalFlashcards  = 0;
        if (totalAssignments == null) totalAssignments = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}