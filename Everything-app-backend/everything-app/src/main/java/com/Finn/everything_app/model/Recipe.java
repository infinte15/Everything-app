package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "recipes")
@Data
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "prep_time_minutes", nullable = false)
    private Integer prepTimeMinutes;

    @Column(name = "cook_time_minutes", nullable = false)
    private Integer cookTimeMinutes;

    @Column(nullable = false)
    private Integer servings;

    @Column(nullable = false, length = 50)
    private String category;


    @Column(nullable = false, columnDefinition = "TEXT")
    private String ingredients;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;


    private Integer calories;
    private Double protein;
    private Double carbs;
    private Double fat;

    @Column(length = 50)
    private String difficulty;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String tags;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL)
    private List<MealPlan> mealPlans;

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