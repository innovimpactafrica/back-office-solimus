package com.example.solimus.repositories;


import com.example.solimus.dtos.syndic.meeting.MeetingSummaryDTO;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.Residence;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.repositories.meeting.MeetingDocumentCount;
import com.example.solimus.repositories.meeting.MeetingParticipationStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByResidenceId(Long residenceId);

    Page<Meeting> findByResidence(Residence residence, org.springframework.data.domain.Pageable pageable);

    List<Meeting> findByResidenceIdAndStatus(Long residenceId, MeetingStatus status);

    List<Meeting> findByResidenceIdAndMeetingDateAfterOrderByMeetingDateAsc(
        Long residenceId, LocalDateTime date);

    List<Meeting> findByResidenceOrderByMeetingDateAsc(Residence residence);

    List<Meeting> findByResidenceAndMeetingDateBetween(Residence residence,
                                                        LocalDate start,
                                                        LocalDate end);

    /**
     * Trouve les 5 prochaines réunions UPCOMING pour une résidence.
     */
    List<Meeting> findTop5ByResidenceIdAndStatusOrderByMeetingDateAsc(
            Long residenceId, MeetingStatus status);

    /**
     * Trouve les 2 prochaines réunions UPCOMING pour une résidence.
     */
    List<Meeting> findTop2ByResidenceIdAndStatusOrderByMeetingDateAsc(
            Long residenceId, MeetingStatus status);

    // =========================================================================
    // DASHBOARD SYNDIC — AG
    // =========================================================================

    // Compter les AG d'un syndic
    long countBySyndicId(Long syndicId);

    // Compter les AG d'un syndic par statut
    long countBySyndicIdAndStatus(Long syndicId, MeetingStatus status);

    // Lister les AG d'un syndic par statut
    List<Meeting> findBySyndicIdAndStatus(Long syndicId, MeetingStatus status);

    // Nombre d'AG du syndic pour plusieurs statuts a la fois (ex: UPCOMING + IN_PROGRESS)
    long countBySyndicIdAndStatusIn(Long syndicId, List<MeetingStatus> statuses);

    // Compter les AG terminées d'un syndic dans l'année en cours
    @Query("SELECT COUNT(m) FROM Meeting m WHERE m.syndic.id = :syndicId " +
           "AND m.status = :status " +
           "AND YEAR(m.meetingDate) = YEAR(CURRENT_DATE)")
    long countBySyndicIdAndStatusAndCurrentYear(
            @Param("syndicId") Long syndicId,
            @Param("status") MeetingStatus status);

    // Trouver les AG d'un syndic avec filtres (search + status), paginé
    @Query("SELECT m FROM Meeting m WHERE m.syndic.id = :syndicId " +
           "AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "ORDER BY m.createdAt DESC")
    Page<Meeting> findBySyndicIdWithFilters(
            @Param("syndicId") Long syndicId,
            @Param("search") String search,
            @Param("status") MeetingStatus status,
            Pageable pageable);

    // =========================================================================
    // DASHBOARD SYNDIC — AG (NOUVELLES METHODES)
    // =========================================================================

    // ===== RECHERCHE PAGINEE AVEC FILTRES (status + recherche par titre, tous deux optionnels) =====
    @Query("SELECT m FROM Meeting m " +
           "WHERE m.residence.syndic.id = :syndicId " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY m.meetingDate DESC")
    Page<Meeting> searchMeetings(@Param("syndicId") Long syndicId,
                                  @Param("status") MeetingStatus status,
                                  @Param("search") String search,
                                  Pageable pageable);

    // ===== COMPTEURS KPI (non filtres, portee = tout le syndic) =====
    long countByResidence_Syndic_Id(Long syndicId);

    long countByResidence_Syndic_IdAndStatus(Long syndicId, MeetingStatus status);

    long countByResidence_Syndic_IdAndStatusIn(Long syndicId, List<MeetingStatus> statuses);

    // ===== STATS DE PARTICIPATION PAR REUNION (une seule requête pour toute la page) =====
    @Query("SELECT new com.example.solimus.repositories.meeting.MeetingParticipationStats(" +
            "mp.meetingParticipant.meeting.id, " +
            "COUNT(mp), " +
            "SUM(CASE WHEN mp.hasSigned = true THEN 1 ELSE 0 END), " +
            "SUM(mp.tantiemeSnapshot), " +
            "SUM(CASE WHEN mp.hasSigned = true THEN mp.tantiemeSnapshot END)) " +
            "FROM MeetingPresence mp " +
            "WHERE mp.meetingParticipant.meeting.id IN :meetingIds " +
            "GROUP BY mp.meetingParticipant.meeting.id")
    List<MeetingParticipationStats> findParticipationStats(@Param("meetingIds") List<Long> meetingIds);


    // ===== NOMBRE DE DOCUMENTS PAR REUNION (une seule requête pour toute la page) =====
    @Query("SELECT new com.example.solimus.repositories.meeting.MeetingDocumentCount(" +
           "d.meeting.id, COUNT(d)) " +
           "FROM MeetingDocument d " +
           "WHERE d.meeting.id IN :meetingIds " +
           "GROUP BY d.meeting.id")
    List<MeetingDocumentCount> countDocumentsByMeetingIds(@Param("meetingIds") List<Long> meetingIds);

    // Réunions dont la date de convocation est atteinte et pas encore envoyée
    @Query("SELECT m FROM Meeting m " +
           "WHERE m.status = 'UPCOMING' " +
           "AND m.convocationSent = false " +
           "AND m.convocationSentDate <= :today")
    List<Meeting> findMeetingsWithPendingConvocation(@Param("today") LocalDate today);

    // Réunions ou ce copropriétaire est convoqué, filtrées sur UPCOMING uniquement
    @Query("SELECT DISTINCT mp.meeting FROM MeetingParticipant mp " +
            "WHERE mp.user.id = :userId " +
            "AND mp.meeting.status = 'UPCOMING' " +
            "ORDER BY mp.meeting.meetingDate ASC")
    Page<Meeting> findUpcomingMeetingsByParticipantUserId(@Param("userId") Long userId, Pageable pageable);


    // Réunions à venir d'un copropriétaire sur un mois précis (UPCOMING uniquement)
    @Query("SELECT DISTINCT mp.meeting FROM MeetingParticipant mp " +
            "WHERE mp.user.id = :userId " +
            "AND mp.meeting.status = 'UPCOMING' " +
            "AND mp.meeting.meetingDate BETWEEN :startOfMonth AND :endOfMonth " +
            "ORDER BY mp.meeting.meetingDate ASC")
    List<Meeting> findUpcomingMeetingsByParticipantUserIdAndMonth(@Param("userId") Long userId,
                                                                    @Param("startOfMonth") LocalDate startOfMonth,
                                                                    @Param("endOfMonth") LocalDate endOfMonth);

    // Liste légère des réunions d'une résidence, pour un sélecteur
    @Query("SELECT new com.example.solimus.dtos.syndic.meeting.MeetingSummaryDTO(m.id, m.title, m.meetingDate) " +
            "FROM Meeting m WHERE m.residence.id = :residenceId ORDER BY m.meetingDate DESC")
    List<MeetingSummaryDTO> findMeetingSummariesByResidenceId(@Param("residenceId") Long residenceId);
}
