package com.example.solimus.repositories;

import com.example.solimus.entities.MeetingParticipant;
import com.example.solimus.entities.User;
import com.example.solimus.enums.AttendanceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    // Liste paginée des participants d'une réunion (pour l'affichage du tableau, page par page)
    @Query("SELECT mp FROM MeetingParticipant mp WHERE mp.meeting.id = :meetingId")
    Page<MeetingParticipant> findByMeetingId(@Param("meetingId") Long meetingId, Pageable pageable);

    // Nombre total de participants convoqués à une réunion précise (badge "Tous X")
    long countByMeetingId(Long meetingId);

    // Nombre de participants d'une réunion ayant un type de présence précis (Présent/Procuration/Absent)
    // — utilisé pour les 3 pills de filtre de l'onglet Participants
    @Query("SELECT COUNT(mpr) FROM MeetingPresence mpr " +
            "WHERE mpr.meetingParticipant.meeting.id = :meetingId AND mpr.attendanceType = :attendanceType")
    long countByMeetingIdAndAttendanceType(@Param("meetingId") Long meetingId,
                                            @Param("attendanceType") AttendanceType attendanceType);

    // Toutes les réunions auxquelles un utilisateur donné a déjà participé
    List<MeetingParticipant> findByUser(User user);

    // Vérifie si un utilisateur précis est bien convoqué à une réunion précise
    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    // Récupère la convocation d'un copropriétaire précis pour une réunion précise — utilisé pour
    // vérifier son appartenance avant markPresent/giveProcuration (espace copropriétaire)
    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);
}