package com.Finn.everything_app.dto;


import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class StudyNoteDTO {
    private Long id;

    @NotBlank(message = "Titel erforderlich")
    @Size(max = 200, message = "Titel darf maximal 200 Zeichen lang sein")
    private String title;

    @NotBlank(message = "Inhalt erforderlich")
    private String content;

    private Long courseId;
    private String courseName;

    private String category;

    @Size(max = 500, message = "Tags dürfen maximal 500 Zeichen lang sein")
    private String tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastReviewedAt;

    private Boolean isFavorite;
}