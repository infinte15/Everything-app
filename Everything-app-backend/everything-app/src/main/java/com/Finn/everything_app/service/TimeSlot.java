package com.Finn.everything_app.service;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TimeSlot {
    private LocalDateTime start;
    private LocalDateTime end;
    private Integer duration;  // in Minuten
    private LocalDate date;
}