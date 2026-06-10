package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // Lister les biens d'une résidence
    List<Property> findByResidenceId(Long residenceId);

    // Compter les biens d'une résidence
    long countByResidenceId(Long residenceId);

    // Lister les biens d'une résidence qui ont un propriétaire
    List<Property> findByResidenceIdAndOwnerIsNotNull(Long residenceId);

    // Lister tous les biens d'un propriétaire donné
    List<Property> findAllByOwnerId(Long ownerId);

    // Compter les biens d'un propriétaire donné
    long countByOwnerId(Long ownerId);
}
