package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Routine ohne Uebungsliste - fuer Uebersichts- und Auswahllisten. */
@Data
public class RoutineSummaryDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private String colorHex;
    private String dayLabel;
    private Integer estimatedDurationMinutes;
    private Integer orderIndex;
    private Boolean isArchived;

    private int exerciseCount;
    private int totalSets;

    /** Beteiligte Muskelgruppen als Slugs, nach Satzanzahl absteigend. */
    private List<String> primaryMuscles = new ArrayList<>();

    /** Bis zu vier Uebungsbilder fuer die Karten-Collage im Client. */
    private List<String> previewImageUrls = new ArrayList<>();

    private Long workoutPlanId;
    private String workoutPlanName;
    private LocalDateTime lastPerformedAt;
    private Integer performCount;
}
