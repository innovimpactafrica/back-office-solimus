package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import com.example.solimus.enums.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // Lister les biens d'une résidence
    List<Property> findByResidenceId(Long residenceId);

    // Lister les biens d'une résidence (paginé)
    Page<Property> findByResidenceId(Long residenceId, Pageable pageable);

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

    // Compter les propriétaires distincts d'une résidence
    @Query("SELECT COUNT(DISTINCT p.owner) FROM Property p WHERE p.residence.id = :residenceId AND p.owner IS NOT NULL")
    long countDistinctOwnersByResidenceId(@Param("residenceId") Long residenceId);

    // Compter les biens d'un syndic (toutes résidences confondues)
    @Query("SELECT COUNT(p) FROM Property p WHERE p.residence.syndic.id = :syndicId")
    long countByResidenceSyndicId(@Param("syndicId") Long syndicId);

    // Lister les biens d'une résidence avec filtres (paginé)
    // search : reference du lot OU nom du owner, LIKE insensible casse
    // floor : exact match
    @Query("SELECT p FROM Property p WHERE p.residence.id = :residenceId " +
           "AND (:search IS NULL OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.owner.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.owner.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:floor IS NULL OR p.floor = :floor) " +
           "ORDER BY p.reference ASC")
    Page<Property> findByResidenceIdWithFilters(
            @Param("residenceId") Long residenceId,
            @Param("search") String search,
            @Param("floor") Integer floor,
            Pageable pageable);
}
