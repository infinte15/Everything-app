package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.StudyNote;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.StudyNoteRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sichert die Eigentumsprüfung ab (vorher lief das über ein blankes findById(id) ohne
 * Nutzerbezug) und den Seitenbaum: Verschieben, Umsortieren, Teilbaum-Löschung.
 */
@ExtendWith(MockitoExtension.class)
class StudyNoteServiceTest {

    @Mock StudyNoteRepository studyNoteRepository;
    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;

    @InjectMocks StudyNoteService service;

    private StudyNote note(long id) {
        StudyNote n = new StudyNote();
        n.setId(id);
        n.setTitle("Vorlesung 1");
        n.setContent("Inhalt");
        n.setOrderIndex(0);
        return n;
    }

    private StudyNote note(long id, StudyNote parent, int orderIndex) {
        StudyNote n = note(id);
        n.setParent(parent);
        n.setOrderIndex(orderIndex);
        return n;
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    /** Der Nutzer sieht genau diese Notizen — Grundlage für Baumlauf und Teilbaum-Löschung. */
    private void givenAllNotes(StudyNote... notes) {
        when(studyNoteRepository.findByUserIdOrderByOrderIndexAscIdAsc(1L))
                .thenReturn(List.of(notes));
    }

    // Fremde Notizen dürfen nicht einmal als "existiert" erkennbar sein - deshalb 404.
    @Test
    void getNoteOfAnotherUserIsNotFound() {
        when(studyNoteRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getNote(1L, 7L));
    }

    @Test
    void updateNoteOfAnotherUserIsNotFound() {
        when(studyNoteRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateNote(1L, 7L, note(7L)));
        verify(studyNoteRepository, never()).save(any());
    }

    @Test
    void deleteNoteOfAnotherUserIsNotFound() {
        when(studyNoteRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteNote(1L, 7L));
        verify(studyNoteRepository, never()).deleteAll(anyCollection());
    }

    // Der eigentliche Regressionsschutz: die userId muss IN der Query landen. Ein Test, der nur
    // auf die Exception prüft, bliebe grün, wenn jemand wieder auf findById(id) zurückfällt.
    @Test
    void getNoteQueriesByIdAndUserId() {
        when(studyNoteRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(note(3L)));

        service.getNote(1L, 3L);

        verify(studyNoteRepository).findByIdAndUserId(3L, 1L);
        verify(studyNoteRepository, never()).findById(anyLong());
    }

    // Ohne die Prüfung im Kurs-Zweig könnte man die eigene Notiz an einen fremden Kurs hängen.
    @Test
    void createNoteRejectsAForeignCourse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(courseRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createNote(1L, note(0L), 99L, null));
        verify(studyNoteRepository, never()).save(any());
    }

    @Test
    void createNoteAttachesTheCourseAndCountsIt() {
        Course course = new Course();
        course.setId(5L);
        course.setTotalNotes(2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(courseRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(course));
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        StudyNote saved = service.createNote(1L, note(0L), 5L, null);

        assertEquals(5L, saved.getCourse().getId());
        assertEquals(3, course.getTotalNotes(), "der Kurszähler muss mitwachsen");
    }

    // Die content-Spalte bleibt NOT NULL (ddl-auto=update nimmt das nicht zurück), eine neue
    // Seite im Notizbaum darf aber leer sein.
    @Test
    void createNoteCoalescesNullContentToEmptyString() {
        Course course = new Course();
        course.setId(5L);
        course.setTotalNotes(0);
        StudyNote n = note(0L);
        n.setContent(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(courseRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(course));
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        StudyNote saved = service.createNote(1L, n, 5L, null);

        assertEquals("", saved.getContent(), "null darf nicht in eine NOT-NULL-Spalte laufen");
    }

    // ==================== Freie Notiz vs. Modulseite ====================
    //
    // Das Modul unterscheidet die beiden Arten: ohne Modul ist es eine freie Notiz aus dem
    // Notizen-Space (Kategorie Personal/Studium), mit Modul eine Seite des Seitenbaums im
    // FAECHER-Tab. Eine Zeit lang war das Modul Pflicht — damit liess sich ueberhaupt keine
    // gewoehnliche Notiz mehr anlegen.

    @Test
    void eineFreieNotizBrauchtKeinModul() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(studyNoteRepository.findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(1L))
                .thenReturn(List.of());
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        StudyNote saved = service.createNote(1L, note(0L), null, null);

        assertNull(saved.getCourse(), "ohne Modul ist die Notiz frei, kein Fehler");
        verify(courseRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void eineUnterseiteEinerFreienNotizBleibtOhneModul() {
        StudyNote parent = note(1L);
        parent.setCourse(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(parent));
        when(studyNoteRepository.findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(1L, 1L))
                .thenReturn(List.of());
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        StudyNote created = service.createNote(1L, note(0L), null, 1L);

        assertNull(created.getCourse(), "das Erbe der Elternseite darf null sein");
        // Kein Modul, also auch kein Zaehler, der hochgezaehlt werden koennte.
        verify(courseRepository, never()).findById(any());
    }

    @Test
    void aSubpageInheritsTheModuleOfItsParent() {
        Course course = new Course();
        course.setId(5L);
        course.setTotalNotes(1);
        StudyNote parent = note(1L);
        parent.setCourse(course);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(parent));
        when(studyNoteRepository.findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(1L, 1L))
                .thenReturn(List.of());
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        // Ohne courseId und sogar mit einer abweichenden Angabe: die Elternseite gewinnt.
        StudyNote created = service.createNote(1L, note(0L), 99L, 1L);

        assertEquals(5L, created.getCourse().getId());
        verify(courseRepository, never()).findByIdAndUserId(99L, 1L);
    }

    @Test
    void assigningAModuleTakesTheWholeSubtree() {
        Course target = new Course();
        target.setId(7L);
        target.setTotalNotes(0);

        StudyNote root  = note(1L);
        StudyNote child = note(2L, root, 0);
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(root));
        when(courseRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(target));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(target));
        givenAllNotes(root, child);

        service.assignCourse(1L, 1L, 7L);

        assertEquals(7L, root.getCourse().getId());
        assertEquals(7L, child.getCourse().getId(), "die Unterseite geht mit");
        assertEquals(2, target.getTotalNotes(), "beide Seiten zaehlen im Modul");
    }

