package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.Semester;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.SemesterRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemesterServiceTest {

    @Mock SemesterRepository semesterRepository;
    @Mock CourseRepository courseRepository;
    @Mock UserRepository userRepository;

    @InjectMocks SemesterService service;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Course course(long id, String semesterLabel) {
        Course c = new Course();
        c.setId(id);
        c.setName("Modul " + id);
        c.setSemester(semesterLabel);
        c.setEctsCredits(6);
        return c;
    }

    private Semester semester(Long id, String label) {
        Semester s = new Semester();
        s.setId(id);
        s.setLabel(label);
        s.setOrderIndex(0);
        s.setIsCurrent(false);
        return s;
    }

    /** Vergibt beim Speichern IDs, damit der Backfill verknüpfen kann. */
    private void autoIdOnSave() {
        AtomicLong seq = new AtomicLong(1);
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> {
            Semester s = i.getArgument(0);
            if (s.getId() == null) s.setId(seq.getAndIncrement());
            return s;
        });
    }

    // ── Backfill ────────────────────────────────────────────────────────────────

    @Test
    void backfillCreatesOneSemesterPerDistinctLabelAndLinksTheModules() {
        when(semesterRepository.countByUserId(1L)).thenReturn(0L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(courseRepository.findByUserId(1L)).thenReturn(List.of(
                course(10L, "WS 2025/26"),
                course(11L, "WS 2025/26"),
                course(12L, "SS 2026")));
        autoIdOnSave();

        service.ensureSemestersBackfilled(1L);

        ArgumentCaptor<Semester> saved = ArgumentCaptor.forClass(Semester.class);
        verify(semesterRepository, times(2)).save(saved.capture());
        assertEquals(List.of("SS 2026", "WS 2025/26"),
                saved.getAllValues().stream().map(Semester::getLabel).sorted().toList(),
                "je verschiedener Bezeichnung genau ein Semester");

        ArgumentCaptor<Course> linked = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository, times(3)).save(linked.capture());
        assertTrue(linked.getAllValues().stream().allMatch(c -> c.getSemesterRef() != null),
                "jedes Modul mit Freitext bekommt sein Semester");
    }

    // Der Backfill haengt am GET /semesters. Ohne die Zaehlpruefung liefe er bei jedem Aufruf
    // erneut und legte die Semester immer wieder neu an.
    @Test
    void backfillIsIdempotent() {
        when(semesterRepository.countByUserId(1L)).thenReturn(2L);

        service.ensureSemestersBackfilled(1L);

        verify(semesterRepository, never()).save(any());
        verify(courseRepository, never()).findByUserId(anyLong());
    }

    @Test
    void backfillDoesNothingWithoutAnyFreeTextSemester() {
        when(semesterRepository.countByUserId(1L)).thenReturn(0L);
        when(courseRepository.findByUserId(1L)).thenReturn(List.of(
                course(10L, null), course(11L, "  ")));

        service.ensureSemestersBackfilled(1L);

        verify(semesterRepository, never()).save(any());
    }

    // ── Aktuelles Semester ──────────────────────────────────────────────────────

    @Test
    void setCurrentUnsetsTheFlagOnEveryOtherSemester() {
        Semester target = semester(5L, "SS 2026");
        when(semesterRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(target));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> i.getArgument(0));

        Semester result = service.setCurrent(1L, 5L);

        assertTrue(result.getIsCurrent());
        verify(semesterRepository).clearCurrentFlagExcept(1L, 5L);
    }

    // ── Löschen ─────────────────────────────────────────────────────────────────

    // An einem Modul haengen Noten, Notizen und Karteikarten. Ein Semester zu loeschen darf
    // die also nicht mitreissen - die Module verlieren nur ihre Zuordnung.
    @Test
    void deletingASemesterKeepsItsModulesAndOnlyDetachesThem() {
        Semester target = semester(5L, "SS 2026");
        Course attached = course(10L, "SS 2026");
        attached.setSemesterRef(target);

        when(semesterRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(target));
        when(courseRepository.findByUserIdAndSemesterRefId(1L, 5L)).thenReturn(List.of(attached));

        service.deleteSemester(1L, 5L);

        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(saved.capture());
        assertNull(saved.getValue().getSemesterRef(), "Zuordnung aufgehoben");
        verify(courseRepository, never()).delete(any());
        verify(semesterRepository).delete(target);
    }

    @Test
    void deletingAForeignSemesterIsNotFound() {
        when(semesterRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteSemester(1L, 5L));
        verify(semesterRepository, never()).delete(any());
    }

    // ── Umbenennen ──────────────────────────────────────────────────────────────

    // Course.semester (Freitext) und Semester.label muessen zusammenbleiben, sonst zeigt die
    // Faecherliste weiter die alte Bezeichnung.
    @Test
    void renamingASemesterUpdatesTheFreeTextOnItsModules() {
        Semester existing = semester(5L, "SS 2026");
        Course attached = course(10L, "SS 2026");
        attached.setSemesterRef(existing);

        when(semesterRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> i.getArgument(0));
        when(courseRepository.findByUserIdAndSemesterRefId(1L, 5L)).thenReturn(List.of(attached));

        Semester renamed = semester(5L, "Sommersemester 2026");
        service.updateSemester(1L, 5L, renamed);

        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(saved.capture());
        assertEquals("Sommersemester 2026", saved.getValue().getSemester());
    }

    @Test
    void updatingWithoutARenameLeavesTheModulesAlone() {
        Semester existing = semester(5L, "SS 2026");
        when(semesterRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> i.getArgument(0));

        Semester sameLabel = semester(5L, "SS 2026");
        service.updateSemester(1L, 5L, sameLabel);

        verify(courseRepository, never()).save(any());
    }

    // ── Reihenfolge ─────────────────────────────────────────────────────────────

    @Test
    void reorderAssignsTheListPositionAsIndex() {
        when(semesterRepository.findByIdAndUserId(anyLong(), eq(1L)))
                .thenAnswer(i -> Optional.of(semester(i.getArgument(0), "S" + i.getArgument(0))));
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> i.getArgument(0));

        service.reorderSemesters(1L, List.of(30L, 10L, 20L));

        ArgumentCaptor<Semester> saved = ArgumentCaptor.forClass(Semester.class);
        verify(semesterRepository, times(3)).save(saved.capture());
        assertEquals(0, saved.getAllValues().get(0).getOrderIndex());
        assertEquals(1, saved.getAllValues().get(1).getOrderIndex());
        assertEquals(2, saved.getAllValues().get(2).getOrderIndex());
    }

    @Test
    void aNewSemesterIsAppendedAtTheEnd() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(semesterRepository.findMaxOrderIndex(1L)).thenReturn(4);
        when(semesterRepository.save(any(Semester.class))).thenAnswer(i -> i.getArgument(0));

        Semester created = service.createSemester(1L, semester(null, "WS 2026/27"));

        assertEquals(5, created.getOrderIndex());
    }
}
