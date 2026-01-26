package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "study_notes")
@Data
public class StudyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 10000)
    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @OneToMany(mappedBy = "studyNote", cascade = CascadeType.ALL)
    private List<Flashcard> flashcards;

    @ElementCollection
    private List<String> tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    private String filePath;
    private String fileType;
}