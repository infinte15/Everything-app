package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.BodyWeightEntryDTO;
import com.Finn.everything_app.dto.BodyWeightSeriesDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.BodyWeightEntry;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.repository.BodyWeightEntryRepository;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Koerpergewichtsverlauf.
 *
 * <p>Ein Eintrag pro Tag: ein zweites Wiegen am selben Tag ueberschreibt den Wert, statt einen
 * weiteren Punkt anzulegen. Der Verlauf ist eine Kurve ueber Tage, nicht ueber Messungen.
 */
@Service
@RequiredArgsConstructor
public class BodyWeightService {

    /** Jenseits davon ist es ein Tippfehler, kein Gewicht. */
    private static final double MIN_KG = 20;
    private static final double MAX_KG = 400;

    private final BodyWeightEntryRepository repository;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;

    @Transactional(readOnly = true)
    public BodyWeightSeriesDTO getSeries(Long userId, LocalDate from) {
        List<BodyWeightEntry> window = from == null
                ? repository.findByUserIdOrderByDateAsc(userId)
                : repository.findByUserIdAndDateGreaterThanEqualOrderByDateAsc(userId, from);

        BodyWeightSeriesDTO dto = new BodyWeightSeriesDTO();
        dto.setEntries(window.stream().map(BodyWeightService::toDTO).toList());

        // Bewusst ueber die volle Historie, nicht ueber das Fenster: die grosse Zahl auf dem
        // Startbildschirm soll das zuletzt Gewogene zeigen, auch wenn das laenger her ist als
        // der dargestellte Zeitraum. Sonst stuende dort nichts, obwohl es einen Wert gibt.
        List<BodyWeightEntry> all = from == null ? window : repository.findByUserIdOrderByDateAsc(userId);
        if (!all.isEmpty()) {
            dto.setLatest(toDTO(all.get(all.size() - 1)));
            if (all.size() > 1) {
                dto.setPrevious(toDTO(all.get(all.size() - 2)));
            }
        }

        dto.setTargetWeightKg(preferencesRepository.findByUserId(userId)
                .map(UserPreferences::getTargetWeightKg)
                .orElse(null));
        return dto;
    }

    /** Legt den Eintrag des Tages an oder aktualisiert ihn. */
    @Transactional
    public BodyWeightEntryDTO log(Long userId, BodyWeightEntryDTO request) {
        double weight = request.getWeightKg() == null ? 0 : request.getWeightKg();
        if (weight < MIN_KG || weight > MAX_KG) {
            throw new BadRequestException(
                    "Gewicht muss zwischen " + (int) MIN_KG + " und " + (int) MAX_KG + " kg liegen");
        }
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Ein Gewicht in der Zukunft lässt sich nicht wiegen");
        }

        BodyWeightEntry entry = repository.findByUserIdAndDate(userId, date)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));
                    BodyWeightEntry fresh = new BodyWeightEntry();
                    fresh.setUser(user);
                    fresh.setDate(date);
                    return fresh;
                });
        entry.setWeightKg(weight);
        entry.setNote(request.getNote());
        return toDTO(repository.save(entry));
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        BodyWeightEntry entry = repository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Eintrag nicht gefunden"));
        repository.delete(entry);
    }

    /**
     * Setzt das Zielgewicht, oder entfernt es mit {@code null}.
     *
     * <p>Eigener Weg statt {@code updatePreferences}, weil dort ein null nicht von einem nicht
     * mitgeschickten Feld zu unterscheiden waere - siehe {@link UserPreferences#getTargetWeightKg()}.
     */
    @Transactional
    public Double setTarget(Long userId, Double targetKg) {
        if (targetKg != null && (targetKg < MIN_KG || targetKg > MAX_KG)) {
            throw new BadRequestException(
                    "Zielgewicht muss zwischen " + (int) MIN_KG + " und " + (int) MAX_KG + " kg liegen");
        }
        UserPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Einstellungen nicht gefunden"));
        prefs.setTargetWeightKg(targetKg);
        preferencesRepository.save(prefs);
        return targetKg;
    }

    @Transactional(readOnly = true)
    public Optional<BodyWeightEntry> latest(Long userId) {
        return repository.findFirstByUserIdOrderByDateDesc(userId);
    }

    private static BodyWeightEntryDTO toDTO(BodyWeightEntry entry) {
        return new BodyWeightEntryDTO(
                entry.getId(), entry.getDate(), entry.getWeightKg(), entry.getNote());
    }
}
