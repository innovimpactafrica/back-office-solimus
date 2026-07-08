package com.example.solimus.repositories;

import com.example.solimus.entities.Signalement;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalementRepository extends JpaRepository<Signalement, Long> {

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
}