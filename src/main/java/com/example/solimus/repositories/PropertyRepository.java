package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // Vérifier si un bien avec cette référence existe déjà dans une résidence donnée
    boolean existsByReferenceAndResidenceId(String reference, Long residenceId);

    // Lister les biens d'une résidence
    java.util.List<Property> findByResidenceId(Long residenceId);

    // Lister les biens d'une résidence qui ont un propriétaire
    java.util.List<Property> findByResidenceIdAndOwnerIsNotNull(Long residenceId);

    /**
     * Trouve tous les biens d'un propriétaire donné.
     */
    java.util.List<Property> findAllByOwnerId(Long ownerId);
}
