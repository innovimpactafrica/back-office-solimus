package com.example.solimus.repositories;

import com.example.solimus.entities.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Utilisé par l'endpoint de lecture du panneau Activité Récente (prompt séparé, à venir).
    @Query("SELECT a FROM ActivityLog a JOIN FETCH a.residence WHERE a.residence.id = :residenceId " +
           "ORDER BY a.createdAt DESC")
    Page<ActivityLog> findByResidenceIdOrderByCreatedAtDesc(@Param("residenceId") Long residenceId, Pageable pageable);

    // Filtrer par résidence et type d'entité liée (ex: "INTERVENTION" pour scope=interventions)
    @Query("SELECT a FROM ActivityLog a WHERE a.residence.id = :residenceId " +
           "AND (:relatedEntityType IS NULL OR a.relatedEntityType = :relatedEntityType) " +
           "ORDER BY a.createdAt DESC")
    Page<ActivityLog> findByResidenceIdAndRelatedEntityTypeOrderByCreatedAtDesc(
            @Param("residenceId") Long residenceId,
            @Param("relatedEntityType") String relatedEntityType,
            Pageable pageable);

    // Filtrer par type d'entité liée et ID de l'entité (ex: pour l'historique d'une AG précise)
    List<ActivityLog> findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            String relatedEntityType,
            Long relatedEntityId);

    // Version paginée
    Page<ActivityLog> findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            String relatedEntityType,
            Long relatedEntityId,
            Pageable pageable);

    // =========================================================================
    // ACTIVITÉ RÉCENTE COPROPRIÉTAIRE — RÈGLE HYBRIDE
    // =========================================================================

    // Logs où le copropriétaire est l'acteur direct
    List<ActivityLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    // Logs de type CHARGE_CALL_GENERATED pour une liste de résidences
    @Query("SELECT a FROM ActivityLog a WHERE a.type = 'CHARGE_CALL_GENERATED' " +
           "AND a.residence.id IN :residenceIds " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findChargeCallGeneratedByResidenceIdsOrderByCreatedAtDesc(
            @Param("residenceIds") List<Long> residenceIds);

    // Logs de type MEETING_DOCUMENT_ADDED pour une liste d'AGs
    @Query("SELECT a FROM ActivityLog a WHERE a.type = 'MEETING_DOCUMENT_ADDED' " +
           "AND a.relatedEntityId IN :meetingIds " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findMeetingDocumentAddedByMeetingIdsOrderByCreatedAtDesc(
            @Param("meetingIds") List<Long> meetingIds);

    // ===== MÉTHODES SUPPLÉMENTAIRES =====

    /**
     * Lister les logs d'activité des résidences d'un syndic
     */
    @Query("SELECT a FROM ActivityLog a JOIN FETCH a.residence WHERE a.residence.syndic.id = :syndicId " +
           "ORDER BY a.createdAt DESC")
    List<ActivityLog> findByResidenceSyndicIdOrderByCreatedAtDesc(@Param("syndicId") Long syndicId, Pageable pageable);
}
