package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.StudyNoteDTO;
import com.Finn.everything_app.model.StudyNote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression fuer die Tag-Korruption.
 *
 * toDTO machte String.valueOf(note.getTags()) - aus null wurde der Literalstring "null".
 * toEntity machte String.valueOf(Collections.singletonList(dto.getTags())) - aus "Klausur"
 * wurde "[Klausur]". Beides bei JEDEM Round-Trip erneut, die Tags wuchsen also mit jedem
 * Speichern um ein Klammerpaar.
 */
class StudyNoteMapperTest {

    private final StudyNoteMapper mapper = new StudyNoteMapper();

    private StudyNote note(String tags) {
        StudyNote n = new StudyNote();
        n.setId(1L);
        n.setTitle("Vorlesung 1");
        n.setContent("Inhalt");
        n.setTags(tags);
        return n;
    }

    @Test
    void tagsSurviveARoundTripUnchanged() {
        StudyNote original = note("Klausur,wichtig");

        StudyNote back = mapper.toEntity(mapper.toDTO(original));

        assertEquals("Klausur,wichtig", back.getTags());
    }

    @Test
    void tagsSurviveRepeatedRoundTrips() {
        StudyNote n = note("Klausur");

        for (int i = 0; i < 3; i++) {
            n = mapper.toEntity(mapper.toDTO(n));
        }

        assertEquals("Klausur", n.getTags(),
                "jeder Durchlauf legte frueher ein weiteres Klammerpaar drumherum");
    }

    @Test
    void nullTagsStayNullAndNeverBecomeTheStringNull() {
        StudyNoteDTO dto = mapper.toDTO(note(null));

        assertNull(dto.getTags(), "aus null wurde frueher der Literalstring \"null\"");
        assertNull(mapper.toEntity(dto).getTags());
    }

    // Eine frisch angelegte Seite im Notizbaum hat noch keinen Inhalt, die Spalte ist aber
    // NOT NULL und bleibt es.
    @Test
    void nullContentBecomesAnEmptyString() {
        StudyNoteDTO dto = new StudyNoteDTO();
        dto.setTitle("Neue Seite");
        dto.setContent(null);

        assertEquals("", mapper.toEntity(dto).getContent());
    }
}
