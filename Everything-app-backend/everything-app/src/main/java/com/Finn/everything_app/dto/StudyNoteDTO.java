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

    // Kein @NotBlank: eine frisch angelegte Seite im Notizbaum ist zulässigerweise leer.
    // Die Spalte bleibt NOT NULL, der Service bildet null auf "" ab.
    private String content;

    private Long courseId;
    private String courseName;

    private String category;

    // Seitenbaum. parentId == null heißt Wurzelseite; der Baum wird im Client aus dieser
    // flachen Liste gebaut.
    private Long parentId;
    private Integer orderIndex;

    @Size(max = 8, message = "Icon darf maximal 8 Zeichen lang sein")
    private String icon;

    @Size(max = 500, message = "Tags dürfen maximal 500 Zeichen lang sein")
    private String tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastReviewedAt;

    private Boolean isFavorite;
}