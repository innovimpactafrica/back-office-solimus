package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;

import java.util.List;

public interface CoOwnerMeetingService {

    List<MeetingSummaryDTO> getMyMeetings();

    MeetingDetailDTO getMeetingDetail(Long meetingId);

    /** Liste des réunions d'une résidence triées par date (plus proche en premier) */
    List<MeetingSummaryDTO> getMeetingsByResidence(Long residenceId);

    /** Vue calendrier — réunions groupées par jour pour un mois donné */
    List<MeetingCalendarDayDTO> getMeetingsCalendar(Long residenceId,
                                                     int year, int month);

    /** Nombre de réunions à venir pour le copropriétaire connecté */
    long getUpcomingMeetingsCount();
}
