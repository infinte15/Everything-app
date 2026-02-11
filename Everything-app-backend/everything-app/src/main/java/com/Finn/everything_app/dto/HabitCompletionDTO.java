package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HabitCompletionDTO {
    private Long id;
    private Long habitId;
    private LocalDate completionDate;
    private LocalDateTime completedAt;
    private String notes;
    private Boolean wasSuccessful;
}
