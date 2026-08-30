package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.UserPreferencesDTO;
import com.Finn.everything_app.model.UserPreferences;
import org.springframework.stereotype.Component;

@Component
public class UserPreferencesMapper {

    public UserPreferencesDTO toDTO(UserPreferences prefs) {
        if (prefs == null) return null;

        UserPreferencesDTO dto = new UserPreferencesDTO();
        dto.setId(prefs.getId());
        dto.setWorkdayStart(prefs.getWorkdayStart());
        dto.setWorkdayEnd(prefs.getWorkdayEnd());
        dto.setPeakProductivityTime(prefs.getPeakProductivityTime());
        dto.setBreakDurationMinutes(prefs.getBreakDurationMinutes());
        dto.setMaxTasksPerDay(prefs.getMaxTasksPerDay());
        dto.setNotificationsEnabled(prefs.getNotificationsEnabled());
        dto.setReminderMinutesBefore(prefs.getReminderMinutesBefore());
        dto.setThemeColor(prefs.getThemeColor());
        dto.setDarkMode(prefs.getDarkMode());
        dto.setBufferMinutes(prefs.getBufferMinutes());
        dto.setMaxTaskMinutesPerDay(prefs.getMaxTaskMinutesPerDay());
        dto.setMaxScheduledMinutesPerDay(prefs.getMaxScheduledMinutesPerDay());
        dto.setCoreHoursEnd(prefs.getCoreHoursEnd());
        dto.setPersonalHoursStart(prefs.getPersonalHoursStart());
        dto.setPersonalHoursEnd(prefs.getPersonalHoursEnd());
        dto.setDefaultMinChunkMinutes(prefs.getDefaultMinChunkMinutes());
        dto.setDefaultMaxChunkMinutes(prefs.getDefaultMaxChunkMinutes());
        dto.setDeadlineBufferHours(prefs.getDeadlineBufferHours());
        dto.setAutoScheduleEnabled(prefs.getAutoScheduleEnabled());
        dto.setTargetWeightKg(prefs.getTargetWeightKg());
        return dto;
    }

    /**
     * Baut eine reine Träger-Entity aus dem DTO. Nicht gesetzte Felder bleiben null — der
     * Service patcht nur die nicht-null-Felder, damit ein Teil-Payload nicht den Rest löscht.
     */
    public UserPreferences toEntity(UserPreferencesDTO dto) {
        if (dto == null) return null;

        UserPreferences prefs = new UserPreferences();
        prefs.setWorkdayStart(dto.getWorkdayStart());
        prefs.setWorkdayEnd(dto.getWorkdayEnd());
        prefs.setPeakProductivityTime(dto.getPeakProductivityTime());
        prefs.setBreakDurationMinutes(dto.getBreakDurationMinutes());
        prefs.setMaxTasksPerDay(dto.getMaxTasksPerDay());
        prefs.setNotificationsEnabled(dto.getNotificationsEnabled());
        prefs.setReminderMinutesBefore(dto.getReminderMinutesBefore());
        prefs.setThemeColor(dto.getThemeColor());
        prefs.setDarkMode(dto.getDarkMode());
        prefs.setBufferMinutes(dto.getBufferMinutes());
        prefs.setMaxTaskMinutesPerDay(dto.getMaxTaskMinutesPerDay());
        prefs.setMaxScheduledMinutesPerDay(dto.getMaxScheduledMinutesPerDay());
        prefs.setCoreHoursEnd(dto.getCoreHoursEnd());
        prefs.setPersonalHoursStart(dto.getPersonalHoursStart());
        prefs.setPersonalHoursEnd(dto.getPersonalHoursEnd());
        prefs.setDefaultMinChunkMinutes(dto.getDefaultMinChunkMinutes());
        prefs.setDefaultMaxChunkMinutes(dto.getDefaultMaxChunkMinutes());
        prefs.setDeadlineBufferHours(dto.getDeadlineBufferHours());
        prefs.setAutoScheduleEnabled(dto.getAutoScheduleEnabled());
        prefs.setTargetWeightKg(dto.getTargetWeightKg());
        return prefs;
    }
}
