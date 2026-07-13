package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingPresence;
import com.example.solimus.enums.MeetingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeetingPresenceRepository extends JpaRepository<MeetingPresence, Long> {

    List<MeetingPresence> findByMeetingParticipantMeetingId(Long meetingId);

    // ===== TOUTES LES PRESENCES D'UN COPROPRIETAIRE POUR LES AG TERMINEES (pour le taux de participation) =====
    @Query("SELECT mpr FROM MeetingPresence mpr " +
           "WHERE mpr.meetingParticipant.user.id = :userId " +
           "AND mpr.meetingParticipant.meeting.residence.id = :residenceId " +
           "AND mpr.meetingParticipant.meeting.status = 'COMPLETED'")
    List<MeetingPresence> findCompletedPresencesForOwner(@Param("userId") Long userId,
                                                           @Param("residenceId") Long residenceId);

    // ===== HISTORIQUE PAGINE, FILTRE PAR TYPE ET ANNEE =====
    @Query("SELECT mpr FROM MeetingPresence mpr " +
           "WHERE mpr.meetingParticipant.user.id = :userId " +
           "AND mpr.meetingParticipant.meeting.residence.id = :residenceId " +
           "AND (:type IS NULL OR mpr.meetingParticipant.meeting.type = :type) " +
           "AND (:year IS NULL OR FUNCTION('YEAR', mpr.meetingParticipant.meeting.meetingDate) = :year) " +
           "ORDER BY mpr.meetingParticipant.meeting.meetingDate DESC")
    Page<MeetingPresence> searchOwnerMeetingHistory(@Param("userId") Long userId,
                                                      @Param("residenceId") Long residenceId,
                                                      @Param("type") MeetingType type,
                                                      @Param("year") Integer year,
                                                      Pageable pageable);

    // ===== DERNIERE AG (peu importe le statut), triee par date desc =====
    @Query("SELECT mpr FROM MeetingPresence mpr " +
           "WHERE mpr.meetingParticipant.user.id = :userId " +
           "AND mpr.meetingParticipant.meeting.residence.id = :residenceId " +
           "ORDER BY mpr.meetingParticipant.meeting.meetingDate DESC")
    Page<MeetingPresence> findLastMeetingForOwner(@Param("userId") Long userId,
                                                    @Param("residenceId") Long residenceId,
                                                    Pageable pageable); // pageable avec size=1 pour ne prendre que la 1ere ligne
}
