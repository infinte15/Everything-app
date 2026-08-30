package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.EquipmentProfileDTO;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.EquipmentProfile;
import com.Finn.everything_app.repository.EquipmentProfileRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ausruestungsprofile: welche Geraete gerade verfuegbar sind.
 *
 * <p>Hoechstens eines ist aktiv. Das wird hier durchgesetzt und nicht ueber eine
 * Datenbank-Bedingung - ein partieller Unique-Index laesst sich mit {@code ddl-auto=update}
 * nicht anlegen, und ein zweites aktives Profil waere ein stiller Fehler.
 */
@Service
@RequiredArgsConstructor
public class EquipmentProfileService {

    private final EquipmentProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<EquipmentProfile> getProfiles(Long userId) {
        return profileRepository.findByUserIdOrderByNameAsc(userId);
    }

    /**
     * Geraete des aktiven Profils, oder leer wenn keines aktiv ist bzw. das aktive Profil
     * keine Einschraenkung nennt. Leer heisst ueberall "nicht filtern".
     */
    @Transactional(readOnly = true)
    public Set<String> activeEquipment(Long userId) {
        return profileRepository.findByUserIdAndIsActiveTrue(userId)
                .map(EquipmentProfile::getEquipment)
                .map(Set::copyOf)
                .orElseGet(Set::of);
    }

    @Transactional
    public EquipmentProfile save(Long userId, Long id, EquipmentProfileDTO dto) {
        EquipmentProfile profile = id == null
                ? newProfile(userId)
                : profileRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Profil nicht gefunden"));

        profile.setName(dto.getName().strip());
        profile.setEquipment(new LinkedHashSet<>(
                Optional.ofNullable(dto.getEquipment()).orElseGet(List::of)));
        return profileRepository.save(profile);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        EquipmentProfile profile = profileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil nicht gefunden"));
        profileRepository.delete(profile);
    }

    /**
     * Aktiviert ein Profil und deaktiviert alle anderen. {@code null} schaltet die
     * Filterung ganz ab.
     */
    @Transactional
    public void activate(Long userId, Long id) {
        List<EquipmentProfile> all = profileRepository.findByUserIdOrderByNameAsc(userId);
        boolean found = id == null;
        for (EquipmentProfile profile : all) {
            boolean active = id != null && profile.getId().equals(id);
            if (active) found = true;
            profile.setIsActive(active);
        }
        if (!found) {
            throw new ResourceNotFoundException("Profil nicht gefunden");
        }
        profileRepository.saveAll(all);
    }

    private EquipmentProfile newProfile(Long userId) {
        EquipmentProfile profile = new EquipmentProfile();
        profile.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzer nicht gefunden")));
        return profile;
    }
}
