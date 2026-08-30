package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.EquipmentProfileDTO;
import com.Finn.everything_app.model.EquipmentProfile;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.EquipmentProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** Ausruestungsprofile: welche Geraete gerade verfuegbar sind. */
@RestController
@RequestMapping("/api/sports/equipment-profiles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EquipmentProfileController {

    private final EquipmentProfileService service;

    @GetMapping
    public ResponseEntity<List<EquipmentProfileDTO>> getProfiles(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getProfiles(userId).stream().map(this::toDTO).toList());
    }

    @PostMapping
    public ResponseEntity<EquipmentProfileDTO> create(
            @CurrentUser Long userId,
            @Valid @RequestBody EquipmentProfileDTO request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDTO(service.save(userId, null, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentProfileDTO> update(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EquipmentProfileDTO request) {

        return ResponseEntity.ok(toDTO(service.save(userId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Aktiviert ein Profil. {@code id = 0} schaltet die Filterung ab - ein eigener
     * Endpunkt dafuer waere ein zweiter Weg fuer dieselbe Entscheidung.
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@CurrentUser Long userId, @PathVariable Long id) {
        service.activate(userId, id == 0 ? null : id);
        return ResponseEntity.noContent().build();
    }

    private EquipmentProfileDTO toDTO(EquipmentProfile profile) {
        EquipmentProfileDTO dto = new EquipmentProfileDTO();
        dto.setId(profile.getId());
        dto.setName(profile.getName());
        dto.setActive(Boolean.TRUE.equals(profile.getIsActive()));
        dto.setEquipment(new ArrayList<>(profile.getEquipment()));
        return dto;
    }
}
