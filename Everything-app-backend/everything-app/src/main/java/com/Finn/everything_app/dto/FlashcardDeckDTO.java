package com.Finn.everything_app.dto;


import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class FlashcardDeckDTO {
    private Long id;

    @NotBlank(message = "Name erforderlich")
    @Size(max = 100, message = "Name darf maximal 100 Zeichen lang sein")
    private String name;

    private String description;

    private Long courseId;
    private String courseName;

    private Integer totalCards;
    private Integer cardsToReview;
    private Integer masteredCards;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastStudiedAt;
}
