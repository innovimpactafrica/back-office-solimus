package com.example.solimus.dtos.owner.meeting;

import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// ===== DTO DETAIL REUNION (APP MOBILE COPROPRIETAIRE) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingDetailDTO {

    private Long id;
    private String title;

    private MeetingType type;
    private String typeLabel;
    private MeetingStatus status;
    private String statusLabel;

    private LocalDate meetingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;

    private String organizerName; // ex: "Syndic - Abdou Diop"

    private String description;          // = convocationMessage
    private long totalParticipants;        // nb réel de convoqués
    private long documentsTotalCount;      // nb de documents total
    private long documentsCurrentPage;
    private long documentsTotalPages;

    private List<OwnerAgendaItemDTO> agendaItems;
    private List<OwnerMeetingDocumentDTO> documents;

}