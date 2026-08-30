package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.EquipmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentProfileRepository extends JpaRepository<EquipmentProfile, Long> {

    List<EquipmentProfile> findByUserIdOrderByNameAsc(Long userId);

    Optional<EquipmentProfile> findByIdAndUserId(Long id, Long userId);

    Optional<EquipmentProfile> findByUserIdAndIsActiveTrue(Long userId);
}
