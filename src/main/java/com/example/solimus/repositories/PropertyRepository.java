package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import com.example.solimus.enums.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // Lister les biens d'une résidence
    List<Property> findByResidenceId(Long residenceId);

    // Compter les biens d'une résidence
    long countByResidenceId(Long residenceId);

    // Lister les biens d'une résidence qui ont un propriétaire
    List<Property> findByResidenceIdAndOwnerIsNotNull(Long residenceId);

    // Lister les biens d'une résidence par statut
    List<Property> findByResidenceIdAndStatus(Long residenceId, PropertyStatus status);

    // Lister tous les biens d'un propriétaire donné
    List<Property> findAllByOwnerId(Long ownerId);

    // Trouver le premier bien d'un propriétaire donné
    Optional<Property> findFirstByOwnerId(Long ownerId);

    // Compter les biens d'un propriétaire donné
    long countByOwnerId(Long ownerId);
}
