package com.Finn.everything_app.repository.projection;

import java.sql.Date;

/** Einheiten und Minuten je ISO-Woche. */
public interface WeekBucketRow {
    Date getWeekStart();

    Long getWorkouts();

    Long getMinutes();
}
