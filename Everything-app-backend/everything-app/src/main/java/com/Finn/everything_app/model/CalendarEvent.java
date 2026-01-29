package com.Finn.everything_app.model;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
@Data
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    private String location;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    private Boolean isFixed;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    private Long relatedTaskId;
    private Long relatedHabitId;
    private Long relatedWorkoutId;
    private Long relatedClassId;

    private String color;

    @Column(length = 1000)
    private String notes;
}

