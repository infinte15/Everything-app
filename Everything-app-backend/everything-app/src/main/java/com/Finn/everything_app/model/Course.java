package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "courses")
@Data
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String courseCode;
    private String professor;
    private String room;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL)
    private List<CourseSchedule> schedules;

    @OneToMany(mappedBy = "course")
    private List<StudyNote> notes;

    @OneToMany(mappedBy = "course")
    private List<Grade> grades;

    private String color;
}