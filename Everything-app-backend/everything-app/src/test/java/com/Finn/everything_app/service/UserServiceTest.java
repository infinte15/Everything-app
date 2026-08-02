package com.Finn.everything_app.service;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserPreferencesRepository userPreferencesRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    UserService service;

    // Sichert den Fix gegen das Null-Clobbering ab: früher hat updatePreferences jedes Feld
    // bedingungslos überschrieben, ein Teil-Payload hat damit alles andere gelöscht.
    @Test
    void updatePreferencesKeepsFieldsThatWereNotSent() {
        UserPreferences existing = new UserPreferences();
        existing.setWorkdayStart(java.time.LocalTime.of(8, 0));
        existing.setWorkdayEnd(java.time.LocalTime.of(22, 0));
        existing.setMaxTasksPerDay(8);
        existing.setDarkMode(true);
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(userPreferencesRepository.save(any(UserPreferences.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserPreferences patch = new UserPreferences();
        patch.setWorkdayEnd(java.time.LocalTime.of(18, 0));   // nur dieses eine Feld

        UserPreferences saved = service.updatePreferences(1L, patch);

        assertEquals(java.time.LocalTime.of(18, 0), saved.getWorkdayEnd(), "geändertes Feld");
        assertEquals(java.time.LocalTime.of(8, 0), saved.getWorkdayStart(), "darf nicht gelöscht werden");
        assertEquals(8, saved.getMaxTasksPerDay(), "darf nicht gelöscht werden");
        assertEquals(true, saved.getDarkMode(), "darf nicht gelöscht werden");
    }

    // Guards the bug fix: a user without a UserPreferences row must not blow up the scheduler —
    // getOrCreatePreferences must self-heal instead of throwing like getUserPreferences does.
    @Test
    void getOrCreatePreferencesSelfHealsWhenMissing() {
        User user = new User();
        user.setId(1L);

        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPreferencesRepository.save(any(UserPreferences.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserPreferences result = assertDoesNotThrow(() -> service.getOrCreatePreferences(1L));

        assertNotNull(result);
        assertEquals(user, result.getUser());
        verify(userPreferencesRepository).save(any(UserPreferences.class));
    }

    @Test
    void getOrCreatePreferencesReturnsExistingWithoutCreating() {
        UserPreferences existing = new UserPreferences();
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        UserPreferences result = service.getOrCreatePreferences(1L);

        assertSame(existing, result);
        verify(userPreferencesRepository, never()).save(any());
    }
}
