package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ReviewRating;
import jakarta.validation.constraints.NotNull;

/** Body von POST /api/study/flashcards/{id}/review. */
public record ReviewRequest(@NotNull(message = "Bewertung erforderlich") ReviewRating rating) {}
