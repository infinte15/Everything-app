package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    private LocalDate startDate;
    private LocalDate targetEndDate;
    private LocalDate actualEndDate;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    private Integer completionPercentage;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<Task> tasks;


    private Integer tasksTotal;
    private Integer tasksCompleted;
}

