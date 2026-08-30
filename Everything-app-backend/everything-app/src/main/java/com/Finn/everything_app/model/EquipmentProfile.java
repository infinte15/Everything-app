package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ein benanntes Set verfuegbarer Geraete - "Zuhause", "Studio".
 *
 * <p>Ist ein Profil aktiv, zeigt die Bibliothek nur noch Uebungen, die sich damit
 * tatsaechlich machen lassen. Die Werte sind die {@code equipment}-Zeichenketten des
 * Katalogs (28 saubere Werte in ExerciseDB), keine eigene Aufzaehlung - eine zweite
 * Wertemenge muesste bei jedem Katalog-Wechsel nachgepflegt werden.
 */
@Entity
@Table(name = "equipment_profiles", indexes = {
        @Index(name = "idx_equipment_profiles_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
public class EquipmentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /** Genau ein Profil je Nutzer ist aktiv - oder keines, dann filtert nichts. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "equipment_profile_items",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "equipment", length = 100, nullable = false)
    private Set<String> equipment = new LinkedHashSet<>();
}
