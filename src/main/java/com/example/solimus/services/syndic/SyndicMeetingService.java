package com.example.solimus.services.syndic;

import com.example.solimus.dtos.meeting.AddAgendaItemDTO;
import com.example.solimus.dtos.meeting.AddExternalParticipantDTO;
import com.example.solimus.dtos.meeting.CreateMeetingDTO;
import com.example.solimus.dtos.meeting.InviteParticipantsDTO;
import com.example.solimus.dtos.meeting.MeetingAgendaItemDTO;
import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingDocumentDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicMeetingService {

    /** Créer une nouvelle réunion */
    void createMeeting(CreateMeetingDTO dto);

    /** Ajouter un point à l'ordre du jour */
    MeetingAgendaItemDTO addAgendaItem(Long meetingId, AddAgendaItemDTO dto);

    /** Uploader un document joint (PDF, etc.) via Minio */
    MeetingDocumentDTO uploadDocument(Long meetingId, MultipartFile file,
                                      String fileName, String documentType);

    /** Inviter des copropriétaires à une réunion */
    void inviteParticipants(Long meetingId, InviteParticipantsDTO dto);

    /** Ajouter un participant externe (nom + rôle libre) */
    void addExternalParticipant(Long meetingId, AddExternalParticipantDTO dto);

    // =========================================================================
    // COPROPRIÉTAIRE + SYNDIC
    // =========================================================================

    /** Liste des réunions d'une résidence (vue liste) */
    List<MeetingSummaryDTO> getMeetingsByResidence(Long residenceId);

    /** Détail complet d'une réunion */
    MeetingDetailDTO getMeetingDetail(Long meetingId);

    /** Vue calendrier — réunions groupées par jour pour un mois donné */
    List<MeetingCalendarDayDTO> getMeetingsCalendar(Long residenceId,
                                                     int year, int month);
}