    // Sonst haette eine Unterseite ein anderes Modul als ihre Elternseite und tauchte in der
    // falschen Modulansicht auf.
    @Test
    void movingUnderAnotherModuleMovesTheSubtreeToo() {
        Course from = new Course();
        from.setId(5L);
        from.setTotalNotes(2);
        Course to = new Course();
        to.setId(7L);
        to.setTotalNotes(1);

        StudyNote target = note(9L);
        target.setCourse(to);
        StudyNote moved = note(1L);
        moved.setCourse(from);
        StudyNote child = note(2L, moved, 0);
        child.setCourse(from);

        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(moved));
        when(studyNoteRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(target));
        when(studyNoteRepository.findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(1L))
                .thenReturn(List.of(moved, target));
        when(studyNoteRepository.findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(1L, 9L))
                .thenReturn(List.of());
        when(courseRepository.findById(anyLong())).thenAnswer(i ->
                Optional.of(i.getArgument(0, Long.class) == 5L ? from : to));
        givenAllNotes(moved, child, target);

        service.moveNote(1L, 1L, 9L, 0);

        assertEquals(7L, moved.getCourse().getId());
        assertEquals(7L, child.getCourse().getId());
        assertEquals(0, from.getTotalNotes(), "zwei Seiten weniger im alten Modul");
        assertEquals(3, to.getTotalNotes(), "und zwei mehr im neuen");
    }

    // ==================== Seitenbaum ====================

    @Test
    void aNewSubpageLandsAtTheEndOfItsLevel() {
        StudyNote parent = note(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(parent));
        when(studyNoteRepository.findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(1L, 1L))
                .thenReturn(List.of(note(2L, parent, 0), note(3L, parent, 1)));
        when(studyNoteRepository.save(any(StudyNote.class))).thenAnswer(i -> i.getArgument(0));

        StudyNote created = service.createNote(1L, note(0L), null, 1L);

        assertEquals(1L, created.getParent().getId());
        assertEquals(2, created.getOrderIndex(), "hinter die beiden vorhandenen Geschwister");
    }

    // Sonst ließe sich eine Seite unter eine fremde hängen und wäre für beide Nutzer sichtbar.
    @Test
    void aNewSubpageRejectsAForeignParent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(studyNoteRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createNote(1L, note(0L), null, 99L));
        verify(studyNoteRepository, never()).save(any());
    }

    // Der Kern: eine Seite in ihren eigenen Teilbaum zu ziehen würde den Teilbaum aus dem Baum
    // heraus unerreichbar machen.
    @Test
    void movingAPageIntoItsOwnDescendantIsRejected() {
        StudyNote root  = note(1L);
        StudyNote child = note(2L, root, 0);
        StudyNote grand = note(3L, child, 0);
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(root));
        when(studyNoteRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(grand));

        assertThrows(BadRequestException.class, () -> service.moveNote(1L, 1L, 3L, 0));
        verify(studyNoteRepository, never()).saveAll(any());
    }

    @Test
    void aPageCannotBecomeItsOwnParent() {
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(note(1L)));

        assertThrows(BadRequestException.class, () -> service.moveNote(1L, 1L, 1L, 0));
    }

    @Test
    void movingIntoAForeignPageIsNotFound() {
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(note(1L)));
        when(studyNoteRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.moveNote(1L, 1L, 99L, 0));
    }

    // Ein bereits korrupter Zyklus (Datenbank von Hand angefasst) muss 400 liefern und darf
    // nicht den Thread aufhängen.
    @Test
    void anExistingCycleIsRejectedInsteadOfLoopingForever() {
        StudyNote a = note(1L);
        StudyNote b = note(2L);
        a.setParent(b);
        b.setParent(a);   // Zyklus
        StudyNote loose = note(3L);
        when(studyNoteRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(loose));
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(a));

        assertThrows(BadRequestException.class, () -> service.moveNote(1L, 3L, 1L, 0));
    }

    @Test
    void movingWithinTheSameLevelRenumbersTheSiblings() {
        StudyNote first  = note(1L, null, 0);
        StudyNote second = note(2L, null, 1);
        StudyNote third  = note(3L, null, 2);
        when(studyNoteRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(third));
        when(studyNoteRepository.findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(1L))
                .thenReturn(List.of(first, second, third));

        service.moveNote(1L, 3L, null, 0);

        assertEquals(0, third.getOrderIndex(), "die verschobene Seite steht jetzt vorn");
        assertEquals(1, first.getOrderIndex());
        assertEquals(2, second.getOrderIndex());
    }

    // Eine Position jenseits der Liste darf nicht in eine IndexOutOfBounds laufen.
    @Test
    void anOutOfRangePositionIsClamped() {
        StudyNote first  = note(1L, null, 0);
        StudyNote second = note(2L, null, 1);
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(first));
        when(studyNoteRepository.findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(1L))
                .thenReturn(List.of(first, second));

        service.moveNote(1L, 1L, null, 99);

        assertEquals(1, first.getOrderIndex(), "ans Ende, nicht daneben");
        assertEquals(0, second.getOrderIndex());
    }

    @Test
    void reorderSetsTheOrderIndexToTheListPosition() {
        StudyNote a = note(5L, null, 0);
        StudyNote b = note(9L, null, 1);
        StudyNote c = note(2L, null, 2);
        givenAllNotes(a, b, c);

        service.reorderNotes(1L, List.of(9L, 2L, 5L));

        assertEquals(0, b.getOrderIndex());
        assertEquals(1, c.getOrderIndex());
        assertEquals(2, a.getOrderIndex());
    }

    // Fremde IDs in der Liste sind kein Grund, die ganze Sortierung scheitern zu lassen.
    @Test
    void reorderIgnoresForeignIds() {
        StudyNote a = note(5L, null, 0);
        givenAllNotes(a);

        service.reorderNotes(1L, List.of(999L, 5L));

        assertEquals(1, a.getOrderIndex());
    }

    // Notion-Semantik: die Seite nimmt ihren Teilbaum mit. Kinder nach oben zu hängen würde
    // Seiten an Stellen teleportieren, an die sie nie gehörten.
    @Test
    void deletingAPageTakesItsWholeSubtree() {
        StudyNote root   = note(1L);
        StudyNote child  = note(2L, root, 0);
        StudyNote grand  = note(3L, child, 0);
        StudyNote unrelated = note(4L);
        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(root));
        givenAllNotes(root, child, grand, unrelated);

        service.deleteNote(1L, 1L);

        List<Long> deleted = capturedDeletedIds();
        assertEquals(List.of(1L, 2L, 3L), deleted, "drei Ebenen tief, in einem Rutsch");
        assertFalse(deleted.contains(4L), "eine unbeteiligte Seite bleibt");
    }

    // Der Kurszähler darf nicht je Notiz einmal heruntergezählt werden, sondern einmal um die
    // Anzahl - sonst kostet ein tiefer Teilbaum eine Schreiboperation pro Seite.
    @Test
    void deletingASubtreeDecrementsEachCourseCounterOnce() {
        Course course = new Course();
        course.setId(5L);
        course.setTotalNotes(10);

        StudyNote root  = note(1L);
        StudyNote child = note(2L, root, 0);
        StudyNote grand = note(3L, child, 0);
        root.setCourse(course);
        child.setCourse(course);
        grand.setCourse(course);

        when(studyNoteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(root));
        givenAllNotes(root, child, grand);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        service.deleteNote(1L, 1L);

        assertEquals(7, course.getTotalNotes(), "10 - 3 auf einen Schlag");
        verify(courseRepository, times(1)).save(course);
    }

    // Course.notes kaskadiert nicht mehr: eine Kaskade risse die Notizen am Kurs weg und ließe
    // deren Unterseiten mit verschwundener Elternseite zurück.
    @Test
    void deletingTheNotesOfACourseAlsoTakesSubpagesOutsideThatCourse() {
        Course course = new Course();
        course.setId(5L);
        course.setTotalNotes(1);

        StudyNote inCourse = note(1L);
        inCourse.setCourse(course);
        StudyNote subpageWithoutCourse = note(2L, inCourse, 0);

        when(studyNoteRepository.findByUserIdAndCourseId(1L, 5L)).thenReturn(List.of(inCourse));
        givenAllNotes(inCourse, subpageWithoutCourse);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        service.deleteNotesOfCourse(1L, 5L);

        assertEquals(List.of(1L, 2L), capturedDeletedIds(),
                "die Unterseite gehört keinem Kurs an und ginge sonst als Waise verloren");
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedDeletedIds() {
        ArgumentCaptor<Iterable<StudyNote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(studyNoteRepository).deleteAll(captor.capture());

        List<Long> ids = new ArrayList<>();
        for (StudyNote n : captor.getValue()) {
            ids.add(n.getId());
        }
        return ids;
    }
}
