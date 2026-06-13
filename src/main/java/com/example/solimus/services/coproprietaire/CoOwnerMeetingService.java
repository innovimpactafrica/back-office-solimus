package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;

import java.util.List;

public interface CoOwnerMeetingService {

    List<MeetingSummaryDTO> getMyMeetings();

    MeetingDetailDTO getMeetingDetail(Long meetingId);

    /** Liste des réunions d'une résidence triées par date (plus proche en premier) */
    org.springframework.data.domain.Page<MeetingSummaryDTO> getMeetingsByResidence(
            Long residenceId,
            int page,
            int size);

    /** Vue calendrier — réunions groupées par jour pour un mois donné */
    org.springframework.data.domain.Page<MeetingCalendarDayDTO> getMeetingsCalendar(
            int year,
            int month,
            int page,
            int size);

    /** Nombre de réunions à venir pour le copropriétaire connecté */
    long getUpcomingMeetingsCount();
}
