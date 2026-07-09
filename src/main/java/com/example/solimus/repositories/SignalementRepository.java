package com.example.solimus.repositories;

import com.example.solimus.entities.Signalement;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalementRepository extends JpaRepository<Signalement, Long> {

    // =========================================================================
    // CÔTÉ COPROPRIÉTAIRE
    // =========================================================================

    // Recherche paginée des signalements d'un copropriétaire, avec filtres optionnels
    // (search sur le titre, résidence, statut) — chaque filtre est ignoré s'il vaut null
    @Query("SELECT s FROM Signalement s WHERE s.owner.id = :ownerId " +
            "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:residenceId IS NULL OR s.residence.id = :residenceId) " +
            "AND (:status IS NULL OR s.status = :status)")
    Page<Signalement> searchMySignalements(
            @Param("ownerId") Long ownerId,
            @Param("search") String search,
            @Param("residenceId") Long residenceId,
            @Param("status") SignalementStatus status,
            Pageable pageable);

    // =========================================================================
    // CÔTÉ SYNDIC
    // =========================================================================

    // Compte tous les signalements des résidences d'un syndic
    long countByResidenceSyndicId(Long syndicId);

    // Compte les signalements des résidences d'un syndic ayant un statut précis
    long countByResidenceSyndicIdAndStatus(Long syndicId, SignalementStatus status);

    // Recherche paginée des signalements d'un syndic, avec filtres optionnels
    // (search sur le titre, statut, résidence) — chaque filtre est ignoré s'il vaut null
    @Query("SELECT s FROM Signalement s WHERE s.residence.syndic.id = :syndicId " +
           "AND (:search IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:residenceId IS NULL OR s.residence.id = :residenceId)")
    Page<Signalement> searchForSyndic(
            @Param("syndicId") Long syndicId,
            @Param("search") String search,
            @Param("status") SignalementStatus status,
            @Param("residenceId") Long residenceId,
            Pageable pageable);
}