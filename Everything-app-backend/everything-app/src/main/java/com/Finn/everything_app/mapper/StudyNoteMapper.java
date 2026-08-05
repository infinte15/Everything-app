package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.StudyNoteDTO;
import com.Finn.everything_app.model.StudyNote;
import org.springframework.stereotype.Component;

/**
 * Tags sind eine kommagetrennte Zeichenkette, keine Collection. Vorher lief das über
 * String.valueOf(...) bzw. String.valueOf(Collections.singletonList(...)) — damit wurde aus
 * null der Literalstring "null" und aus "Klausur" der String "[Klausur]", und zwar bei JEDEM
 * Round-Trip erneut. Hier wird schlicht durchgereicht.
 */
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
        dto.setParentId(note.getParent() != null ? note.getParent().getId() : null);
        dto.setOrderIndex(note.getOrderIndex());
        dto.setIcon(note.getIcon());
        dto.setTags(note.getTags());
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
        // Die Spalte ist NOT NULL und bleibt es (ddl-auto=update nimmt das nicht zurück),
        // eine neue Seite im Baum darf aber leer sein.
        note.setContent(dto.getContent() != null ? dto.getContent() : "");
        note.setCategory(dto.getCategory());
        note.setIcon(dto.getIcon());
        note.setTags(dto.getTags());
        note.setIsFavorite(dto.getIsFavorite());
        // parent und orderIndex kommen bewusst NICHT aus dem DTO: die Baumstruktur gehört dem
        // Server und ändert sich nur über /move und /reorder. Beim Anlegen reicht der Service
        // die parentId aus dem DTO durch, aufgelöst über findByIdAndUserId.

        return note;
    }
}