package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingMode;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO complet pour l'écran détail d'une réunion.
 * Contient : infos générales, ordre du jour, documents, participants.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingDetailDTO {
    private Long id;
    private String title;
    private String location;        // sous-titre du header
    private MeetingType type;       // badge
    private MeetingStatus status;
    private MeetingMode mode;
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate meetingDate;
    private String meetingStartTime;   // "14:00"
    private String meetingEndTime;     // "16:00"
    private String organizerName;   // "M. Diop - Syndic SOLIMUS"
    private String description;     // section "À propos"
    private int participantCount;   // "45 participants attendus"
    private List<MeetingAgendaItemDTO> agendaItems;   // ordre du jour
    private List<MeetingDocumentDTO> documents;        // documents joints
    private List<MeetingParticipantDTO> participants;  // liste participants
}
