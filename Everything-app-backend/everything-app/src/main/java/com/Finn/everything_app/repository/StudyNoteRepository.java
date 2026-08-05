package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.StudyNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyNoteRepository extends JpaRepository<StudyNote, Long> {

    // Einziges Eingangstor für Einzelzugriffe: die userId gehört in die Query, nicht in eine
    // nachgelagerte Prüfung. Ohne das liefert findById fremde Notizen aus.
    Optional<StudyNote> findByIdAndUserId(Long id, Long userId);

    // Alle Notizen
    // @EntityGraph, weil StudyNoteMapper course.getName() anfasst. Im Betrieb rettet das
    // open-in-view (nicht gesetzt => true), in Tests ist es false und es gäbe eine
    // LazyInitializationException. Nebeneffekt: kein N+1 mehr auf der Liste.
    @EntityGraph(attributePaths = "course")
    List<StudyNote> findByUserId(Long userId);

    // Notizen nach Kurs
    @EntityGraph(attributePaths = "course")
    List<StudyNote> findByUserIdAndCourseId(Long userId, Long courseId);

    // --- Seitenbaum. Der Baum wird clientseitig gebaut, es gibt bewusst keinen Baum-Endpunkt:
    // GET /notes liefert ohnehin alles, und zwei Darstellungen derselben Hierarchie wären
    // zwangsläufig irgendwann uneinig. ---

    // Grundlage für Teilbaum-Löschung und Zyklusprüfung: einmal alles laden, dann in Java
    // Eltern->Kinder aufbauen. Ein rekursives SQL wäre hier die teurere Antwort.
    @EntityGraph(attributePaths = "course")
    List<StudyNote> findByUserIdOrderByOrderIndexAscIdAsc(Long userId);

    // Geschwister — zum Neunummerieren beim Verschieben. Zwei Methoden, weil "parent_id IS NULL"
    // in JPQL nicht über einen Parameter geht.
    List<StudyNote> findByUserIdAndParentIdOrderByOrderIndexAscIdAsc(Long userId, Long parentId);

    List<StudyNote> findByUserIdAndParentIsNullOrderByOrderIndexAscIdAsc(Long userId);

    /**
     * Kappt die Elternverweise, bevor ein Teilbaum gelöscht wird.
     *
     * Ohne das scheitert ein Sammel-DELETE auf H2 an der selbstreferenzierenden
     * Fremdschlüsselbedingung — H2 prüft je Zeile, Postgres erst am Ende der Anweisung. Zwei
     * Anweisungen statt einer, dafür auf beiden Datenbanken dasselbe Verhalten.
     */
    // @Transactional am Repository, weil eine schreibende Query eine Transaktion braucht und
    // diese hier auch außerhalb eines Service aufgerufen wird (Aufräumen in Tests).
    @Modifying
    @Transactional
    @Query("UPDATE StudyNote n SET n.parent = null WHERE n.id IN :ids")
    void clearParentOf(@Param("ids") Collection<Long> ids);

    List<StudyNote> findByUserIdAndTitleContainingOrContentContaining(
            Long userId,
            String titleQuery,
            String contentQuery
    );


    // Notizen mit Dateien
    @Query("SELECT n FROM StudyNote n WHERE n.user.id = :userId AND n.filePath IS NOT NULL")
    List<StudyNote> findNotesWithFiles(@Param("userId") Long userId);

    // Neueste Notizen
    List<StudyNote> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Kein findByUserIdAndTag mehr: das JOIN n.tags joint ein String-Attribut und hätte beim
    // nächsten Startup die Query-Validierung gerissen. searchNotes deckt Tags per LIKE ab.

    // Notizen pro Kurs
    @Query("SELECT COUNT(n) FROM StudyNote n WHERE n.course.id = :courseId")
    Long countByCourseId(@Param("courseId") Long courseId);

    List<StudyNote> findByUserIdAndIsFavoriteTrue(Long userId);

    List<StudyNote> findByUserIdAndLastReviewedAtIsNotNullOrderByLastReviewedAtDesc(Long userId);

    List<StudyNote> findByUserIdAndLastReviewedAtIsNull(Long userId);

    List<StudyNote> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime date);

    Long countByUserIdAndCourseId(Long userId, Long courseId);

    List<StudyNote> findByUserIdAndCategory(Long userId, String category);

    @EntityGraph(attributePaths = "course")
    @Query("SELECT n FROM StudyNote n WHERE n.user.id = :userId AND " +
            "(n.title LIKE %:query% OR n.content LIKE %:query% OR n.tags LIKE %:query%)")
    List<StudyNote> searchNotes(@Param("userId") Long userId, @Param("query") String query);
}

