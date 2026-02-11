package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.GradeDTO;
import com.Finn.everything_app.model.Grade;
import org.springframework.stereotype.Component;

@Component
public class GradeMapper {

    public GradeDTO toDTO(Grade grade) {
        if (grade == null) return null;

        GradeDTO dto = new GradeDTO();
        dto.setId(grade.getId());
        dto.setExamName(grade.getExamName());
        dto.setCourseId(grade.getCourse() != null ? grade.getCourse().getId() : null);
        dto.setCourseName(grade.getCourse() != null ? grade.getCourse().getName() : null);
        dto.setGrade(grade.getGrade());
        dto.setWeight(grade.getWeight());
        dto.setExamDate(grade.getExamDate());
        dto.setExamType(grade.getExamType());
        dto.setNotes(grade.getNotes());
        dto.setCreatedAt(grade.getCreatedAt());
        dto.setUpdatedAt(grade.getUpdatedAt());

        return dto;
    }

    public Grade toEntity(GradeDTO dto) {
        if (dto == null) return null;

        Grade grade = new Grade();
        grade.setId(dto.getId());
        grade.setExamName(dto.getExamName());
        grade.setGrade(dto.getGrade());
        grade.setWeight(dto.getWeight());
        grade.setExamDate(dto.getExamDate());
        grade.setExamType(dto.getExamType());
        grade.setNotes(dto.getNotes());

        return grade;
    }
}
