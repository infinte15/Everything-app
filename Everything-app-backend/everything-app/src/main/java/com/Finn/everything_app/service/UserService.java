package com.Finn.everything_app.service;

import com.Finn.everything_app.model.ProductivityPeakTime;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
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

        return userRepository.save(user);
    }

    public void createDefaultPreferences(User user){
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

        userPreferencesRepository.save(prefs);
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
    public UserPreferences updatePreferences(Long userId, UserPreferences newPrefs) {
        UserPreferences existing = getUserPreferences(userId);

        existing.setWorkdayStart(newPrefs.getWorkdayStart());
        existing.setWorkdayEnd(newPrefs.getWorkdayEnd());
        existing.setPeakProductivityTime(newPrefs.getPeakProductivityTime());
        existing.setBreakDurationMinutes(newPrefs.getBreakDurationMinutes());
        existing.setHoursBeforeBreak(newPrefs.getHoursBeforeBreak());
        existing.setGroupSimilarTasks(newPrefs.getGroupSimilarTasks());
        existing.setMaxTasksPerDay(newPrefs.getMaxTasksPerDay());
        existing.setNotificationsEnabled(newPrefs.getNotificationsEnabled());
        existing.setReminderMinutesBefore(newPrefs.getReminderMinutesBefore());
        existing.setThemeColor(newPrefs.getThemeColor());
        existing.setDarkMode(newPrefs.getDarkMode());

        return userPreferencesRepository.save(existing);
    }
}
