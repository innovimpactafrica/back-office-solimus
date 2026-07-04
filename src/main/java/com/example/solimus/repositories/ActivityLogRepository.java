package com.example.solimus.repositories;

import com.example.solimus.entities.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Utilisé par l'endpoint de lecture du panneau Activité Récente (prompt séparé, à venir).
    Page<ActivityLog> findByResidenceIdOrderByCreatedAtDesc(Long residenceId, Pageable pageable);

    // Filtrer par résidence et type d'entité liée (ex: "INTERVENTION" pour scope=interventions)
    @Query("SELECT a FROM ActivityLog a WHERE a.residence.id = :residenceId " +
           "AND (:relatedEntityType IS NULL OR a.relatedEntityType = :relatedEntityType) " +
           "ORDER BY a.createdAt DESC")
    Page<ActivityLog> findByResidenceIdAndRelatedEntityTypeOrderByCreatedAtDesc(
            @Param("residenceId") Long residenceId,
            @Param("relatedEntityType") String relatedEntityType,
            Pageable pageable);
}
