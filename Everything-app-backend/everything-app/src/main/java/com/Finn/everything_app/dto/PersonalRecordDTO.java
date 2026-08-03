package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Bestleistungen einer Uebung ueber die gesamte Historie. */
@Data
public class PersonalRecordDTO {
    private Long exerciseId;
    private String exerciseName;

    private Double maxWeight;
    private Integer maxWeightReps;
    private LocalDateTime maxWeightAt;
    private Long maxWeightSessionId;

    private Integer maxReps;
    private Double maxSetVolumeKg;
    private Double best1RM;

    private long totalSetsAllTime;
    private LocalDateTime firstPerformedAt;
    private LocalDateTime lastPerformedAt;
}
