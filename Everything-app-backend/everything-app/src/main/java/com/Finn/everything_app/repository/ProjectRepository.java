package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.model.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserId(Long userId);
    List<Project> findByUserIdAndStatus(Long userId, ProjectStatus status);

    /**
     * Besitzpruefung und Lookup in einer Abfrage. Liefert leer statt "gefunden, aber fremd",
     * damit der Aufrufer 404 melden kann und fremde IDs nicht ueber den Status durchsickern.
     */
    Optional<Project> findByIdAndUserId(Long id, Long userId);

    /** Quelle des Schedulers: nur Projekte in einem Status, der Projektzeit verdient. */
    List<Project> findByUserIdAndStatusIn(Long userId, Collection<ProjectStatus> statuses);
}