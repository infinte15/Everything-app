package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.SemesterDTO;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.Semester;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SemesterMapper {

    public SemesterDTO toDTO(Semester semester) {
        return toDTO(semester, List.of());
    }

    /**
     * @param modules Module dieses Semesters; nur für die abgeleiteten Zähler. Sie werden
     *                übergeben statt hier nachgeladen, damit die Liste nicht pro Semester
     *                eine eigene Abfrage auslöst.
     */
    public SemesterDTO toDTO(Semester semester, List<Course> modules) {
        if (semester == null) return null;

        SemesterDTO dto = new SemesterDTO();
        dto.setId(semester.getId());
        dto.setLabel(semester.getLabel());
        dto.setStartDate(semester.getStartDate());
        dto.setEndDate(semester.getEndDate());
        dto.setOrderIndex(semester.getOrderIndex());
        dto.setIsCurrent(semester.getIsCurrent());
        dto.setModuleCount(modules.size());
        dto.setTotalEcts(modules.stream()
                .mapToInt(c -> c.getEctsCredits() != null ? c.getEctsCredits() : 0)
                .sum());
        dto.setCreatedAt(semester.getCreatedAt());
        dto.setUpdatedAt(semester.getUpdatedAt());

        return dto;
    }

    public Semester toEntity(SemesterDTO dto) {
        if (dto == null) return null;

        Semester semester = new Semester();
        semester.setId(dto.getId());
        semester.setLabel(dto.getLabel());
        semester.setStartDate(dto.getStartDate());
        semester.setEndDate(dto.getEndDate());
        if (dto.getOrderIndex() != null) semester.setOrderIndex(dto.getOrderIndex());
        semester.setIsCurrent(dto.getIsCurrent() != null && dto.getIsCurrent());

        return semester;
    }
}
