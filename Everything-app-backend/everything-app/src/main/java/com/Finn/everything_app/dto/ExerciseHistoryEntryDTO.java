package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Eine Uebung, so wie sie in einer bestimmten Einheit trainiert wurde. */
@Data
public class ExerciseHistoryEntryDTO {
    private Long sessionId;
    private String sessionName;
    private LocalDateTime performedAt;

    private List<ExerciseSetDTO> sets = new ArrayList<>();

    private double totalVolumeKg;
    private int totalSets;
    private Double bestSetWeight;
    private Integer bestSetReps;
    private Double estimated1RM;
}
