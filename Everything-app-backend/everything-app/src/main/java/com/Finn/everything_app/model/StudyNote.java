package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;


@Entity
@Table(name = "study_notes")
@Data
public class StudyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Die alte, gefälschte Hierarchie: hier stand eine Ordner-ID, die serverseitig nie
    // existierte. Die Spalte bleibt stehen (ddl-auto=update nimmt nichts weg), wird aber nicht
    // mehr beschrieben — der Baum hängt jetzt an parent.
    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String tags;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;


    private String filePath;
    private String fileType;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /**
     * Die Elternseite; {@code null} heißt Wurzel.
     *
     * Kein {@code isFolder}, kein Typ-Enum: jeder Knoten ist eine Seite, eine Seite mit Kindern
     * bekommt in der UI ein Aufklapp-Dreieck. Genau die Ordner/Notiz-Dualität hatte den
     * category-Hack erzwungen.
     *
     * Bewusst OHNE {@code cascade}: das Löschen eines Teilbaums läuft explizit über den Service
     * (siehe StudyNoteService.deleteNote), damit die Kurszähler stimmen.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private StudyNote parent;

    /** Position unter den Geschwistern. */
    @Column(name = "order_index")
    private Integer orderIndex = 0;

    /** Ein Emoji vor dem Titel. Acht Zeichen, weil ein Emoji mehrere Codepoints haben kann. */
    @Column(length = 8)
    private String icon;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    /**
     * Bestandszeilen haben nach ddl-auto=update NULL in order_index — der Feldinitialisierer
     * greift nur bei frisch konstruierten Objekten, nicht bei geladenen. Ohne das fliegt beim
     * ersten Sortieren eine NPE beim Unboxing.
     */
    @PostLoad
    protected void normalizeDefaults() {
        if (orderIndex == null) orderIndex = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}