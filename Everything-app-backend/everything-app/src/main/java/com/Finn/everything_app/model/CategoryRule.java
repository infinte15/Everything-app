package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Eine Regel, die eine Buchung einer Kategorie zuordnet.
 *
 * <p><strong>{@link #user} ist als einzige Entity im Projekt absichtlich nullable:</strong>
 * {@code null} bedeutet "ausgelieferte Standardregel", angelegt vom CategoryRuleSeeder und fuer
 * alle Nutzer gueltig. Zwei Konsequenzen daraus:
 *
 * <ul>
 *   <li><strong>Ein Nutzer kann eine globale Regel nicht loeschen.</strong> Die Zeile ist geteilt,
 *       und es gibt keine Tabelle fuer nutzerspezifische Unterdrueckungen. Der einzige Override
 *       ist eine eigene Regel - die laut
 *       {@code CategoryRuleRepository.findApplicableRules} immer vor jeder globalen Regel kommt,
 *       unabhaengig von {@link #priority}. Die Oberflaeche darf fuer globale Regeln also keinen
 *       Loeschen-Knopf anbieten.</li>
 *   <li>Abfragen muessen {@code (r.user is null or r.user.id = :userId)} schreiben - ein schlichtes
 *       {@code findByUserId} unterschlaegt den gesamten Standardsatz.</li>
 * </ul>
 *
 * <p>{@link #pattern} wird <em>normalisiert gespeichert</em> ({@code trim().toLowerCase(Locale.ROOT)}),
 * damit weder der Seeder-Abgleich noch der Kategorisierer pro Zeile umformen muss.
 *
 * <p>{@link #active} gibt es, damit eine schlechte gelernte Regel abgeschaltet statt geloescht
 * werden kann: Lernen ist statistisch, und wer die Evidenz wegwirft, lernt beim naechsten
 * Korrigieren exakt dieselbe falsche Regel erneut.
 *
 * <p>Zu {@link RuleMatchType#REGEX}: das Muster wird hier nirgends validiert. Der Kategorisierer
 * muss jedes Muster einzeln in try/catch kompilieren und die Regel bei
 * {@code PatternSyntaxException} deaktivieren, sonst reisst eine kaputte Nutzerregel den ganzen
 * Import mit. Alle ausgelieferten Regeln sind {@link RuleMatchType#CONTAINS}.
 */
@Entity
@Table(name = "category_rules",
        indexes = {
                @Index(name = "idx_category_rules_user", columnList = "user_id"),
                @Index(name = "idx_category_rules_active", columnList = "active")
        })
@Data
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalisiert gespeichert: klein und getrimmt. */
    @Column(nullable = false, length = 300)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 20)
    private RuleMatchField matchField = RuleMatchField.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private RuleMatchType matchType = RuleMatchType.CONTAINS;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subcategory;

    /**
     * Hoehere Zahl gewinnt - wie bei {@code Task.priority}. Der Standardsatz nutzt 200 fuer
     * Einnahmen, 100 fuer konkrete Marken und 50 fuer absichtlich generische Muster; das
     * verhindert, dass "Gehalt DB Netz AG" ueber die "db "-Regel als Transport gebucht wird.
     */
    @Column(nullable = false)
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleSource source = RuleSource.MANUAL;

    @Column
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** {@code null} = ausgelieferte Standardregel, siehe Klassenkommentar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
