package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.dtos.syndic.meeting.*;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.enums.MeetingStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicMeetingService {

    void createMeeting(CreateMeetingDTO dto, List<MultipartFile> documents);

    AGListResponseDTO getMeetingsList(MeetingStatus status, String search, Integer page, Integer size);

    MeetingDetailAGDTO getMeetingDetail(Long meetingId);

    // Publie une réunion en brouillon (DRAFT -> UPCOMING) et génère les participants
    void publishMeeting(Long meetingId);

    MeetingParticipantsTabResponseDTO getMeetingParticipants(Long meetingId, int page, int size);

    void signPresence(Long meetingId, Long participantId, SignPresenceDTO dto);

    AgendaItemsTabResponseDTO getAgendaItems(Long meetingId);

    ResolutionsTabResponseDTO getResolutions(Long meetingId);

    void updateResolution(Long meetingId, Long agendaItemId, UpdateResolutionDTO dto);

    MeetingDocumentsTabResponseDTO getMeetingDocuments(Long meetingId, int page, int size);

    List<MeetingDocumentRowDTO> addMeetingDocuments(Long meetingId, List<MultipartFile> files);

    MeetingHistoryTabResponseDTO getMeetingHistory(Long meetingId, int page, int size);

    void deleteMeeting(Long meetingId);

    List<MeetingSummaryDTO> getMeetingSummariesByResidence(Long residenceId);

    MeetingDocumentRowDTO createMeetingDocument(CreateMeetingDocumentDTO dto, MultipartFile file);

    MeetingDocumentRowDTO updateMeetingDocument(Long documentId, UpdateMeetingDocumentDTO dto);

    MeetingDocumentListResponseDTO getMeetingDocumentsList(String search, MeetingDocumentType documentType,
                                                           Long residenceId, int page, int size);


    MeetingDocumentDetailDTO getMeetingDocumentDetail(Long documentId);
}