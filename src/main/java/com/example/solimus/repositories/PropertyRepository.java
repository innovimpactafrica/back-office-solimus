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

    // Lister les biens d'un propriétaire dans une résidence spécifique
    List<Property> findByOwnerIdAndResidenceId(Long ownerId, Long residenceId);

    // Vérifier si un propriétaire a au moins un bien dans une résidence
    boolean existsByOwnerIdAndResidenceId(Long ownerId, Long residenceId);

    // Vérifier qu'un bien précis appartient à un propriétaire dans une résidence donnée
    boolean existsByIdAndOwnerIdAndResidenceId(Long id, Long ownerId, Long residenceId);

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

    // Lister les biens occupés des résidences d'un syndic (pour lister les copropriétaires)
    List<Property> findByResidence_SyndicIdAndOwnerIsNotNull(Long syndicId);
}
