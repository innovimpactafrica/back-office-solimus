package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.dtos.meeting.AddAgendaItemDTO;
import com.example.solimus.dtos.meeting.AddExternalParticipantDTO;
import com.example.solimus.dtos.meeting.CreateMeetingDTO;
import com.example.solimus.dtos.meeting.InviteParticipantsDTO;
import com.example.solimus.dtos.meeting.MeetingAgendaItemDTO;
import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingCardDTO;
import com.example.solimus.dtos.meeting.MeetingDashboardResponseDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingDetailSyndicDTO;
import com.example.solimus.dtos.meeting.MeetingDocumentDTO;
import com.example.solimus.dtos.meeting.MeetingParticipantsResponseDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import com.example.solimus.dtos.meeting.UpdateMeetingConvocationDTO;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.enums.MeetingDocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicMeetingService {

    /** Créer une nouvelle réunion (Étape 1 — informations générales) */
    MeetingSummaryDTO createMeeting(CreateMeetingDTO dto);

    /** Dashboard des AG — KPIs + liste filtrable */
    MeetingDashboardResponseDTO getMeetingsDashboard(String search, String status, Integer page, Integer size);

    /** Mettre à jour les paramètres de convocation (Étape 2) */
    MeetingSummaryDTO updateConvocation(Long meetingId, UpdateMeetingConvocationDTO dto);

    /** Publier une AG — envoie les convocations et fige les participants */
    MeetingSummaryDTO publishMeeting(Long meetingId);

    /** Ajouter un point à l'ordre du jour */
    MeetingAgendaItemDTO addAgendaItem(Long meetingId, AddAgendaItemDTO dto);

    /** Uploader un document joint (PDF, etc.) via Minio */
    MeetingDocumentDTO uploadDocument(Long meetingId, MultipartFile file,
                                      String fileName);

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

    /** Détail d'une AG pour le syndic — KPIs + Vue Générale */
    MeetingDetailSyndicDTO getMeetingDetailSyndic(Long meetingId);

    /** Liste des participants d'une AG avec filtre optionnel sur le statut de présence */
    MeetingParticipantsResponseDTO getMeetingParticipants(Long meetingId, String status);

    /** Liste des points de l'ordre du jour d'une AG */
    List<MeetingAgendaItemDTO> getAgendaItems(Long meetingId);

    /** Liste des documents d'une AG */
    List<MeetingDocumentDTO> getDocuments(Long meetingId);

    /** Ajouter un document à une AG */
    MeetingDocumentDTO addDocument(Long meetingId, MultipartFile file, MeetingDocumentType documentType);

    /** Historique d'une AG (via ActivityLog) */
    List<ActivityLogItemDTO> getMeetingHistory(Long meetingId);

    /** Vue calendrier — réunions groupées par jour pour un mois donné */
    List<MeetingCalendarDayDTO> getMeetingsCalendar(Long residenceId,
                                                     int year, int month);
}
