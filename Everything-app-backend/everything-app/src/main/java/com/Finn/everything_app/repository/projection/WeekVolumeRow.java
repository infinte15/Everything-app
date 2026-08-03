package com.Finn.everything_app.repository.projection;

import java.sql.Date;

/** Volumen und Satzanzahl je ISO-Woche. */
public interface WeekVolumeRow {
    Date getWeekStart();

    Double getVolume();

    Long getSetCount();
}
