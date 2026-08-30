package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.ProgressionPolicy;
import com.Finn.everything_app.model.SetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bringt die CHECK-Bedingungen der Enum-Spalten auf den Stand des Java-Enums.
 *
 * <p><b>Warum es das braucht.</b> Hibernate legt fuer {@code @Enumerated(EnumType.STRING)} eine
 * CHECK-Bedingung mit allen damals bekannten Konstanten an - aber nur beim <em>Erzeugen</em> der
 * Tabelle. {@code ddl-auto=update} ergaenzt spaeter fehlende Spalten und Tabellen, fasst
 * bestehende Bedingungen jedoch nie wieder an. Eine neue Enum-Konstante kompiliert dann
 * sauber, die Tests laufen (H2 baut das Schema pro Lauf neu), und erst der erste Schreibzugriff
 * auf der gewachsenen Datenbank scheitert mit
 * {@code violates check constraint "..._muscle_check"}.
 *
 * <p>Genau das ist beim Wechsel auf den ExerciseDB-Katalog passiert: {@link MuscleGroup#CARDIO}
 * kam dazu, und der Seeder brach beim ersten Ausdauer-Datensatz ab.
 *
 * <p>Der Angleicher laeuft vor jedem Seeder und ist idempotent: enthaelt die vorhandene
 * Bedingung bereits jede Konstante, passiert nichts. Nur auf PostgreSQL - unter H2 baut
 * {@code create-drop} das Schema ohnehin frisch aus dem aktuellen Enum.
 */
@Component
@RequiredArgsConstructor
@Slf4j
// Vor dem Katalog-Seeder (@Order(0)): der schreibt als Erster in diese Tabellen.
@Order(-1)
public class EnumCheckConstraintSync implements ApplicationRunner {

    /** Eine Enum-Spalte, deren CHECK-Bedingung mitwachsen muss. */
    private record EnumColumn(String table, String column, List<String> values) {

        static EnumColumn of(String table, String column, Class<? extends Enum<?>> type) {
            return new EnumColumn(table, column,
                    Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
        }
    }

    private static final List<EnumColumn> COLUMNS = List.of(
            EnumColumn.of("exercise_primary_muscles", "muscle", MuscleGroup.class),
            EnumColumn.of("exercise_secondary_muscles", "muscle", MuscleGroup.class),
            EnumColumn.of("exercise_sets", "set_type", SetType.class),
            EnumColumn.of("routine_exercises", "progression_policy", ProgressionPolicy.class));

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        for (EnumColumn column : COLUMNS) {
            try {
                sync(column);
            } catch (Exception e) {
                // Ein fehlgeschlagener Angleich darf den Start nicht verhindern: die Anwendung
                // laeuft mit der alten Bedingung weiter und faellt erst beim Schreiben eines
                // neuen Wertes auf - mit dieser Warnung im Log davor.
                log.warn("CHECK-Bedingung für {}.{} konnte nicht angeglichen werden: {}",
                        column.table(), column.column(), e.getMessage());
            }
        }
    }

    private boolean isPostgres() {
        try {
            String product = jdbc.execute(
                    (Connection c) -> c.getMetaData().getDatabaseProductName());
            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    private void sync(EnumColumn column) {
        // pg_get_constraintdef liefert den Bedingungstext, an dem sich ablesen laesst, ob eine
        // Konstante fehlt - ohne ihn muesste blind gedroppt und neu angelegt werden.
        List<String[]> constraints = jdbc.query("""
                select con.conname, pg_get_constraintdef(con.oid)
                from pg_constraint con
                join pg_class rel on rel.oid = con.conrelid
                join pg_namespace ns on ns.oid = rel.relnamespace
                where con.contype = 'c'
                  and rel.relname = ?
                  and ns.nspname = current_schema()
                  and pg_get_constraintdef(con.oid) like ?
                """,
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)},
                column.table(), "%" + column.column() + "%");

        if (constraints.isEmpty()) {
            // Keine Bedingung vorhanden (Tabelle fehlt oder Hibernate hat keine angelegt) -
            // dann gibt es auch nichts anzugleichen. Eine hier neu erfundene Bedingung waere
            // strenger als das, was die Anwendung bisher zugelassen hat.
            return;
        }

        boolean complete = constraints.stream().allMatch(
                c -> column.values().stream().allMatch(v -> c[1].contains("'" + v + "'")));
        if (complete) {
            return;
        }

        String literals = column.values().stream()
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(", "));
        String name = column.table() + "_" + column.column() + "_check";

        for (String[] constraint : constraints) {
            jdbc.execute("alter table " + column.table()
                    + " drop constraint \"" + constraint[0] + "\"");
        }
        jdbc.execute("alter table " + column.table()
                + " add constraint \"" + name + "\" check (" + column.column()
                + " in (" + literals + "))");

        log.info("CHECK-Bedingung {}.{} auf {} Enum-Werte angeglichen",
                column.table(), column.column(), column.values().size());
    }
}
