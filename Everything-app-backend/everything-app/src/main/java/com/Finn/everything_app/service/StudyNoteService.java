package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyNoteService {

    private final StudyNoteRepository studyNoteRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    /** Wie tief der Zyklusschutz aufwärts läuft, bevor er die Struktur für kaputt erklärt. */
    private static final int MAX_TREE_DEPTH = 64;

    /**
     * Legt eine Notiz an.
     *
     * <p>Es gibt <b>zwei Arten von Notizen</b>, und das Modul unterscheidet sie:
     *
     * <ul>
     *   <li>{@code courseId == null} — eine <b>freie Notiz</b>. Sie trägt eine Kategorie
     *       („Personal" oder „Studium") und lebt im Notizen-Space.</li>
     *   <li>{@code courseId != null} — eine <b>Modulseite</b>. Sie ist Teil des Seitenbaums und
     *       lebt im FÄCHER-Tab des Study Space.</li>
     * </ul>
     *
     * <p>Eine Unterseite erbt das Modul ihrer Elternseite und ignoriert eine abweichende Angabe;
     * ein Teilbaum gehört damit immer als Ganzes zur selben Art. Das Erbe darf {@code null}
     * sein — auch eine freie Notiz darf Unterseiten haben.
     *
     * <p>Eine freie Notiz lässt sich nachträglich zur Modulseite machen, über
     * {@link #assignCourse(Long, Long, Long)} oder indem sie im Baum unter eine Modulseite
     * gezogen wird ({@link #moveNote}).
     */
    @Transactional
    public StudyNote createNote(Long userId, StudyNote note, Long courseId, Long parentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        note.setUser(user);
        note.setIsFavorite(note.getIsFavorite() != null ? note.getIsFavorite() : false);
        if (note.getContent() == null) {
            note.setContent("");
        }

        Course course;
        if (parentId != null) {
            // findByIdAndUserId: sonst ließe sich eine Seite unter eine fremde hängen.
            StudyNote parent = getNote(userId, parentId);
            note.setParent(parent);
            // Darf null sein: die Unterseite einer freien Notiz bleibt ebenfalls frei.
            course = parent.getCourse();
        } else {
            course = courseId != null ? requireCourse(userId, courseId) : null;
        }

        // Neue Seiten kommen ans Ende ihrer Ebene.
        note.setOrderIndex(siblingsOf(userId, parentId).size());

        note.setCourse(course);
        if (course != null) {
            adjustNoteCount(course.getId(), +1);
        }

        return studyNoteRepository.save(note);
    }

    /**
     * Ordnet eine Seite samt Teilbaum einem Modul zu.
     *
     * Gedacht für Bestandsseiten, die vor der Modulpflicht entstanden sind, und für das
     * Verschieben zwischen Modulen. Der Teilbaum geht immer mit — eine Unterseite in einem
     * anderen Modul als ihre Elternseite wäre in keiner Ansicht sinnvoll darstellbar.
     */
    @Transactional
    public StudyNote assignCourse(Long userId, Long id, Long courseId) {
        StudyNote note = getNote(userId, id);
        Course course = requireCourse(userId, courseId);

        applyCourseToSubtree(userId, note, course);
        return note;
    }

    private Course requireCourse(Long userId, Long courseId) {
        // findByIdAndUserId, nicht findById: sonst könnte man die eigene Notiz an ein fremdes
        // Modul hängen.
        return courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kurs nicht gefunden"));
    }

    /** Schreibt das Modul auf die Seite und alles darunter und zieht die Kurszähler nach. */
    private void applyCourseToSubtree(Long userId, StudyNote root, Course course) {
        List<StudyNote> subtree = collectSubtrees(userId, List.of(root));

        Map<Long, Integer> delta = new HashMap<>();
        for (StudyNote n : subtree) {
            Long previous = n.getCourse() != null ? n.getCourse().getId() : null;
            if (Objects.equals(previous, course.getId())) continue;

            if (previous != null) delta.merge(previous, -1, Integer::sum);
            delta.merge(course.getId(), +1, Integer::sum);
            n.setCourse(course);
        }

        studyNoteRepository.saveAll(subtree);
        delta.forEach(this::adjustNoteCount);
    }

    @Transactional(readOnly = true)
    public List<StudyNote> getUserNotes(Long userId) {
        return studyNoteRepository.findByUserId(userId);
    }

    /**
     * Einziges Eingangstor für Einzelzugriffe: die userId gehört in die Query. Vorher lief das
     * über findById(id) ohne Nutzerbezug — jeder eingeloggte Nutzer konnte jede fremde Notiz
     * lesen, ändern und löschen.
     */
    @Transactional(readOnly = true)
    public StudyNote getNote(Long userId, Long id) {
        return studyNoteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notiz nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<StudyNote> getNotesByCourse(Long userId, Long courseId) {
        return studyNoteRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Transactional(readOnly = true)
    public List<StudyNote> searchNotes(Long userId, String query) {
        // searchNotes statt des abgeleiteten Finders: der deckt auch Tags ab und ist die
        // Suche, die der Endpunkt verspricht.
        return studyNoteRepository.searchNotes(userId, query);
    }

    @Transactional(readOnly = true)
    public List<StudyNote> getFavoriteNotes(Long userId) {
        return studyNoteRepository.findByUserIdAndIsFavoriteTrue(userId);
    }

    @Transactional
    public StudyNote updateNote(Long userId, Long id, StudyNote updatedNote) {
        StudyNote note = getNote(userId, id);

        if (updatedNote.getTitle() != null) {
            note.setTitle(updatedNote.getTitle());
        }
        if (updatedNote.getContent() != null) {
            note.setContent(updatedNote.getContent());
        }
        if (updatedNote.getCategory() != null) {
            note.setCategory(updatedNote.getCategory());
        }
        if (updatedNote.getTags() != null) {
            note.setTags(updatedNote.getTags());
        }
        if (updatedNote.getIsFavorite() != null) {
            note.setIsFavorite(updatedNote.getIsFavorite());
        }

        return studyNoteRepository.save(note);
    }

    @Transactional
    public StudyNote markAsReviewed(Long userId, Long id) {
        StudyNote note = getNote(userId, id);
        note.setLastReviewedAt(LocalDateTime.now());
        return studyNoteRepository.save(note);
    }

    /**
     * Verschiebt eine Seite unter eine andere (oder auf die Wurzelebene) und setzt sie dort an
     * Position {@code position}.
     *
     * Die Zyklusprüfung ist der eigentliche Inhalt: ohne sie ließe sich eine Seite unter ihren
     * eigenen Nachfahren hängen, und der Teilbaum wäre aus dem Baum heraus nicht mehr
     * erreichbar. Der Aufwärtslauf bricht nach {@link #MAX_TREE_DEPTH} Schritten ab — eine
     * bereits korrupte Struktur muss 400 liefern und darf nicht den Thread aufhängen.
     */
    @Transactional
    public StudyNote moveNote(Long userId, Long id, Long parentId, Integer position) {
        StudyNote note = getNote(userId, id);

        StudyNote newParent = null;
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new BadRequestException("Eine Seite kann nicht ihre eigene Elternseite sein");
            }
            // Fremdnutzer-Schutz: eine fremde Seite existiert für diesen Nutzer schlicht nicht.
            newParent = getNote(userId, parentId);
            assertNoCycle(note, newParent);
        }

        Long oldParentId = note.getParent() != null ? note.getParent().getId() : null;

        // Aus der alten Ebene herausnehmen und die verbliebenen Geschwister dicht schließen.
        List<StudyNote> oldSiblings = siblingsOf(userId, oldParentId);
        oldSiblings.removeIf(n -> n.getId().equals(id));
        renumber(oldSiblings);

        note.setParent(newParent);

        List<StudyNote> targetSiblings = Objects.equals(oldParentId, parentId)
                ? oldSiblings                            // gleiche Ebene, schon bereinigt
                : withoutNote(siblingsOf(userId, parentId), id);
        int index = Math.max(0, Math.min(position != null ? position : targetSiblings.size(),
                targetSiblings.size()));
        targetSiblings.add(index, note);
        renumber(targetSiblings);

        studyNoteRepository.saveAll(targetSiblings);
        if (!Objects.equals(oldParentId, parentId)) {
            studyNoteRepository.saveAll(oldSiblings);
        }

        // Unter eine Seite aus einem anderen Modul gezogen? Dann wechselt der ganze Teilbaum
        // mit — eine Unterseite, die einem anderen Modul gehört als ihre Elternseite, waere in
        // keiner Modulansicht sinnvoll darstellbar.
        if (newParent != null && newParent.getCourse() != null) {
            applyCourseToSubtree(userId, note, newParent.getCourse());
        }

        return note;
    }

    /**
     * Setzt {@code orderIndex} auf die Listenposition. Fremde IDs werden still übergangen —
     * die Reihenfolge ist eine Anzeigefrage, kein Grund, den ganzen Aufruf scheitern zu lassen.
     */
    @Transactional
    public void reorderNotes(Long userId, List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;

        Map<Long, StudyNote> own = studyNoteRepository.findByUserIdOrderByOrderIndexAscIdAsc(userId)
                .stream()
                .collect(Collectors.toMap(StudyNote::getId, n -> n));

        List<StudyNote> touched = new ArrayList<>();
        for (int i = 0; i < noteIds.size(); i++) {
            StudyNote note = own.get(noteIds.get(i));
            if (note != null) {
                note.setOrderIndex(i);
                touched.add(note);
            }
        }

        studyNoteRepository.saveAll(touched);
    }

    /**
     * Löscht die Seite MIT ihrem Teilbaum (Notion-Semantik).
     *
     * Kinder nach oben zu hängen wäre die Alternative — die teleportiert Seiten an Stellen, an
     * die sie nie gehörten. Umgesetzt ohne selbstreferenzierendes {@code cascade=ALL}: einmal
     * alle Notizen laden, Eltern->Kinder aufbauen, per Breitensuche einsammeln, in einem Rutsch
     * löschen, danach je betroffenem Kurs EINMAL herunterzählen.
     */
    @Transactional
    public void deleteNote(Long userId, Long id) {
        StudyNote note = getNote(userId, id);
        deleteSubtrees(userId, List.of(note));
    }

    /**
     * Alle Notizen eines Kurses samt ihrer Teilbäume.
     *
     * Aufrufer ist {@code CourseService.deleteCourse}: {@code Course.notes} kaskadiert nicht
     * mehr, weil eine JPA-Kaskade Kinder mit verschwundener Elternseite zurücklassen würde.
     * Der Teilbaum kann über den Kurs hinausreichen — eine Unterseite darf einem anderen Kurs
     * gehören —, deshalb wird hier über den Baum gelöscht und nicht über die Kursspalte.
     */
    @Transactional
    public void deleteNotesOfCourse(Long userId, Long courseId) {
        List<StudyNote> roots = studyNoteRepository.findByUserIdAndCourseId(userId, courseId);
        if (!roots.isEmpty()) {
            deleteSubtrees(userId, roots);
        }
    }

    // ── Baum-Hilfen ──────────────────────────────────────────────────────────────

    /**
     * Die angegebenen Seiten samt allem, was darunter hängt — Breitensuche über eine einmal
     * geladene Eltern-&gt;Kinder-Abbildung. Ein bereits korrupter Zyklus laeuft nicht endlos,
     * weil jede Seite nur einmal eingesammelt wird.
     */
    private List<StudyNote> collectSubtrees(Long userId, List<StudyNote> roots) {
        List<StudyNote> all = studyNoteRepository.findByUserIdOrderByOrderIndexAscIdAsc(userId);

        Map<Long, List<StudyNote>> childrenByParent = new HashMap<>();
        for (StudyNote n : all) {
            Long parentId = n.getParent() != null ? n.getParent().getId() : null;
            if (parentId != null) {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(n);
            }
        }

        Map<Long, StudyNote> collected = new LinkedHashMap<>();
        Deque<StudyNote> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            StudyNote current = queue.poll();
            if (collected.putIfAbsent(current.getId(), current) != null) {
                continue;
            }
            queue.addAll(childrenByParent.getOrDefault(current.getId(), List.of()));
        }

        return new ArrayList<>(collected.values());
    }

    private void deleteSubtrees(Long userId, List<StudyNote> roots) {
        Map<Long, StudyNote> doomed = new LinkedHashMap<>();
        for (StudyNote n : collectSubtrees(userId, roots)) {
            doomed.put(n.getId(), n);
        }

        // Je Kurs einmal zählen, nicht je Notiz einmal aufrufen.
        Map<Long, Integer> perCourse = new HashMap<>();
        for (StudyNote n : doomed.values()) {
            if (n.getCourse() != null) {
                perCourse.merge(n.getCourse().getId(), 1, Integer::sum);
            }
        }

        // Erst die Elternverweise kappen, dann löschen: sonst stolpert das Löschen über die
        // selbstreferenzierende Fremdschlüsselbedingung, sobald eine Elternseite vor ihrem Kind
        // an der Reihe ist (siehe clearParentOf).
        //
        // deleteAll und nicht deleteAllInBatch: ein Sammel-DELETE ließe die Notizen als
        // veraltete verwaltete Objekte im Persistenzkontext stehen. Beim Löschen eines Kurses
        // zeigen die noch auf genau den Kurs, der in derselben Transaktion verschwindet — und
        // Hibernate bricht das Flushen mit einer TransientObjectException ab.
        studyNoteRepository.clearParentOf(doomed.keySet());
        doomed.values().forEach(n -> n.setParent(null));   // Kontext und Datenbank gleichziehen
        studyNoteRepository.deleteAll(doomed.values());

        perCourse.forEach((courseId, count) -> adjustNoteCount(courseId, -count));
    }

    /**
     * Der Notizzähler am Kurs.
     *
     * Wohnt hier und nicht in CourseService: dort hinge StudyNoteService an CourseService und
     * CourseService (fürs Löschen der Notizen eines Kurses) an StudyNoteService — Spring lehnt
     * diesen Zyklus beim Start ab.
     */
    private void adjustNoteCount(Long courseId, int delta) {
        courseRepository.findById(courseId).ifPresent(course -> {
            course.setTotalNotes(Math.max(0, course.getTotalNotes() + delta));
            courseRepository.save(course);
        });
    }

    /** Läuft von der Zielseite aufwärts und verbietet den Sprung in den eigenen Teilbaum. */
    private void assertNoCycle(StudyNote note, StudyNote target) {
        StudyNote cursor = target;
        for (int hops = 0; cursor != null && hops < MAX_TREE_DEPTH; hops++) {
            if (cursor.getId().equals(note.getId())) {
                throw new BadRequestException(
                        "Eine Seite kann nicht unter eine ihrer eigenen Unterseiten verschoben werden");
            }
            cursor = cursor.getParent();
        }
        if (cursor != null) {
            throw new BadRequestException("Die Seitenstruktur ist zu tief verschachtelt");
        }
    }

    private List<StudyNote> siblingsOf(Long userId, Long parentId) {
        return new ArrayList<>(parentId == null
                ? studyNoteRepository.findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(userId)
                : studyNoteRepository.findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(userId, parentId));
    }

    private static List<StudyNote> withoutNote(List<StudyNote> notes, Long id) {
        notes.removeIf(n -> n.getId().equals(id));
        return notes;
    }

    private static void renumber(List<StudyNote> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setOrderIndex(i);
        }
    }
}
