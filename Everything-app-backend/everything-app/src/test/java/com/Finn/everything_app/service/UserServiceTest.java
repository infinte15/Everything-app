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

    @InjectMocks
    UserService service;

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
