package com.Finn.everything_app.dto;


import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class FlashcardDTO {
    private Long id;

    @NotBlank(message = "Frage erforderlich")
    @Size(max = 500, message = "Frage darf maximal 500 Zeichen lang sein")
    private String question;

    @NotBlank(message = "Antwort erforderlich")
    @Size(max = 1000, message = "Antwort darf maximal 1000 Zeichen lang sein")
    private String answer;

    private Long deckId;
    private String deckName;

    private String category;
    private String difficulty;
    // Spaced Repetition Daten
    private Integer repetitionCount;
    private Integer easinessFactor; // 0-100
    private LocalDateTime nextReviewDate;
    private LocalDateTime lastReviewedAt;

    private String tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
