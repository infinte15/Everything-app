package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eine Zeile auf der Einkaufsliste.
 *
 * <p>Es gibt genau eine offene Liste je Nutzer - eine eigene {@code ShoppingList}-Entitaet
 * braucht eine Einzelnutzer-App nicht.
 *
 * <p>Der eigentliche Grund fuer diese Tabelle ist {@link #isChecked}. Vorher wurde die Liste
 * bei jedem Aufruf neu aus dem Wochenplan berechnet und der Haken lag in einer Dart-Map -
 * er war weg, sobald die App neu zeichnete. Eine Einkaufsliste, die im Laden ihren Stand
 * vergisst, ist keine.
 */
@Entity
@Table(name = "shopping_items", indexes = {
        @Index(name = "idx_shopping_items_user", columnList = "user_id, is_checked")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    /** Summierte Menge in der Einheit {@link #unit}, oder {@code null} bei "Salz". */
    @Column(precision = 10, scale = 3)
    private BigDecimal amount;

    @Column(length = 30)
    private String unit;

    /** Regal im Laden, aus {@code data/ingredient-aisles.json}. */
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "is_checked", nullable = false)
    private Boolean isChecked = false;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ShoppingItemSource source = ShoppingItemSource.MANUAL;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isChecked == null) {
            isChecked = false;
        }
    }
}
