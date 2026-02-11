package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.StudyNoteDTO;
import com.Finn.everything_app.model.StudyNote;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class StudyNoteMapper {

    public StudyNoteDTO toDTO(StudyNote note) {
        if (note == null) return null;

        StudyNoteDTO dto = new StudyNoteDTO();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setContent(note.getContent());
        dto.setCourseId(note.getCourse() != null ? note.getCourse().getId() : null);
        dto.setCourseName(note.getCourse() != null ? note.getCourse().getName() : null);
        dto.setCategory(note.getCategory());
        dto.setTags(String.valueOf(note.getTags()));
        dto.setCreatedAt(note.getCreatedAt());
        dto.setUpdatedAt(note.getUpdatedAt());
        dto.setLastReviewedAt(note.getLastReviewedAt());
        dto.setIsFavorite(note.getIsFavorite());

        return dto;
    }

    public StudyNote toEntity(StudyNoteDTO dto) {
        if (dto == null) return null;

        StudyNote note = new StudyNote();
        note.setId(dto.getId());
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setCategory(dto.getCategory());
        note.setTags(String.valueOf(Collections.singletonList(dto.getTags())));
        note.setIsFavorite(dto.getIsFavorite());

        return note;
    }
}