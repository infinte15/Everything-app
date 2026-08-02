package com.Finn.everything_app.service;

import com.Finn.everything_app.model.ProductivityPeakTime;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor  //automatischer Konstruktor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public User registerUser(String username, String email, String password){

        if(userRepository.existsByUsername(username)){
            throw new RuntimeException("Username bereits vergeben");
        }
        if(userRepository.existsByEmail(email)){
            throw new RuntimeException("Email bereits vergeben");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        createDefaultPreferences(savedUser);
        return savedUser;
    }

    public UserPreferences createDefaultPreferences(User user){
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setWorkdayStart(LocalTime.of(8,0));
        prefs.setWorkdayEnd(LocalTime.of(22,0));
        prefs.setPeakProductivityTime(ProductivityPeakTime.MORNING);
        prefs.setBreakDurationMinutes(15);
        prefs.setHoursBeforeBreak(2);
        prefs.setMaxTasksPerDay(8);
        prefs.setNotificationsEnabled(true);
        prefs.setReminderMinutesBefore(15);
        prefs.setDarkMode(true);
        prefs.setBufferMinutes(0);
        prefs.setMaxTaskMinutesPerDay(480);
        prefs.setDefaultMinChunkMinutes(30);
        prefs.setDefaultMaxChunkMinutes(120);
        prefs.setAutoScheduleEnabled(true);

        return userPreferencesRepository.save(prefs);
    }

    public User findById(Long id){
        return userRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("User nicht gefunden"));
    }

    public User findByUsername(String username){
        return userRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("User nicht gefunden"));
    }

    @Transactional
    public void updateLastLogin(Long userId){
        User user = findById(userId);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

    public UserPreferences getUserPreferences(Long userId){
        return userPreferencesRepository.findByUserId(userId)
                .orElseThrow(()-> new RuntimeException("Preferences nicht gefunden"));
    }

    @Transactional
    public UserPreferences getOrCreatePreferences(Long userId){
        return userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(findById(userId)));
    }

    /**
     * Patcht nur die gesetzten Felder. Vorher wurde jedes Feld bedingungslos überschrieben, womit
     * ein Teil-Payload (z.B. nur die Arbeitszeiten) alle übrigen Einstellungen auf null gesetzt hat.
     */
    @Transactional
    public UserPreferences updatePreferences(Long userId, UserPreferences newPrefs) {
        // getOrCreate statt get: Nutzer ohne Preferences-Zeile sollen speichern können.
        UserPreferences existing = getOrCreatePreferences(userId);

        if (newPrefs.getWorkdayStart() != null)         existing.setWorkdayStart(newPrefs.getWorkdayStart());
        if (newPrefs.getWorkdayEnd() != null)           existing.setWorkdayEnd(newPrefs.getWorkdayEnd());
        if (newPrefs.getPeakProductivityTime() != null) existing.setPeakProductivityTime(newPrefs.getPeakProductivityTime());
        if (newPrefs.getBreakDurationMinutes() != null) existing.setBreakDurationMinutes(newPrefs.getBreakDurationMinutes());
        if (newPrefs.getHoursBeforeBreak() != null)     existing.setHoursBeforeBreak(newPrefs.getHoursBeforeBreak());
        if (newPrefs.getGroupSimilarTasks() != null)    existing.setGroupSimilarTasks(newPrefs.getGroupSimilarTasks());
        if (newPrefs.getMaxTasksPerDay() != null)       existing.setMaxTasksPerDay(newPrefs.getMaxTasksPerDay());
        if (newPrefs.getNotificationsEnabled() != null) existing.setNotificationsEnabled(newPrefs.getNotificationsEnabled());
        if (newPrefs.getReminderMinutesBefore() != null) existing.setReminderMinutesBefore(newPrefs.getReminderMinutesBefore());
        if (newPrefs.getThemeColor() != null)           existing.setThemeColor(newPrefs.getThemeColor());
        if (newPrefs.getDarkMode() != null)             existing.setDarkMode(newPrefs.getDarkMode());
        if (newPrefs.getBufferMinutes() != null)        existing.setBufferMinutes(newPrefs.getBufferMinutes());
        if (newPrefs.getMaxTaskMinutesPerDay() != null) existing.setMaxTaskMinutesPerDay(newPrefs.getMaxTaskMinutesPerDay());
        if (newPrefs.getDefaultMinChunkMinutes() != null) existing.setDefaultMinChunkMinutes(newPrefs.getDefaultMinChunkMinutes());
        if (newPrefs.getDefaultMaxChunkMinutes() != null) existing.setDefaultMaxChunkMinutes(newPrefs.getDefaultMaxChunkMinutes());
        if (newPrefs.getAutoScheduleEnabled() != null)  existing.setAutoScheduleEnabled(newPrefs.getAutoScheduleEnabled());

        UserPreferences saved = userPreferencesRepository.save(existing);
        // Geänderte Arbeitszeiten oder Puffer müssen den Kalender neu fließen lassen.
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }
}
