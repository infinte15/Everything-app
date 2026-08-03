package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.model.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserId(Long userId);
    List<Project> findByUserIdAndStatus(Long userId, ProjectStatus status);
}